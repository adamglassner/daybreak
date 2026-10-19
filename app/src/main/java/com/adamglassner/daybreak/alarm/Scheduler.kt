package com.adamglassner.daybreak.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.adamglassner.daybreak.MainActivity
import com.adamglassner.daybreak.data.Alarm
import com.adamglassner.daybreak.data.Bedtime
import com.adamglassner.daybreak.data.Store
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object Scheduler {
    const val KIND_ALARM = "alarm"
    const val KIND_SNOOZE = "snooze"
    const val KIND_TIMER = "timer"
    const val KIND_PREVIEW = "preview"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "alarmId"

    private const val TIMER_REQ = 900_001
    private const val BEDTIME_REQ = 900_002
    private const val SNOOZE_BASE = 500_000
    private const val UPCOMING_BASE = 600_000

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    /** How long before an alarm the "Upcoming alarm" notification (with Dismiss) appears. */
    const val UPCOMING_MS = 2 * 60 * 60 * 1000L

    /** Next time (epoch ms) this alarm should ring, after [from], leaving out a ring that was dismissed early. */
    fun nextTrigger(a: Alarm, from: ZonedDateTime = ZonedDateTime.now()): Long {
        val start = if (a.skipUntil > from.toInstant().toEpochMilli()) {
            ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(a.skipUntil), from.zone)
        } else from
        return nextRing(a, start)
    }

    /** True when the alarm's next ring has been dismissed ahead of time. */
    fun isSkipping(a: Alarm): Boolean = a.enabled && a.skipUntil > System.currentTimeMillis()

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

    private fun ringIntent(ctx: Context, kind: String, id: Int, req: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.adamglassner.daybreak.RING.$req"
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).putExtra("tab", 1),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setClock(ctx: Context, at: Long, pi: PendingIntent) {
        val m = am(ctx)
        try {
            m.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(ctx)), pi)
        } catch (e: SecurityException) {
            m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun schedule(ctx: Context, a: Alarm) {
        val pi = ringIntent(ctx, KIND_ALARM, a.id, a.id)
        val up = upcomingIntent(ctx, a.id)
        Upcoming.hide(ctx, a.id)
        if (!a.enabled) {
            am(ctx).cancel(pi); am(ctx).cancel(up)
            return
        }
        val at = nextTrigger(a)
        setClock(ctx, at, pi)
        val showAt = at - UPCOMING_MS
        if (showAt <= System.currentTimeMillis()) {
            am(ctx).cancel(up)
            Upcoming.show(ctx, a)
        } else {
            runCatching { am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showAt, up) }
        }
    }

    fun cancel(ctx: Context, id: Int) {
        am(ctx).cancel(ringIntent(ctx, KIND_ALARM, id, id))
        am(ctx).cancel(upcomingIntent(ctx, id))
        Upcoming.hide(ctx, id)
        cancelSnooze(ctx, id)
    }

    private fun upcomingIntent(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, UPCOMING_BASE + id,
            Intent(ctx, UpcomingReceiver::class.java).setAction(Upcoming.ACTION_SHOW).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Dismisses the alarm's next ring ahead of time (for when you're up before it).
     * A one-time alarm is simply turned off; a repeating one rings again on its next day.
     * Returns the updated alarm.
     */
    fun dismissNext(ctx: Context, a: Alarm): Alarm {
        val updated = if (a.days.isEmpty()) a.copy(enabled = false, skipUntil = 0L) else a.copy(skipUntil = nextTrigger(a))
        Store.upsertAlarm(updated)
        schedule(ctx, updated)
        return updated
    }

    /** Undoes [dismissNext] for a repeating alarm. */
    fun undoDismiss(ctx: Context, a: Alarm): Alarm {
        val updated = a.copy(skipUntil = 0L)
        Store.upsertAlarm(updated)
        schedule(ctx, updated)
        return updated
    }

    fun snooze(ctx: Context, a: Alarm) {
        val at = System.currentTimeMillis() + a.snoozeMinutes * 60_000L
        setClock(ctx, at, ringIntent(ctx, KIND_SNOOZE, a.id, SNOOZE_BASE + a.id))
    }

    fun cancelSnooze(ctx: Context, id: Int) {
        am(ctx).cancel(ringIntent(ctx, KIND_SNOOZE, id, SNOOZE_BASE + id))
    }

    fun scheduleTimer(ctx: Context, endAt: Long) {
        setClock(ctx, endAt, ringIntent(ctx, KIND_TIMER, 0, TIMER_REQ))
    }

    fun cancelTimer(ctx: Context) {
        am(ctx).cancel(ringIntent(ctx, KIND_TIMER, 0, TIMER_REQ))
    }

    // ---------- Bedtime reminder ----------

    private fun bedtimeIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, BEDTIME_REQ, Intent(ctx, BedtimeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun nextBedtimeReminder(b: Bedtime, from: ZonedDateTime = ZonedDateTime.now()): Long? {
        if (b.days.isEmpty()) return null
        var day = from.toLocalDate()
        repeat(9) {
            if (day.dayOfWeek.value in b.days) {
                val bed = LocalDateTime.of(day, java.time.LocalTime.of(b.bedHour, b.bedMinute))
                val remind = bed.minusMinutes(b.remindMinutes.toLong()).atZone(ZoneId.systemDefault())
                if (remind.isAfter(from)) return remind.toInstant().toEpochMilli()
            }
            day = day.plusDays(1)
        }
        return null
    }

    fun scheduleBedtime(ctx: Context) {
        val b = Store.settings.value.bedtime
        val pi = bedtimeIntent(ctx)
        val at = if (b.enabled) nextBedtimeReminder(b) else null
        if (at == null) { am(ctx).cancel(pi); return }
        val m = am(ctx)
        try {
            if (canScheduleExact(ctx)) m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun rescheduleAll(ctx: Context) {
        Store.alarms.value.forEach { schedule(ctx, it) }
        val t = Store.timer.value
        if (t.endAt > System.currentTimeMillis()) scheduleTimer(ctx, t.endAt)
        scheduleBedtime(ctx)
    }
}
