package com.digitalsolverland.opencode.runtime

import android.content.Context
import java.io.File

/** Emplacements du runtime Linux embarqué (proot + Debian + opencode). */
class RuntimePaths(context: Context) {

    private val app = context.applicationContext

    val base: File = File(app.filesDir, "runtime")

    /** Racine Debian (contient bin/, usr/, etc.). */
    val rootfs: File = File(base, "rootfs")

    /** Binaires proot + bibliothèques bionic. */
    val lib: File = File(base, "lib")

    val tmp: File = File(base, "tmp")
    val shm: File = File(base, "shm")

    /** Dossier de travail partagé, monté sur /root/workspace dans la Debian. */
    val workspace: File = File(app.filesDir, "workspace")

    val marker: File = File(base, "installed.version")
    val logFile: File = File(base, "server.log")
    val execLogFile: File = File(base, "shell.log")

    fun ensure() {
        base.mkdirs()
        lib.mkdirs()
        tmp.mkdirs()
        shm.mkdirs()
        workspace.mkdirs()
    }

    fun proot(): File = File(lib, "proot")
    fun loader(): File = File(lib, "loader")

    fun installedVersion(): Int =
        try {
            if (marker.exists()) marker.readText().trim().toIntOrNull() ?: 0 else 0
        } catch (_: Exception) {
            0
        }

    fun isInstalled(): Boolean = installedVersion() > 0 && File(rootfs, "usr/local/bin/opencode").exists()
}
