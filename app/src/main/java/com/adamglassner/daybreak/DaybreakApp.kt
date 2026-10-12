package com.adamglassner.daybreak

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.adamglassner.daybreak.data.Store

class DaybreakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows when an alarm or timer is ringing"
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BEDTIME, "Bedtime reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminds you when it's almost bedtime"
            },
        )
    }

    companion object {
        const val CHANNEL_ALARM = "ringing"
        const val CHANNEL_BEDTIME = "bedtime"
    }
}
