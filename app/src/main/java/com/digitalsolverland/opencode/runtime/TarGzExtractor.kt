package com.digitalsolverland.opencode.runtime

import android.system.Os
import java.io.File
import java.io.InputStream

/**
 * Extracteur tar+gzip minimaliste, compatible avec les archives GNU tar d'un rootfs Debian
 * (noms longs GNU 'L', en-têtes pax 'x', liens symboliques et liens durs).
 *
 * On n'utilise pas commons-compress pour éviter toute dépendance supplémentaire côté APK.
 */
class TarGzExtractor(
    private val destRoot: File,
    private val onBytes: (Long) -> Unit = {},
) {

    private var total = 0L

    fun extract(input: InputStream) {
        val header = ByteArray(512)
        var longName: String? = null
        var longLink: String? = null
        var paxName: String? = null
        var paxLink: String? = null

        while (true) {
            if (!readFully(input, header, 512)) break
            if (header.all { it == 0.toByte() }) continue

            val type = header[156].toInt().and(0xFF).toChar()
            val size = parseOctal(header, 124, 12)

            if (type == 'x' || type == 'g') {
                val data = readData(input, size)
                val text = String(data, Charsets.UTF_8)
                text.split('\n').forEach { record ->
                    val sep = record.indexOf('=')
                    if (sep > 0) {
                        val key = record.substring(0, sep).trim()
                        val value = record.substring(sep + 1)
                        when (key) {
                            "path" -> paxName = value
                            "linkpath" -> paxLink = value
                        }
                    }
                }
                longName = null
                longLink = null
                continue
            }

            if (type == 'L') {
                longName = String(readData(input, size), Charsets.UTF_8).trimEnd('\u0000', '\n')
                continue
            }
            if (type == 'K') {
                longLink = String(readData(input, size), Charsets.UTF_8).trimEnd('\u0000', '\n')
                continue
            }

            val rawName = (paxName ?: longName ?: nameOf(header)).trim('\u0000')
            val linkName = (paxLink ?: longLink ?: nameField(header, 157, 100)).trim('\u0000')
            paxName = null
            paxLink = null
            longName = null
            longLink = null

            val clean = sanitize(rawName) ?: run {
                skip(input, size)
                skipPadding(input, size)
                continue
            }

            when (type) {
                '0', '\u0000', '7' -> writeFile(input, clean, size, modeOf(header))
                '5' -> makeDir(clean, modeOf(header))
                '2' -> makeSymlink(clean, linkName)
                '1' -> makeHardLink(clean, linkName)
                else -> skip(input, size)
            }
            skipPadding(input, size)
            onBytes(total)
        }
    }

    // ------------------------------------------------------------------ entries

    private fun writeFile(input: InputStream, rel: String, size: Long, mode: Int) {
        val out = File(destRoot, rel)
        out.parentFile?.mkdirs()
        removeIfExists(out)
        out.outputStream().use { os ->
            copy(input, os, size)
        }
        chmod(out, mode and 0x1FF)
    }

    private fun makeDir(rel: String, mode: Int) {
        val dir = File(destRoot, rel)
        dir.mkdirs()
        chmod(dir, mode and 0x1FF)
    }

    private fun makeSymlink(rel: String, target: String) {
        if (target.isBlank()) return
        val link = File(destRoot, rel)
        link.parentFile?.mkdirs()
        removeIfExists(link)
        try {
            Os.symlink(target, link.absolutePath)
        } catch (_: Exception) {
            // Certains systèmes de fichiers Android refusent les liens symboliques :
            // on copie alors la cible si elle existe déjà (dégradé mais utilisable).
            val src = File(destRoot, target.removePrefix("/"))
            if (src.exists()) {
                try {
                    src.copyTo(link, overwrite = true)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun makeHardLink(rel: String, target: String) {
        if (target.isBlank()) return
        val link = File(destRoot, rel)
        link.parentFile?.mkdirs()
        removeIfExists(link)
        val src = File(destRoot, target.removePrefix("./").removePrefix("/"))
        try {
            Os.link(src.absolutePath, link.absolutePath)
        } catch (_: Exception) {
            try {
                src.copyTo(link, overwrite = true)
            } catch (_: Exception) {
            }
        }
    }

    private fun removeIfExists(f: File) {
        try {
            if (f.exists() || f.isDirectory) f.deleteRecursively()
        } catch (_: Exception) {
            try {
                f.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun chmod(f: File, mode: Int) {
        try {
            Os.chmod(f.absolutePath, if (mode == 0) 0b111_101_101 else mode)
        } catch (_: Exception) {
        }
    }

    // -------------------------------------------------------------------- utils

    private fun sanitize(raw: String): String? {
        var name = raw.replace('\\', '/')
        while (name.startsWith("./")) name = name.substring(2)
        name = name.trimStart('/')
        if (name.isEmpty() || name == ".") return null
        if (name.split('/').any { it == ".." }) return null
        return name
    }

    private fun nameOf(header: ByteArray): String {
        val base = nameField(header, 0, 100)
        val prefix = nameField(header, 345, 155)
        return if (prefix.isBlank()) base else "$prefix/$base"
    }

    private fun nameField(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = (offset + length).coerceAtMost(header.size)
        while (end < limit && header[end] != 0.toByte()) end++
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    private fun modeOf(header: ByteArray): Int = parseOctal(header, 100, 8).toInt()

    private fun parseOctal(header: ByteArray, offset: Int, length: Int): Long {
        // GNU tar peut encoder les très grandes tailles en base-256.
        if (header[offset].toInt().and(0x80) != 0) {
            var value = 0L
            for (i in offset + 1 until offset + length) {
                value = (value shl 8) or header[i].toLong().and(0xFF)
            }
            return value
        }
        var value = 0L
        for (i in offset until offset + length) {
            val c = header[i].toInt()
            if (c == 0 || c == ' '.code) continue
            if (c < '0'.code || c > '7'.code) break
            value = value * 8 + (c - '0'.code)
        }
        return value
    }

    private fun readFully(input: InputStream, buf: ByteArray, length: Int): Boolean {
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n < 0) return false
            read += n
            total += n
        }
        return true
    }

    private fun readData(input: InputStream, size: Long): ByteArray {
        val data = ByteArray(size.toInt())
        var read = 0
        while (read < data.size) {
            val n = input.read(data, read, data.size - read)
            if (n < 0) break
            read += n
            total += n
        }
        onBytes(total)
        return data
    }

    private fun copy(input: InputStream, out: java.io.OutputStream, size: Long) {
        var left = size
        val buf = ByteArray(128 * 1024)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            out.write(buf, 0, n)
            left -= n
            total += n
            onBytes(total)
        }
    }

    private fun skip(input: InputStream, size: Long) {
        var left = size
        val buf = ByteArray(64 * 1024)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            left -= n
            total += n
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val pad = ((512 - (size % 512)) % 512).toInt()
        if (pad == 0) return
        var left = pad
        val buf = ByteArray(pad)
        while (left > 0) {
            val n = input.read(buf, 0, left)
            if (n < 0) break
            left -= n
            total += n
        }
    }
}
