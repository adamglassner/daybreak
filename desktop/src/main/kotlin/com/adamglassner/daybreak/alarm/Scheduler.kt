package com.adamglassner.daybreak.alarm

import com.adamglassner.daybreak.data.Alarm
import com.adamglassner.daybreak.data.Bedtime
import com.adamglassner.daybreak.data.Store
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * On Windows there is no system alarm service, so Daybreak checks the clock itself
 * while it's running (open or in the tray).
 */
object Scheduler {
    const val KIND_ALARM = "alarm"
    const val KIND_SNOOZE = "snooze"
    const val KIND_TIMER = "timer"
    const val KIND_PREVIEW = "preview"

    /** A missed alarm still rings if the PC wakes up within this window. */
    private const val LATE_GRACE_MS = 30 * 60_000L

    private val snoozes = ConcurrentHashMap<Int, Long>()

    fun nextTrigger(a: Alarm, from: ZonedDateTime = ZonedDateTime.now()): Long {
        var candidate = from.withHour(a.hour).withMinute(a.minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(from)) candidate = candidate.plusDays(1)
        if (a.days.isNotEmpty()) {
            var guard = 0
            while (candidate.dayOfWeek.value !in a.days && guard < 8) {
                candidate = candidate.plusDays(1); guard++
            }
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun nextBedtimeReminder(b: Bedtime, from: ZonedDateTime = ZonedDateTime.now()): Long? {
        if (b.days.isEmpty()) return null
        var day = from.toLocalDate()
        repeat(9) {
            if (day.dayOfWeek.value in b.days) {
                val bed = LocalDateTime.of(day, LocalTime.of(b.bedHour, b.bedMinute))
                val remind = bed.minusMinutes(b.remindMinutes.toLong()).atZone(ZoneId.systemDefault())
                if (remind.isAfter(from)) return remind.toInstant().toEpochMilli()
            }
            day = day.plusDays(1)
        }
        return null
    }

    // The engine reads alarms straight from the store, so these only manage snoozes.
    fun schedule(@Suppress("UNUSED_PARAMETER") a: Alarm) {}
    fun cancel(id: Int) { snoozes.remove(id) }
    fun snooze(a: Alarm) { snoozes[a.id] = System.currentTimeMillis() + a.snoozeMinutes * 60_000L }
    fun cancelSnooze(id: Int) { snoozes.remove(id) }
    fun scheduleTimer(@Suppress("UNUSED_PARAMETER") endAt: Long) {}
    fun cancelTimer() {}
    fun scheduleBedtime() {}

    private fun zoned(ms: Long) = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    /** Runs forever, checking twice a second for anything due since the last check. */
    suspend fun loop(onBedtime: (String, String) -> Unit) {
        var last = System.currentTimeMillis()
        while (true) {
            delay(500)
            val now = System.currentTimeMillis()
            if (now < last) { last = now; continue } // clock moved backwards
            val since = zoned(last)

            for (a in Store.alarms.value) {
                if (!a.enabled) continue
                val t = nextTrigger(a, since)
                if (t <= now) {
                    if (a.days.isEmpty()) Store.upsertAlarm(a.copy(enabled = false))
                    if (now - t <= LATE_GRACE_MS) AlarmService.ring(KIND_ALARM, a.id)
                }
            }

            for ((id, at) in snoozes.entries.toList()) {
                if (at <= now) {
                    snoozes.remove(id)
                    if (Store.alarm(id) != null) AlarmService.ring(KIND_SNOOZE, id)
                }
            }

            val timer = Store.timer.value
            if (timer.endAt in 1..now) {
                Store.setTimer(timer.copy(endAt = 0L, remainingMs = timer.totalMs))
                AlarmService.ring(KIND_TIMER, 0)
            }

            val b = Store.settings.value.bedtime
            if (b.enabled) {
                val r = nextBedtimeReminder(b, since)
                if (r != null && r <= now && now - r <= LATE_GRACE_MS) {
                    val use24 = Store.settings.value.use24h
                    onBedtime(
                        "Bedtime in ${b.remindMinutes} min",
                        "Time to wind down. Bed at ${com.adamglassner.daybreak.ui.formatTime(b.bedHour, b.bedMinute, use24)}, " +
                            "up at ${com.adamglassner.daybreak.ui.formatTime(b.wakeHour, b.wakeMinute, use24)}.",
                    )
                }
            }
            last = now
        }
    }
}
