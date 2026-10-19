package com.adamglassner.larksong.alarm

import com.adamglassner.larksong.data.Alarm
import com.adamglassner.larksong.data.Bedtime
import com.adamglassner.larksong.data.Store
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * On Windows there is no system alarm service, so Larksong checks the clock itself
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

    /** How long before an alarm the "Upcoming alarm" notification appears. */
    const val UPCOMING_MS = 2 * 60 * 60 * 1000L

    /** Next time (epoch ms) this alarm should ring, after [from], leaving out a ring that was dismissed early. */
    fun nextTrigger(a: Alarm, from: ZonedDateTime = ZonedDateTime.now()): Long {
        val start = if (a.skipUntil > from.toInstant().toEpochMilli()) {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(a.skipUntil), from.zone)
        } else from
        return nextRing(a, start)
    }

    private fun nextRing(a: Alarm, from: ZonedDateTime): Long {
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

    /**
     * Dismisses the alarm's next ring ahead of time (for when you're up before it).
     * A one-time alarm is turned off; a repeating one rings again on its next day.
     */
    fun dismissNext(a: Alarm): Alarm {
        val updated = if (a.days.isEmpty()) a.copy(enabled = false, skipUntil = 0L) else a.copy(skipUntil = nextTrigger(a))
        Store.upsertAlarm(updated)
        return updated
    }

    fun undoDismiss(a: Alarm): Alarm {
        val updated = a.copy(skipUntil = 0L)
        Store.upsertAlarm(updated)
        return updated
    }

    /** The enabled alarm that rings next, with its ring time, or null. */
    fun soonest(): Pair<Alarm, Long>? =
        Store.alarms.value.filter { it.enabled }.map { it to nextTrigger(it) }.minByOrNull { it.second }

    private val announced = ConcurrentHashMap<Int, Long>()

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
    suspend fun loop(onBedtime: (String, String) -> Unit, onUpcoming: (Alarm, Long) -> Unit = { _, _ -> }) {
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
                } else if (t - now <= UPCOMING_MS && announced[a.id] != t) {
                    // Once per ring: a heads-up that this alarm is coming, so it can be dismissed early.
                    announced[a.id] = t
                    onUpcoming(a, t)
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
                        "Time to wind down. Bed at ${com.adamglassner.larksong.ui.formatTime(b.bedHour, b.bedMinute, use24)}, " +
                            "up at ${com.adamglassner.larksong.ui.formatTime(b.wakeHour, b.wakeMinute, use24)}.",
                    )
                }
            }
            last = now
        }
    }
}
