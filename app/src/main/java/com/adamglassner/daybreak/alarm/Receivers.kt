package com.adamglassner.daybreak.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.adamglassner.daybreak.DaybreakApp
import com.adamglassner.daybreak.MainActivity
import com.adamglassner.daybreak.R
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.ui.formatTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val kind = intent.getStringExtra(Scheduler.EXTRA_KIND) ?: Scheduler.KIND_ALARM
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, 0)

        when (kind) {
            Scheduler.KIND_ALARM -> {
                val a = Store.alarm(id) ?: return
                if (!a.enabled) return
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
            val n = NotificationCompat.Builder(context, DaybreakApp.CHANNEL_BEDTIME)
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
