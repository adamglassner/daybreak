package com.adamglassner.daybreak.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ClockStyle { STOCK, COLORS, RETRO, RADIO, TERMINAL, CLASSIC }
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Days use java.time.DayOfWeek numbering: 1 = Monday … 7 = Sunday. */
data class Alarm(
    val id: Int,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Set<Int> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    val soundUri: String? = null,
    val soundName: String = "Default alarm",
    val vibrate: Boolean = true,
    val gradual: Boolean = true,
    val rampSeconds: Int = 60,
    val snoozeMinutes: Int = 10,
)

data class City(val name: String, val region: String, val lat: Double, val lon: Double)

/** A live radio station from the Radio Browser directory. */
data class Station(
    val uuid: String,
    val name: String,
    val url: String,
    val country: String = "",
    val countryCode: String = "",
    val tags: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val favicon: String = "",
)

const val PRESET_COUNT = 6

data class Bedtime(
    val enabled: Boolean = false,
    val bedHour: Int = 23,
    val bedMinute: Int = 0,
    val wakeHour: Int = 6,
    val wakeMinute: Int = 30,
    val remindMinutes: Int = 30,
    val days: Set<Int> = setOf(7, 1, 2, 3, 4),
    val linkAlarm: Boolean = false,
    val linkedAlarmId: Int = 0,
)

data class Settings(
    val style: ClockStyle = ClockStyle.STOCK,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val use24h: Boolean = false,
    val showSeconds: Boolean = true,
    val celsius: Boolean = false,
    val useGps: Boolean = true,
    val cities: List<City> = emptyList(),
    val worldZones: List<String> = listOf("Europe/London", "Asia/Tokyo"),
    val timerSoundUri: String? = null,
    val timerSoundName: String = "Default alarm",
    val bedtime: Bedtime = Bedtime(),
    val radioStation: Station? = null,
    val radioPresets: List<Station?> = List(PRESET_COUNT) { null },
    val radioCountry: String = "",
)

data class TimerState(
    val totalMs: Long = 5 * 60_000L,
    val remainingMs: Long = 5 * 60_000L,
    val endAt: Long = 0L, // 0 = not running
)

data class StopwatchState(
    val baseMs: Long = 0L,
    val startedAt: Long = 0L, // 0 = not running
    val laps: List<Long> = emptyList(), // lap split durations, newest first
) {
    fun elapsed(now: Long): Long = baseMs + if (startedAt > 0) now - startedAt else 0
}

object Store {
    private lateinit var prefs: SharedPreferences

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    private val _stopwatch = MutableStateFlow(StopwatchState())
    val stopwatch: StateFlow<StopwatchState> = _stopwatch.asStateFlow()

    @Volatile private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        prefs = context.applicationContext.getSharedPreferences("daybreak", Context.MODE_PRIVATE)
        _alarms.value = runCatching { parseAlarms(prefs.getString("alarms", null)) }.getOrDefault(emptyList())
        _settings.value = runCatching { parseSettings(prefs.getString("settings", null)) }.getOrDefault(Settings())
        _timer.value = runCatching { parseTimer(prefs.getString("timer", null)) }.getOrDefault(TimerState())
        _stopwatch.value = runCatching { parseStopwatch(prefs.getString("stopwatch", null)) }.getOrDefault(StopwatchState())
        loaded = true
    }

    fun alarm(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun nextAlarmId(): Int = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    fun upsertAlarm(alarm: Alarm) {
        val list = _alarms.value.toMutableList()
        val i = list.indexOfFirst { it.id == alarm.id }
        if (i >= 0) list[i] = alarm else list.add(alarm)
        list.sortWith(compareBy({ it.hour }, { it.minute }))
        _alarms.value = list
        prefs.edit().putString("alarms", alarmsJson(list)).apply()
    }

    fun deleteAlarm(id: Int) {
        val list = _alarms.value.filter { it.id != id }
        _alarms.value = list
        prefs.edit().putString("alarms", alarmsJson(list)).apply()
    }

    fun updateSettings(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        _settings.value = s
        prefs.edit().putString("settings", settingsJson(s)).apply()
    }

    fun setTimer(t: TimerState) {
        _timer.value = t
        prefs.edit().putString("timer", JSONObject().apply {
            put("total", t.totalMs); put("remaining", t.remainingMs); put("endAt", t.endAt)
        }.toString()).apply()
    }

    fun setStopwatch(s: StopwatchState) {
        _stopwatch.value = s
        prefs.edit().putString("stopwatch", JSONObject().apply {
            put("base", s.baseMs); put("startedAt", s.startedAt)
            put("laps", JSONArray().apply { s.laps.forEach { put(it) } })
        }.toString()).apply()
    }

    // ---------- backup & restore ----------
    // A .json file shared with Daybreak Classic, so alarms can move between devices.

    /** Everything worth keeping: alarms, bedtime and world clocks. */
    fun exportBackup(appName: String): String = JSONObject().apply {
        put("format", "daybreak-backup")
        put("version", 1)
        put("app", appName)
        put("alarms", JSONArray(alarmsJson(_alarms.value)))
        put("bedtime", JSONObject(settingsJson(_settings.value)).getJSONObject("bedtime"))
        put("worldZones", JSONArray().apply { _settings.value.worldZones.forEach { put(it) } })
    }.toString(2)

    /** Counts the alarms in a backup, or returns -1 if the text isn't a Daybreak backup. */
    fun backupAlarmCount(text: String): Int = runCatching {
        JSONObject(text).getJSONArray("alarms").length()
    }.getOrDefault(-1)

    /**
     * Restores a backup. [replace] swaps out the current alarms (and bedtime and world clocks);
     * otherwise the backup's alarms are added. Returns how many alarms were added.
     */
    fun importBackup(text: String, replace: Boolean): Int {
        val o = JSONObject(text)
        val incoming = parseAlarms(o.getJSONArray("alarms").toString())
        val keep = if (replace) emptyList() else _alarms.value
        var nextId = (keep.maxOfOrNull { it.id } ?: 0) + 1
        val added = incoming.map { a ->
            a.copy(
                id = nextId++,
                hour = a.hour.coerceIn(0, 23),
                minute = a.minute.coerceIn(0, 59),
                snoozeMinutes = a.snoozeMinutes.coerceIn(1, 60),
            )
        }
        val list = (keep + added).sortedWith(compareBy({ it.hour }, { it.minute }))
        _alarms.value = list
        prefs.edit().putString("alarms", alarmsJson(list)).apply()
        if (replace) {
            val b = o.optJSONObject("bedtime")
            val zones = o.optJSONArray("worldZones")
            updateSettings { s ->
                var n = s
                if (b != null) {
                    n = n.copy(
                        bedtime = Bedtime(
                            enabled = b.optBoolean("enabled", false),
                            bedHour = b.optInt("bedHour", 23).coerceIn(0, 23), bedMinute = b.optInt("bedMinute", 0).coerceIn(0, 59),
                            wakeHour = b.optInt("wakeHour", 6).coerceIn(0, 23), wakeMinute = b.optInt("wakeMinute", 30).coerceIn(0, 59),
                            remindMinutes = b.optInt("remind", 30),
                            days = b.optJSONArray("days")?.let { parseDays(it) } ?: Bedtime().days,
                            linkAlarm = false,
                            linkedAlarmId = 0,
                        ),
                    )
                }
                if (zones != null && zones.length() > 0) {
                    n = n.copy(worldZones = (0 until zones.length()).map { zones.getString(it) })
                }
                n
            }
        }
        return added.size
    }

    // ---------- JSON ----------

    private fun daysJson(days: Set<Int>) = JSONArray().apply { days.sorted().forEach { put(it) } }
    private fun parseDays(a: JSONArray?): Set<Int> {
        if (a == null) return emptySet()
        return (0 until a.length()).map { a.getInt(it) }.toSet()
    }

    private fun alarmsJson(list: List<Alarm>): String = JSONArray().apply {
        list.forEach { a ->
            put(JSONObject().apply {
                put("id", a.id); put("hour", a.hour); put("minute", a.minute)
                put("days", daysJson(a.days)); put("label", a.label); put("enabled", a.enabled)
                put("soundUri", a.soundUri ?: ""); put("soundName", a.soundName)
                put("vibrate", a.vibrate); put("gradual", a.gradual)
                put("ramp", a.rampSeconds); put("snooze", a.snoozeMinutes)
            })
        }
    }.toString()

    private fun parseAlarms(s: String?): List<Alarm> {
        if (s.isNullOrEmpty()) return emptyList()
        val a = JSONArray(s)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Alarm(
                id = o.getInt("id"),
                hour = o.optInt("hour", 7),
                minute = o.optInt("minute", 0),
                days = parseDays(o.optJSONArray("days")),
                label = o.optString("label", ""),
                enabled = o.optBoolean("enabled", true),
                soundUri = o.optString("soundUri", "").ifEmpty { null },
                soundName = o.optString("soundName", "Default alarm"),
                vibrate = o.optBoolean("vibrate", true),
                gradual = o.optBoolean("gradual", true),
                rampSeconds = o.optInt("ramp", 60),
                snoozeMinutes = o.optInt("snooze", 10),
            )
        }
    }

    private fun settingsJson(s: Settings): String = JSONObject().apply {
        put("style", s.style.name); put("mode", s.mode.name)
        put("use24h", s.use24h); put("showSeconds", s.showSeconds); put("celsius", s.celsius)
        put("useGps", s.useGps)
        put("cities", JSONArray().apply {
            s.cities.forEach { c ->
                put(JSONObject().apply { put("name", c.name); put("region", c.region); put("lat", c.lat); put("lon", c.lon) })
            }
        })
        put("zones", JSONArray().apply { s.worldZones.forEach { put(it) } })
        put("timerSoundUri", s.timerSoundUri ?: ""); put("timerSoundName", s.timerSoundName)
        val b = s.bedtime
        put("bedtime", JSONObject().apply {
            put("enabled", b.enabled); put("bedHour", b.bedHour); put("bedMinute", b.bedMinute)
            put("wakeHour", b.wakeHour); put("wakeMinute", b.wakeMinute); put("remind", b.remindMinutes)
            put("days", daysJson(b.days)); put("link", b.linkAlarm); put("linkedId", b.linkedAlarmId)
        })
        s.radioStation?.let { put("radioStation", stationJson(it)) }
        put("radioPresets", JSONArray().apply { s.radioPresets.forEach { st -> put(st?.let { stationJson(it) } ?: JSONObject.NULL) } })
        put("radioCountry", s.radioCountry)
    }.toString()

    fun stationJson(st: Station): JSONObject = JSONObject().apply {
        put("uuid", st.uuid); put("name", st.name); put("url", st.url); put("country", st.country)
        put("countryCode", st.countryCode); put("tags", st.tags); put("codec", st.codec)
        put("bitrate", st.bitrate); put("favicon", st.favicon)
    }

    fun parseStation(o: JSONObject?): Station? {
        if (o == null || !o.has("url")) return null
        return Station(
            o.optString("uuid"), o.optString("name"), o.optString("url"), o.optString("country"),
            o.optString("countryCode"), o.optString("tags"), o.optString("codec"), o.optInt("bitrate"), o.optString("favicon"),
        )
    }

    private fun parseSettings(s: String?): Settings {
        if (s.isNullOrEmpty()) return Settings()
        val o = JSONObject(s)
        val citiesA = o.optJSONArray("cities") ?: JSONArray()
        val zonesA = o.optJSONArray("zones")
        val b = o.optJSONObject("bedtime") ?: JSONObject()
        return Settings(
            style = runCatching { ClockStyle.valueOf(o.optString("style")) }.getOrDefault(ClockStyle.STOCK),
            mode = runCatching { ThemeMode.valueOf(o.optString("mode")) }.getOrDefault(ThemeMode.SYSTEM),
            use24h = o.optBoolean("use24h", false),
            showSeconds = o.optBoolean("showSeconds", true),
            celsius = o.optBoolean("celsius", false),
            useGps = o.optBoolean("useGps", true),
            cities = (0 until citiesA.length()).map { i ->
                val c = citiesA.getJSONObject(i)
                City(c.optString("name"), c.optString("region"), c.optDouble("lat"), c.optDouble("lon"))
            },
            worldZones = if (zonesA == null) Settings().worldZones else (0 until zonesA.length()).map { zonesA.getString(it) },
            timerSoundUri = o.optString("timerSoundUri", "").ifEmpty { null },
            timerSoundName = o.optString("timerSoundName", "Default alarm"),
            bedtime = Bedtime(
                enabled = b.optBoolean("enabled", false),
                bedHour = b.optInt("bedHour", 23), bedMinute = b.optInt("bedMinute", 0),
                wakeHour = b.optInt("wakeHour", 6), wakeMinute = b.optInt("wakeMinute", 30),
                remindMinutes = b.optInt("remind", 30),
                days = b.optJSONArray("days")?.let { parseDays(it) } ?: Bedtime().days,
                linkAlarm = b.optBoolean("link", false),
                linkedAlarmId = b.optInt("linkedId", 0),
            ),
            radioStation = parseStation(o.optJSONObject("radioStation")),
            radioPresets = o.optJSONArray("radioPresets").let { a ->
                List(PRESET_COUNT) { i -> if (a != null && i < a.length()) parseStation(a.optJSONObject(i)) else null }
            },
            radioCountry = o.optString("radioCountry", ""),
        )
    }

    private fun parseTimer(s: String?): TimerState {
        if (s.isNullOrEmpty()) return TimerState()
        val o = JSONObject(s)
        return TimerState(o.optLong("total", 300_000L), o.optLong("remaining", 300_000L), o.optLong("endAt", 0L))
    }

    private fun parseStopwatch(s: String?): StopwatchState {
        if (s.isNullOrEmpty()) return StopwatchState()
        val o = JSONObject(s)
        val laps = o.optJSONArray("laps") ?: JSONArray()
        return StopwatchState(o.optLong("base", 0L), o.optLong("startedAt", 0L), (0 until laps.length()).map { laps.getLong(it) })
    }
}
