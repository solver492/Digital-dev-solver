package com.digitalsolverland.opencode.runtime

import android.content.Context
import android.util.Base64
import com.digitalsolverland.opencode.AgentBus
import com.digitalsolverland.opencode.SecureSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.SecureRandom

enum class LocalPhase { NOT_INSTALLED, INSTALLING, STOPPED, STARTING, RUNNING, ERROR }

/**
 * Gestion du serveur OpenCode embarqué : un proot + Debian arm64 + binaire opencode.
 * Le service de premier plan maintient le processus en vie ; l'UI observe les StateFlow.
 */
object LocalServer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _phase = MutableStateFlow(LocalPhase.NOT_INSTALLED)
    val phase: StateFlow<LocalPhase> = _phase

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private val _port = MutableStateFlow(4096)
    val port: StateFlow<Int> = _port

    private val _installed = MutableStateFlow(false)
    val installed: StateFlow<Boolean> = _installed

    private var process: Process? = null
    private var startJob: Job? = null
    private var installJob: Job? = null
    private var appContext: Context? = null
    private var lastError: String = ""
    private var settingsCache: SecureSettings? = null
    @Volatile private var cachedPassword: String? = null

    private fun settings(context: Context): SecureSettings =
        settingsCache ?: SecureSettings(context.applicationContext).also { settingsCache = it }

    fun context(): Context? = appContext

    fun refresh(context: Context) {
        appContext = context.applicationContext
        val paths = RuntimePaths(context)
        _installed.value = paths.isInstalled()
        if (!_installed.value) {
            if (_phase.value != LocalPhase.INSTALLING) _phase.value = LocalPhase.NOT_INSTALLED
        } else if (_phase.value == LocalPhase.NOT_INSTALLED) {
            _phase.value = LocalPhase.STOPPED
            _message.value = "Runtime installé, prêt à démarrer."
        }
        _port.value = settings(context).localPort
    }

    fun password(context: Context): String {
        cachedPassword?.let { return it }
        val prefs = settings(context)
        var pw = prefs.localPassword
        if (pw.isBlank()) {
            pw = generatePassword()
            prefs.localPassword = pw
        }
        cachedPassword = pw
        return pw
    }

    fun baseUrl(context: Context): String = "http://127.0.0.1:${_port.value}"

    /* ------------------------------------------------------------- installation */

    fun install(context: Context) {
        appContext = context.applicationContext
        if (installJob?.isActive == true) return
        val app = context.applicationContext
        installJob = scope.launch {
            _phase.value = LocalPhase.INSTALLING
            _progress.value = 0f
            _message.value = "Préparation…"
            log("Installation du runtime Linux (Debian 12 + OpenCode)…")
            val ok = BundleInstaller.install(app) { progress, message ->
                _progress.value = progress
                _message.value = message
            }
            _installed.value = RuntimePaths(app).isInstalled()
            if (ok && _installed.value) {
                _phase.value = LocalPhase.STOPPED
                _message.value = "Runtime installé."
                log("Runtime installé avec succès.")
            } else {
                _phase.value = if (_installed.value) LocalPhase.STOPPED else LocalPhase.NOT_INSTALLED
                log("Échec de l'installation : ${_message.value}")
            }
        }
    }

    /* ------------------------------------------------------------------ process */

    fun start(context: Context) {
        appContext = context.applicationContext
        val app = context.applicationContext
        refresh(app)
        if (!_installed.value) {
            _phase.value = LocalPhase.NOT_INSTALLED
            _message.value = "Installez d'abord le runtime Linux."
            return
        }
        if (process?.isAlive == true || startJob?.isActive == true) return

        startJob = scope.launch {
            try {
                _phase.value = LocalPhase.STARTING
                _message.value = "Extraction des binaires proot…"
                extractProotAssets(app)

                val selectedPort = findFreePort(settings(app).localPort)
                _port.value = selectedPort
                settings(app).localPort = selectedPort

                val pw = password(app)
                val command = buildCommand(app, pw, selectedPort, guestCommand = listOf(
                    "/usr/local/bin/opencode",
                    "serve",
                    "--hostname", "127.0.0.1",
                    "--port", selectedPort.toString(),
                    "--print-logs",
                ))

                log("[sys] démarrage : " + command.joinToString(" "))
                _message.value = "Démarrage du serveur OpenCode…"

                val pb = ProcessBuilder(command)
                    .directory(RuntimePaths(app).base)
                    .redirectErrorStream(true)
                pb.environment().apply {
                    put("PROOT_TMP_DIR", RuntimePaths(app).tmp.absolutePath)
                    put("PROOT_LOADER", RuntimePaths(app).loader().absolutePath)
                    put("HOME", RuntimePaths(app).base.absolutePath)
                    put("TMPDIR", RuntimePaths(app).tmp.absolutePath)
                    put("PATH", "/system/bin:/system/xbin")
                }
                val p = pb.start()
                process = p
                lastError = ""

                scope.launch { pump(p) }

                // Attente de disponibilité réelle de l'API.
                var ready = false
                var waited = 0
                while (waited < 240_000) {
                    delay(1_500)
                    waited += 1_500
                    if (!p.isAlive) break
                    if (health(app, selectedPort)) {
                        ready = true
                        break
                    }
                }

                if (ready) {
                    _phase.value = LocalPhase.RUNNING
                    _message.value = "Serveur local actif sur le port $selectedPort"
                    log("[sys] serveur OpenCode prêt sur http://127.0.0.1:$selectedPort")
                } else if (p.isAlive && _phase.value == LocalPhase.STARTING) {
                    _phase.value = LocalPhase.ERROR
                    _message.value = "Le serveur ne répond pas. Consultez le journal."
                    log("[err] le serveur ne répond pas après 4 min (processus vivant).")
                } else {
                    _phase.value = LocalPhase.ERROR
                    _message.value = "Le serveur s'est arrêté au démarrage. Consultez le journal."
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                _phase.value = LocalPhase.ERROR
                _message.value = "Erreur : $lastError"
                log("[err] démarrage impossible : $lastError")
            }
        }
    }

    fun stop() {
        startJob?.cancel()
        startJob = null
        val p = process
        process = null
        try {
            p?.destroy()
        } catch (_: Exception) {
        }
        scope.launch {
            delay(1_500)
            try {
                if (p?.isAlive == true) p.destroyForcibly()
            } catch (_: Exception) {
            }
        }
        _phase.value = if (_installed.value) LocalPhase.STOPPED else LocalPhase.NOT_INSTALLED
        _message.value = "Serveur arrêté."
        log("[sys] serveur arrêté.")
    }

    /** Exécute une commande shell dans la Debian (console intégrée). */
    suspend fun exec(context: Context, command: String, timeoutMs: Long = 180_000): String =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            appContext = app
            if (!RuntimePaths(app).isInstalled()) return@withContext "Runtime non installé."
            try {
                extractProotAssets(app)
                val pw = password(app)
                val cmd = buildCommand(app, pw, _port.value, listOf(
                    "/bin/bash", "-lc", command,
                ))
                val pb = ProcessBuilder(cmd)
                    .directory(RuntimePaths(app).base)
                    .redirectErrorStream(true)
                pb.environment().apply {
                    put("PROOT_TMP_DIR", RuntimePaths(app).tmp.absolutePath)
                    put("PROOT_LOADER", RuntimePaths(app).loader().absolutePath)
                    put("HOME", RuntimePaths(app).base.absolutePath)
                    put("TMPDIR", RuntimePaths(app).tmp.absolutePath)
                }
                val started = System.currentTimeMillis()
                val p = pb.start()
                val output = StringBuilder()
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                val pumpJob = scope.launch {
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            output.append(line).append('\n')
                            if (output.length > 200_000) output.delete(0, output.length - 150_000)
                        }
                    }
                }
                val finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!finished) {
                    p.destroyForcibly()
                    return@withContext output.toString() + "\n[timeout après ${timeoutMs / 1000} s]"
                }
                pumpJob.join()
                val elapsed = (System.currentTimeMillis() - started) / 1000
                val text = output.toString().ifBlank { "(aucune sortie)" }
                appendFile(RuntimePaths(app).execLogFile, "$ $command\n$text\n")
                "$text\n[code ${p.exitValue()} · ${elapsed}s]"
            } catch (e: Exception) {
                "Erreur d'exécution : ${e.message}"
            }
        }

    /* ------------------------------------------------------------------ interne */

    private fun buildCommand(
        context: Context,
        password: String,
        port: Int,
        guestCommand: List<String>,
    ): List<String> {
        val paths = RuntimePaths(context)
        val cmd = mutableListOf(
            paths.proot().absolutePath,
            "--kill-on-exit",
            "--link2symlink",
            "-0",
            "-r", paths.rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${paths.tmp.absolutePath}:/tmp",
            "-b", "${paths.shm.absolutePath}:/dev/shm",
            "-b", "${paths.workspace.absolutePath}:/root/workspace",
            "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "SHELL=/bin/bash",
            "OPENCODE_SERVER_PASSWORD=$password",
        )
        cmd += guestCommand
        return cmd
    }

    private fun extractProotAssets(context: Context) {
        val paths = RuntimePaths(context)
        paths.ensure()
        val files = listOf("proot", "loader", "libtalloc.so.2", "libandroid-shmem.so")
        for (name in files) {
            val target = File(paths.lib, name)
            if (target.exists() && target.length() > 0) {
                target.setExecutable(true, false)
                continue
            }
            context.assets.open("proot/$name").use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            target.setExecutable(true, false)
            target.setReadable(true, false)
        }
    }

    private fun pump(p: Process) {
        val paths = appContext?.let { RuntimePaths(it) } ?: return
        try {
            BufferedReader(InputStreamReader(p.inputStream)).useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        log("[srv] $line")
                        appendFile(paths.logFile, line + "\n")
                        trimLog(paths.logFile)
                    }
                }
            }
        } catch (_: Exception) {
        }
        val code = try {
            p.waitFor()
        } catch (_: Exception) {
            -1
        }
        log("[sys] processus opencode terminé (code $code)")
        if (process === p) {
            process = null
            if (_phase.value == LocalPhase.RUNNING || _phase.value == LocalPhase.STARTING) {
                _phase.value = LocalPhase.ERROR
                _message.value = "Serveur arrêté de façon inattendue (code $code)."
            }
        }
    }

    private fun health(context: Context, port: Int): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val pw = password(context)
            conn = (URL("http://127.0.0.1:$port/api/model").openConnection() as HttpURLConnection).apply {
                connectTimeout = 3_000
                readTimeout = 8_000
                setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString("opencode:$pw".toByteArray(), Base64.NO_WRAP)
                )
            }
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun findFreePort(preferred: Int): Int {
        for (candidate in preferred until preferred + 20) {
            try {
                ServerSocket(candidate).use { return candidate }
            } catch (_: Exception) {
            }
        }
        return preferred
    }

    private fun generatePassword(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
            .replace("=", "")
            .take(40)
    }

    fun log(line: String) {
        AgentBus.log(line)
    }

    private fun appendFile(file: File, text: String) {
        try {
            file.parentFile?.mkdirs()
            file.appendText(text)
        } catch (_: Exception) {
        }
    }

    private fun trimLog(file: File) {
        try {
            if (file.length() > 2_000_000) {
                val lines = file.readLines()
                file.writeText(lines.takeLast(4_000).joinToString("\n"))
            }
        } catch (_: Exception) {
        }
    }
}
