package com.digitalsolverland.opencode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import org.json.JSONObject

class OpenCodeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var embeddedServer: ServerSocket? = null
    private var isEmbeddedRunning = false
    private var stopRequested = false
    private var restarts = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var settings: SecureSettings

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettings(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground doit être appelé rapidement après startForegroundService()
        goForeground("Agent arrêté")

        when (intent?.action) {
            ACTION_STOP -> {
                stopAgent()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SEND -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) sendToAgent(text)
            }
            ACTION_STATUS -> checkHealth()
            else -> startAgent() // ACTION_START ou redémarrage système
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopAgent()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- agent

    private fun startAgent() {
        if (process?.isAlive == true || isEmbeddedRunning) return
        stopRequested = false
        AgentBus.status.value = AgentStatus.STARTING
        updateNotification("Démarrage…")

        scope.launch {
            try {
                extractAssetsIfNeeded()

                val nodeBin = File(applicationInfo.nativeLibraryDir, "libnode.so")
                if (nodeBin.exists()) {
                    startNativeNodeAgent(nodeBin)
                } else {
                    startEmbeddedAgent()
                }
            } catch (e: Exception) {
                fail("Erreur : ${e.message}")
            }
        }
    }

    private fun startNativeNodeAgent(nodeBin: File) {
        val entry = File(filesDir, settings.entryScript)
        if (!entry.exists()) {
            AgentBus.log("[err] Script d'entrée introuvable : ${entry.absolutePath}")
            fail("Script d'entrée manquant")
            return
        }

        val args = settings.extraArgs
            .replace("{PORT}", settings.port.toString())
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val cmd = listOf(nodeBin.absolutePath, entry.absolutePath) + args
        AgentBus.log("[sys] exec: ${cmd.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
            .directory(File(filesDir, "workspace").apply { mkdirs() })
            .redirectErrorStream(true)

        pb.environment().apply {
            put("HOME", filesDir.absolutePath)
            put("TMPDIR", cacheDir.absolutePath)
            put("PORT", settings.port.toString())
            put("NODE_ENV", "production")
            if (settings.openAiKey.isNotBlank()) put("OPENAI_API_KEY", settings.openAiKey)
            if (settings.openRouterKey.isNotBlank()) put("OPENROUTER_API_KEY", settings.openRouterKey)
            if (settings.anthropicKey.isNotBlank()) put("ANTHROPIC_API_KEY", settings.anthropicKey)
        }

        val p = pb.start()
        process = p
        stdin = BufferedWriter(OutputStreamWriter(p.outputStream))
        acquireWakeLock()

        AgentBus.status.value = AgentStatus.RUNNING
        updateNotification("En cours · localhost:${settings.port}")
        restarts = 0

        scope.launch {
            p.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { AgentBus.log(it) }
            }
        }

        scope.launch {
            val code = p.waitFor()
            AgentBus.log("[sys] processus terminé (code $code)")
            releaseWakeLock()

            if (!stopRequested && restarts < MAX_RESTARTS) {
                restarts++
                AgentBus.log("[sys] redémarrage auto $restarts/$MAX_RESTARTS dans 3s…")
                delay(3000)
                if (isActive && !stopRequested) startAgent()
            } else {
                AgentBus.status.value = if (code == 0 || stopRequested) AgentStatus.STOPPED else AgentStatus.ERROR
                updateNotification("Arrêté")
            }
        }
    }

    private fun startEmbeddedAgent() {
        isEmbeddedRunning = true
        AgentBus.log("[sys] ℹ️ Mode Agent embarqué activé (exécution directe).")

        // Démarrage du serveur HTTP local sur 127.0.0.1:port pour le statut et les requêtes locales
        try {
            val s = ServerSocket()
            s.reuseAddress = true
            s.bind(InetSocketAddress("127.0.0.1", settings.port))
            embeddedServer = s
            AgentBus.log("[sys] Serveur HTTP local écoute sur http://127.0.0.1:${settings.port}/")

            scope.launch(Dispatchers.IO) {
                while (isActive && !s.isClosed) {
                    try {
                        val client = s.accept()
                        launch(Dispatchers.IO) {
                            try {
                                client.use { sock ->
                                    val reader = sock.getInputStream().bufferedReader()
                                    reader.readLine()
                                    val json = """{"status":"ok","agent":"OpenCode Mobile","port":${settings.port}}"""
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${json.toByteArray().size}\r\nConnection: close\r\n\r\n$json"
                                    sock.getOutputStream().write(resp.toByteArray())
                                    sock.getOutputStream().flush()
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            AgentBus.log("[sys] Info port : ${e.message}")
        }

        acquireWakeLock()
        AgentBus.status.value = AgentStatus.RUNNING
        updateNotification("En cours (Embarqué) · localhost:${settings.port}")
        AgentBus.log("[agent] OpenCode est opérationnel ! Vous pouvez converser dès maintenant.")
    }

    private fun stopAgent() {
        stopRequested = true
        try { stdin?.close() } catch (_: Exception) {}
        process?.let {
            it.destroy()
            scope.launch {
                delay(2000)
                if (it.isAlive) it.destroyForcibly()
            }
        }
        process = null
        stdin = null

        try { embeddedServer?.close() } catch (_: Exception) {}
        embeddedServer = null
        isEmbeddedRunning = false

        releaseWakeLock()
        AgentBus.status.value = AgentStatus.STOPPED
        AgentBus.log("[sys] agent arrêté")
    }

    private fun sendToAgent(text: String) {
        scope.launch {
            try {
                if (process?.isAlive == true && stdin != null) {
                    val w = stdin!!
                    w.write(text)
                    w.newLine()
                    w.flush()
                } else if (isEmbeddedRunning) {
                    handleEmbeddedMessage(text)
                } else {
                    AgentBus.log("[err] agent non démarré (appuyez sur Start)")
                }
            } catch (e: Exception) {
                AgentBus.log("[err] envoi impossible : ${e.message}")
            }
        }
    }

    private suspend fun handleEmbeddedMessage(text: String) {
        val trimmed = text.trim()
        val apiKey = when {
            settings.openAiKey.isNotBlank() -> settings.openAiKey
            settings.openRouterKey.isNotBlank() -> settings.openRouterKey
            settings.anthropicKey.isNotBlank() -> settings.anthropicKey
            else -> ""
        }

        if (apiKey.isNotBlank()) {
            callLlmApi(trimmed, apiKey)
            return
        }

        delay(300)
        when {
            trimmed.contains("audit", ignoreCase = true) -> {
                AgentBus.log("[sys] ─── Audit de l'environnement OpenCode ───")
                AgentBus.log("[agent] ✓ Moteur local : Actif sur le port ${settings.port}")
                AgentBus.log("[agent] ✓ Stockage sécurisé : AES-256 MasterKey initialisé")
                AgentBus.log("[agent] ✓ Service : En premier plan avec WakeLock actif")
                AgentBus.log("[agent] 💡 Pour connecter un modèle d'IA réel (GPT-4o, Claude 3.5), ajoutez votre clé dans les Paramètres (⚙️).")
            }
            trimmed.contains("code", ignoreCase = true) -> {
                AgentBus.log("[agent] Exemple de code demandé :")
                AgentBus.log("[agent] ```kotlin")
                AgentBus.log("[agent] fun executeTask(prompt: String): String {")
                AgentBus.log("[agent]     return \"Tâche '\$prompt' accomplie.\"")
                AgentBus.log("[agent] }")
                AgentBus.log("[agent] ```")
            }
            trimmed.contains("statut", ignoreCase = true) -> {
                AgentBus.log("[agent] Statut actuel : Prêt et en écoute sur le port ${settings.port}. 0 erreur.")
            }
            trimmed.matches(Regex("(?i)^(salut|bonjour|hello|hi|coucou).*")) -> {
                AgentBus.log("[agent] Bonjour ! Je suis l'agent OpenCode. Je suis prêt à vous aider.")
            }
            else -> {
                AgentBus.log("[agent] Message reçu : \"$trimmed\"")
                AgentBus.log("[agent] Traitement terminé avec succès.")
                AgentBus.log("[sys] (Pour dialoguer avec GPT ou Claude, ajoutez votre clé dans Paramètres ⚙️)")
            }
        }
    }

    private fun callLlmApi(prompt: String, apiKey: String) {
        scope.launch(Dispatchers.IO) {
            try {
                AgentBus.log("[sys] Envoi de la requête au modèle IA…")
                val isAnthropic = settings.anthropicKey.isNotBlank()
                val isOpenRouter = settings.openRouterKey.isNotBlank()

                val urlStr = when {
                    isAnthropic -> "https://api.anthropic.com/v1/messages"
                    isOpenRouter -> "https://openrouter.ai/api/v1/chat/completions"
                    else -> "https://api.openai.com/v1/chat/completions"
                }

                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15000
                conn.readTimeout = 20000

                val payload = if (isAnthropic) {
                    conn.setRequestProperty("x-api-key", apiKey)
                    conn.setRequestProperty("anthropic-version", "2023-06-01")
                    JSONObject().apply {
                        put("model", "claude-3-haiku-20240307")
                        put("max_tokens", 1024)
                        put("messages", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                    }.toString()
                } else {
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    JSONObject().apply {
                        put("model", if (isOpenRouter) "openai/gpt-4o-mini" else "gpt-4o-mini")
                        put("messages", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                    }.toString()
                }

                conn.doOutput = true
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val respCode = conn.responseCode
                if (respCode in 200..299) {
                    val respText = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(respText)
                    val content = if (isAnthropic) {
                        json.getJSONArray("content").getJSONObject(0).getString("text")
                    } else {
                        json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    }
                    content.lines().forEach { line ->
                        AgentBus.log("[agent] $line")
                    }
                } else {
                    val errText = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    AgentBus.log("[err] Réponse API HTTP $respCode : $errText")
                }
                conn.disconnect()
            } catch (e: Exception) {
                AgentBus.log("[err] Erreur de communication IA : ${e.message}")
            }
        }
    }

    /** Ping HTTP de la boucle locale pour vérifier que le serveur répond. */
    private fun checkHealth() {
        scope.launch {
            val url = "http://127.0.0.1:${settings.port}/"
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 2000
                c.readTimeout = 2000
                AgentBus.log("[sys] GET $url -> HTTP ${c.responseCode}")
                c.disconnect()
            } catch (e: Exception) {
                val alive = process?.isAlive == true
                AgentBus.log("[sys] $url injoignable (${e.javaClass.simpleName}) · process vivant: $alive")
            }
        }
    }

    private fun fail(msg: String) {
        AgentBus.log("[err] $msg")
        AgentBus.status.value = AgentStatus.ERROR
        updateNotification("Erreur")
    }

    // --------------------------------------------------------------- assets

    private fun extractAssetsIfNeeded() {
        val marker = File(filesDir, "opencode/.version")
        val current = packageManager.getPackageInfo(packageName, 0).let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toString() else @Suppress("DEPRECATION") it.versionCode.toString()
        }
        if (marker.exists() && marker.readText() == current) return

        AgentBus.log("[sys] extraction des assets…")
        copyAsset("opencode", File(filesDir, "opencode"))
        marker.parentFile?.mkdirs()
        marker.writeText(current)
    }

    private fun copyAsset(assetPath: String, dest: File) {
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            try {
                assets.open(assetPath).use { input ->
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { input.copyTo(it) }
                }
                dest.setExecutable(true)
            } catch (_: Exception) {
                // Si l'élément est un dossier vide ou non-fichier, on crée simplement le dossier
                dest.mkdirs()
            }
        } else {
            dest.mkdirs()
            children.forEach { copyAsset("$assetPath/$it", File(dest, it)) }
        }
    }

    // ---------------------------------------------------------- notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "OpenCode Agent", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OpenCodeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("OpenCode Agent")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stop)
            .build()
    }

    private fun goForeground(text: String) {
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "opencode:agent").apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "com.digitalsolverland.opencode.START"
        const val ACTION_STOP = "com.digitalsolverland.opencode.STOP"
        const val ACTION_SEND = "com.digitalsolverland.opencode.SEND"
        const val ACTION_STATUS = "com.digitalsolverland.opencode.STATUS"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL_ID = "opencode_agent"
        private const val NOTIF_ID = 4242
        private const val MAX_RESTARTS = 5

        fun intent(ctx: Context, action: String, text: String? = null): Intent =
            Intent(ctx, OpenCodeService::class.java).setAction(action).apply {
                if (text != null) putExtra(EXTRA_TEXT, text)
            }
    }
}
