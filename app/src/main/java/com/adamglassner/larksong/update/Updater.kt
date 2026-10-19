package com.adamglassner.larksong.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Checks GitHub Releases for a newer Larksong and downloads it.
 * Releases are tagged per platform: android-v1.2.0, windows-v1.2.0, …
 */
object Updater {
    /** The GitHub repository that publishes Larksong builds (must be public). */
    const val REPO = "adamglassner/larksong"

    data class Release(val version: String, val title: String, val notes: String, val assetUrl: String, val assetName: String, val size: Long, val page: String)

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Available(val release: Release) : Status
        data class Downloading(val release: Release, val progress: Float) : Status
        data class Ready(val release: Release, val file: File) : Status
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun open(url: String): HttpURLConnection {
        var u = url
        repeat(6) {
            val c = URI(u).toURL().openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "Larksong-updater")
            c.setRequestProperty("Accept", if (u.contains("api.github.com")) "application/vnd.github+json" else "application/octet-stream")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: error("Bad redirect")
                c.disconnect()
                u = URI(u).resolve(loc).toString()
            } else {
                return c
            }
        }
        error("Too many redirects")
    }

    /** Looks for the newest release for this platform. Updates [status]. */
    suspend fun check(currentVersion: String, tagPrefix: String, assetSuffix: String): Status = withContext(Dispatchers.IO) {
        _status.value = Status.Checking
        val result: Status = try {
            val c = open("https://api.github.com/repos/$REPO/releases?per_page=30")
            val code = c.responseCode
            if (code == 404) {
                Status.Failed("Updates aren't available yet: the Larksong repository on GitHub needs to be public.")
            } else if (code !in 200..299) {
                Status.Failed("GitHub didn't answer (error $code). Try again later.")
            } else {
                val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
                var best: Release? = null
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    val tag = r.optString("tag_name")
                    if (!tag.startsWith(tagPrefix) || r.optBoolean("draft") || r.optBoolean("prerelease")) continue
                    val version = tag.removePrefix(tagPrefix)
                    val assets = r.optJSONArray("assets") ?: continue
                    val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(assetSuffix, ignoreCase = true) } ?: continue
                    val rel = Release(
                        version, r.optString("name"), r.optString("body"),
                        asset.optString("browser_download_url"), asset.optString("name"), asset.optLong("size"),
                        r.optString("html_url"),
                    )
                    if (best == null || compare(rel.version, best.version) > 0) best = rel
                }
                c.disconnect()
                if (best != null && compare(best.version, currentVersion) > 0) Status.Available(best) else Status.UpToDate
            }
        } catch (e: Exception) {
            Status.Failed("Couldn't check for updates. Check your internet connection.")
        }
        _status.value = result
        result
    }

    /** Downloads the release's file into [dir]. Updates [status] with progress. */
    suspend fun download(release: Release, dir: File): File? = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val dest = File(dir, release.assetName)
            val part = File(dir, release.assetName + ".part")
            _status.value = Status.Downloading(release, 0f)
            val c = open(release.assetUrl)
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val f = if (total > 0) done.toFloat() / total else 0f
                        if (f - lastShown >= 0.01f) { lastShown = f; _status.value = Status.Downloading(release, f) }
                    }
                }
            }
            c.disconnect()
            dest.delete()
            if (!part.renameTo(dest)) error("Couldn't save the download")
            _status.value = Status.Ready(release, dest)
            dest
        } catch (e: Exception) {
            _status.value = Status.Failed("The download didn't finish. Try again.")
            null
        }
    }

    fun reset() { _status.value = Status.Idle }
}
