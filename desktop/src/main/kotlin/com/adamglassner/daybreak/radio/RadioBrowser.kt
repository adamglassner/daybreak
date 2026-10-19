package com.adamglassner.daybreak.radio

import com.adamglassner.daybreak.data.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder

data class Country(val code: String, val name: String, val stations: Int)

/**
 * Client for Radio Browser (https://www.radio-browser.info), a free, open directory of
 * internet radio stations. No key is needed; the project asks apps to pick a server from
 * its DNS list, send an identifying User-Agent, and report station clicks.
 */
object RadioBrowser {
    private const val AGENT = "Daybreak/0.2 (github.com/adamglassner/daybreak)"
    private val FALLBACK = listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info", "fi1.api.radio-browser.info", "nl1.api.radio-browser.info")

    /** Set on platforms that can only play some formats (Windows plays MP3 streams). */
    var codecFilter: String? = null

    @Volatile private var servers: List<String>? = null

    private fun discover(): List<String> {
        servers?.let { return it }
        val found = runCatching {
            InetAddress.getAllByName("all.api.radio-browser.info")
                .mapNotNull { runCatching { it.canonicalHostName }.getOrNull() }
                .filter { it.endsWith("radio-browser.info") }
                .distinct()
                .shuffled()
        }.getOrDefault(emptyList())
        val list = (found + FALLBACK.shuffled()).distinct()
        servers = list
        return list
    }

    private fun httpGet(url: String): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", AGENT)
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** GET a path, trying each server until one answers. */
    private fun get(path: String): String {
        var last: Exception? = null
        for (host in discover()) {
            try {
                return httpGet("https://$host$path")
            } catch (e: Exception) {
                last = e
            }
        }
        servers = null
        throw last ?: IllegalStateException("No radio directory server reachable")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** [minPopularity]: leave out stations with fewer plays + votes than this (obscure or abandoned ones). */
    private fun parseStations(json: String, minPopularity: Int = 0): List<Station> {
        val a = JSONArray(json)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            if (url.isBlank() || o.optInt("lastcheckok", 1) == 0) return@mapNotNull null
            if (o.optInt("clickcount") + o.optInt("votes") < minPopularity) return@mapNotNull null
            val kbps = o.optInt("bitrate")
            if (minPopularity > 0 && kbps in 1..47) return@mapNotNull null
            Station(
                uuid = o.optString("stationuuid"),
                name = o.optString("name").trim().ifBlank { "Unnamed station" },
                url = url,
                country = o.optString("country"),
                countryCode = o.optString("countrycode"),
                tags = o.optString("tags"),
                codec = o.optString("codec"),
                bitrate = o.optInt("bitrate"),
                favicon = o.optString("favicon"),
            )
        }.distinctBy { it.name.lowercase() + it.countryCode }
    }

    private fun search(params: String, limit: Int, minPopularity: Int = 0): List<Station> {
        val codec = codecFilter?.let { "&codec=${enc(it)}" } ?: ""
        return parseStations(get("/json/stations/search?hidebroken=true&limit=$limit$codec&$params"), minPopularity)
    }

    suspend fun topStations(countryCode: String, limit: Int = 100): List<Station> = withContext(Dispatchers.IO) {
        search("countrycode=${enc(countryCode)}&order=clickcount&reverse=true", limit)
    }

    suspend fun searchByName(query: String, limit: Int = 100): List<Station> = withContext(Dispatchers.IO) {
        val byName = search("name=${enc(query.trim())}&order=clickcount&reverse=true", limit)
        if (byName.size >= 10) byName
        else (byName + search("tag=${enc(query.trim().lowercase())}&order=clickcount&reverse=true", limit)).distinctBy { it.uuid }
    }

    /**
     * Random stations, optionally of one style ([tag]) or from one country, favoring stations
     * people actually listen to so a spin rarely lands on a dead or empty stream.
     */
    suspend fun randomStations(tag: String? = null, countryCode: String? = null, limit: Int = 40): List<Station> =
        withContext(Dispatchers.IO) {
            val params = buildString {
                append("order=random")
                if (tag != null) append("&tag=${enc(tag)}")
                if (countryCode != null) append("&countrycode=${enc(countryCode)}")
            }
            val good = search(params, 200, minPopularity = 15)
            (if (good.size >= 8) good else (good + search(params, 100)).distinctBy { it.uuid }).take(limit)
        }

    suspend fun countries(): List<Country> = withContext(Dispatchers.IO) {
        val a = JSONArray(get("/json/countries?order=name&hidebroken=true"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val code = o.optString("iso_3166_1")
            val count = o.optInt("stationcount")
            if (code.length == 2 && count > 0) Country(code.uppercase(), o.optString("name"), count) else null
        }.sortedBy { it.name }
    }

    /** Tells the directory a station was played (it uses this to rank popular stations). */
    suspend fun reportClick(uuid: String) = withContext(Dispatchers.IO) {
        if (uuid.isNotBlank()) runCatching { get("/json/url/${enc(uuid)}") }
    }
}
