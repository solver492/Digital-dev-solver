package com.digitalsolverland.opencode.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Télécharge et installe le bundle runtime (Debian 12 arm64 + binaire opencode 2.1.4).
 * Le bundle est publié comme asset de release GitHub (~180 Mo).
 */
object BundleInstaller {

    const val VERSION = 1
    const val SIZE_BYTES = 188_945_504L
    const val SHA256 = "d5b0595192976382fdafb1aee70a2b789dafac592c4704ad2b6866aed8c6a3be"
    const val URL =
        "https://github.com/solver492/Digital-dev-solver/releases/download/runtime-v1/opencode-runtime-arm64-v1.tar.gz"

    /** Télécharge, vérifie et extrait. Retourne true si le runtime est prêt. */
    suspend fun install(
        context: Context,
        onProgress: (Float, String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val paths = RuntimePaths(context)
        paths.ensure()

        val archive = File(context.cacheDir, "opencode-runtime.tar.gz")
        val staging = File(paths.base, "rootfs.new")
        try {
            if (!download(archive, onProgress)) return@withContext false

            onProgress(0f, "Vérification de l'archive…")
            val digest = sha256(archive)
            if (!digest.equals(SHA256, ignoreCase = true)) {
                archive.delete()
                onProgress(0f, "Archive corrompue (SHA-256 invalide). Réessayez.")
                return@withContext false
            }

            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()

            onProgress(0f, "Extraction de la distribution Linux (plusieurs minutes)…")
            var lastPercent = -1
            GZIPInputStream(archive.inputStream().buffered(256 * 1024), 256 * 1024).use { gz ->
                TarGzExtractor(staging) { bytes ->
                    // L'extraction produit ~460 Mo : on rapporte la progression sur cette base.
                    val percent = ((bytes * 100) / 500_000_000L).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent / 100f, "Extraction… $percent %")
                    }
                }.extract(gz)
            }

            if (!File(staging, "usr/local/bin/opencode").exists()) {
                staging.deleteRecursively()
                onProgress(0f, "Extraction incomplète : binaire opencode absent.")
                return@withContext false
            }

            onProgress(1f, "Installation…")
            paths.marker.delete()
            if (paths.rootfs.exists()) paths.rootfs.deleteRecursively()
            if (!staging.renameTo(paths.rootfs)) {
                staging.copyRecursively(paths.rootfs, overwrite = true)
                staging.deleteRecursively()
            }
            paths.marker.writeText(VERSION.toString())
            archive.delete()
            onProgress(1f, "Runtime installé.")
            true
        } catch (e: Exception) {
            onProgress(0f, "Erreur d'installation : ${e.message}")
            false
        } finally {
            try {
                if (archive.exists()) archive.delete()
            } catch (_: Exception) {
            }
            try {
                if (staging.exists()) staging.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }

    private fun download(target: File, onProgress: (Float, String) -> Unit): Boolean {
        onProgress(0f, "Téléchargement du runtime (~180 Mo)…")
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(URL).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "OpenCode-Mobile")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                onProgress(0f, "Téléchargement refusé (HTTP $code).")
                return false
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: SIZE_BYTES
            FileOutputStream(target).use { out ->
                connection.inputStream.use { input ->
                    val buf = ByteArray(256 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            val mb = copied / (1024 * 1024)
                            onProgress(percent / 100f, "Téléchargement… $percent % ($mb Mo)")
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            onProgress(0f, "Téléchargement interrompu : ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(256 * 1024).use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
