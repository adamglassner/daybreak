package com.adamglassner.larksong.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.adamglassner.larksong.data.City
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class WeatherNow(
    val place: String,
    val gps: Boolean,
    val temp: Int,
    val code: Int,
    val high: Int,
    val low: Int,
)

/** A place to show weather for: either the phone's location or a saved city. */
data class WeatherPlace(val name: String, val gps: Boolean, val city: City?)

object Weather {

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Larksong-Android")
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetch(lat: Double, lon: Double, celsius: Boolean, place: String, gps: Boolean): WeatherNow? =
        withContext(Dispatchers.IO) {
            runCatching {
                val unit = if (celsius) "celsius" else "fahrenheit"
                val url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                        "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                        "&temperature_unit=%s&timezone=auto&forecast_days=1",
                    lat, lon, unit,
                )
                val o = JSONObject(get(url))
                val cur = o.getJSONObject("current")
                val daily = o.getJSONObject("daily")
                WeatherNow(
                    place = place,
                    gps = gps,
                    temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
                    code = cur.getInt("weather_code"),
                    high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                    low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                )
            }.getOrNull()
        }

    suspend fun searchCities(query: String): List<City> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=en&format=json"))
            val arr = o.optJSONArray("results") ?: return@runCatching emptyList<City>()
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                val region = listOf(r.optString("admin1", ""), r.optString("country", ""))
                    .filter { it.isNotBlank() }.joinToString(", ")
                City(r.getString("name"), region, r.getDouble("latitude"), r.getDouble("longitude"))
            }
        }.getOrDefault(emptyList())
    }

    fun hasLocationPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    suspend fun currentLocation(ctx: Context): Location? {
        if (!hasLocationPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val last = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3600_000L) return last
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = providers.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            if (provider != null) {
                val fresh = withTimeoutOrNull(15_000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        runCatching {
                            lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(ctx)) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        }.onFailure { if (cont.isActive) cont.resume(null) }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return last
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorms"
        else -> "—"
    }
}
