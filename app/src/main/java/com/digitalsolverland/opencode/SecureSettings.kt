package com.digitalsolverland.opencode

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSettings(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "opencode_secure_prefs_v2",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /* ------------------------- Server access ------------------------- */

    /** Base URL of `opencode serve`, e.g. http://192.168.1.20:4096 */
    var serverUrl: String
        get() = prefs.getString("server_url", "http://127.0.0.1:4096") ?: "http://127.0.0.1:4096"
        set(v) = prefs.edit().putString("server_url", v).apply()

    /** The token printed by `opencode serve` ("server password ..."). */
    var serverPassword: String
        get() = prefs.getString("server_password", "") ?: ""
        set(v) = prefs.edit().putString("server_password", v).apply()

    /* --------------------------- Selection -------------------------- */

    var defaultProvider: String
        get() = prefs.getString("default_provider", "") ?: ""
        set(v) = prefs.edit().putString("default_provider", v).apply()

    var defaultModel: String
        get() = prefs.getString("default_model", "") ?: ""
        set(v) = prefs.edit().putString("default_model", v).apply()

    var defaultAgent: String
        get() = prefs.getString("default_agent", "") ?: ""
        set(v) = prefs.edit().putString("default_agent", v).apply()

    /* ---------------------- Embedded runtime ------------------------ */

    var openAiKey: String
        get() = prefs.getString("openai_key", "") ?: ""
        set(v) = prefs.edit().putString("openai_key", v).apply()

    var openRouterKey: String
        get() = prefs.getString("openrouter_key", "") ?: ""
        set(v) = prefs.edit().putString("openrouter_key", v).apply()

    var anthropicKey: String
        get() = prefs.getString("anthropic_key", "") ?: ""
        set(v) = prefs.edit().putString("anthropic_key", v).apply()

    var port: Int
        get() = prefs.getInt("port", 4096)
        set(v) = prefs.edit().putInt("port", v).apply()

    var entryScript: String
        get() = prefs.getString("entry", "opencode/cli.js") ?: "opencode/cli.js"
        set(v) = prefs.edit().putString("entry", v).apply()

    var extraArgs: String
        get() = prefs.getString("args", "serve --hostname 127.0.0.1 --port {PORT}")
            ?: "serve --hostname 127.0.0.1 --port {PORT}"
        set(v) = prefs.edit().putString("args", v).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean("autostart", false)
        set(v) = prefs.edit().putBoolean("autostart", v).apply()

    var quickActionsRaw: String
        get() = prefs.getString("quick", DEFAULT_ACTIONS) ?: DEFAULT_ACTIONS
        set(v) = prefs.edit().putString("quick", v).apply()

    fun quickActions(): List<Pair<String, String>> =
        quickActionsRaw.lines()
            .mapNotNull { line ->
                val parts = line.split("::", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim() else null
            }

    fun rememberModel(providerID: String, modelID: String) {
        defaultProvider = providerID
        defaultModel = modelID
    }

    companion object {
        const val DEFAULT_ACTIONS =
            "Audit complet::Fais un audit complet du projet courant (sécurité, qualité, perf) et résume les points critiques.\n" +
            "Générer du code::Génère le code demandé, complet et prêt à l'emploi, sans omettre les imports.\n" +
            "Statut du projet::Donne-moi le statut actuel : tâches en cours, fichiers modifiés, erreurs."
    }
}
