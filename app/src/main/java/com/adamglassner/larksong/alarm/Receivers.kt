package com.adamglassner.larksong.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.adamglassner.larksong.LarksongApp
import com.adamglassner.larksong.MainActivity
import com.adamglassner.larksong.R
import com.adamglassner.larksong.data.Store
import com.adamglassner.larksong.ui.formatTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val kind = intent.getStringExtra(Scheduler.EXTRA_KIND) ?: Scheduler.KIND_ALARM
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, 0)

        when (kind) {
            Scheduler.KIND_ALARM -> {
                val a = Store.alarm(id) ?: return
                if (!a.enabled) return
                Upcoming.hide(context, id)
                if (a.days.isEmpty()) {
                    Store.upsertAlarm(a.copy(enabled = false))
                } else {
                    Scheduler.schedule(context, a)
                }
            }
            Scheduler.KIND_SNOOZE -> if (Store.alarm(id) == null) return
            Scheduler.KIND_TIMER -> {
                val t = Store.timer.value
                Store.setTimer(t.copy(endAt = 0L, remainingMs = t.totalMs))
            }
        }

        val svc = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_RING
            putExtra(Scheduler.EXTRA_KIND, kind)
            putExtra(Scheduler.EXTRA_ID, id)
        }
        ContextCompat.startForegroundService(context, svc)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Scheduler.rescheduleAll(context)
    }
}

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val b = Store.settings.value.bedtime
        if (b.enabled) {
            val use24 = Store.settings.value.use24h
            val open = PendingIntent.getActivity(
                context, 3, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, LarksongApp.CHANNEL_BEDTIME)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Bedtime in ${b.remindMinutes} min")
                .setContentText(
                    "Time to wind down. Bed at ${formatTime(b.bedHour, b.bedMinute, use24)}, " +
                        "up at ${formatTime(b.wakeHour, b.wakeMinute, use24)}.",
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(4242, n) }
        }
        Scheduler.scheduleBedtime(context)
    }
}

/** The "Upcoming alarm" notification, shown a couple of hours before an alarm, with a Dismiss button. */
object Upcoming {
    const val ACTION_SHOW = "com.adamglassner.larksong.UPCOMING_SHOW"
    const val ACTION_DISMISS = "com.adamglassner.larksong.UPCOMING_DISMISS"
    private const val NOTIFY_BASE = 7000

    fun show(ctx: Context, a: com.adamglassner.larksong.data.Alarm) {
        if (!a.enabled) return
        val at = Scheduler.nextTrigger(a)
        if (at - System.currentTimeMillis() > Scheduler.UPCOMING_MS + 60_000L) return
        val use24 = Store.settings.value.use24h
        val time = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
        val day = time.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        val dismiss = PendingIntent.getBroadcast(
            ctx, 610_000 + a.id,
            Intent(ctx, UpcomingReceiver::class.java).setAction(ACTION_DISMISS).putExtra(Scheduler.EXTRA_ID, a.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java).putExtra("tab", 1),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, LarksongApp.CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Upcoming alarm")
            .setContentText("$day ${formatTime(time.hour, time.minute, use24)}" + if (a.label.isNotBlank()) " · ${a.label}" else "")
            .setContentIntent(open)
            .addAction(0, "Dismiss now", dismiss)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(NOTIFY_BASE + a.id, n) }
    }

    fun hide(ctx: Context, id: Int) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFY_BASE + id) }
    }
}

class UpcomingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, 0)
        val a = Store.alarm(id) ?: return
        when (intent.action) {
            Upcoming.ACTION_SHOW -> Upcoming.show(context, a)
            Upcoming.ACTION_DISMISS -> if (a.enabled) Scheduler.dismissNext(context, a)
        }
    }
}
