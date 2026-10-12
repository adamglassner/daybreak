package com.adamglassner.daybreak

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.concurrent.TimeUnit

object Platform {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")

    fun dataDir(): File {
        val base = System.getenv("APPDATA")?.let { File(it) } ?: File(System.getProperty("user.home"), ".config")
        return File(base, "Daybreak").apply { mkdirs() }
    }

    /** The installed Daybreak.exe, when running from the installer (not from a dev build). */
    private val appExe: String? get() = System.getProperty("jpackage.app-path")

    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    val canAutostart: Boolean get() = isWindows && appExe != null

    private fun reg(vararg args: String): Int = runCatching {
        val p = ProcessBuilder(listOf("reg") + args).redirectErrorStream(true).start()
        p.inputStream.readAllBytes()
        if (p.waitFor(10, TimeUnit.SECONDS)) p.exitValue() else -1
    }.getOrDefault(-1)

    fun isAutostartOn(): Boolean = canAutostart && reg("query", RUN_KEY, "/v", "Daybreak") == 0

    fun setAutostart(on: Boolean) {
        val exe = appExe ?: return
        if (on) reg("add", RUN_KEY, "/v", "Daybreak", "/t", "REG_SZ", "/d", "\"$exe\" --tray", "/f")
        else reg("delete", RUN_KEY, "/v", "Daybreak", "/f")
    }

    /** Native "Open" dialog for an audio file. Returns the chosen path or null. */
    fun pickAudioFile(): File? {
        val dialog = FileDialog(null as Frame?, "Choose an alarm sound", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".aif") || it.endsWith(".aiff") || it.endsWith(".au") } }
        if (isWindows) dialog.file = "*.mp3;*.wav;*.aif;*.aiff;*.au"
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        return File(dir, name)
    }

    /** Windows' own alarm and notification sounds (C:\Windows\Media). */
    fun systemSounds(): List<File> {
        val media = File(System.getenv("WINDIR") ?: "C:\\Windows", "Media")
        return media.listFiles { f -> f.isFile && f.extension.equals("wav", true) }
            ?.sortedWith(compareBy({ !it.name.startsWith("Alarm", true) }, { it.name.lowercase() }))
            ?: emptyList()
    }
}
