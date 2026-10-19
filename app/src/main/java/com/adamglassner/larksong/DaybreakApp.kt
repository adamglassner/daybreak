package com.adamglassner.larksong

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.adamglassner.larksong.data.Store
import com.adamglassner.larksong.radio.AndroidRadioEngine
import com.adamglassner.larksong.radio.Radio

class LarksongApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        com.adamglassner.larksong.ui.Notices.init(this)
        Radio.engine = AndroidRadioEngine(this)
        Radio.restoreLastStation()
        com.adamglassner.larksong.sleep.SleepSounds.output = com.adamglassner.larksong.sleep.AndroidSleepOutput(this)
        com.adamglassner.larksong.sleep.SleepSounds.restore()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows when an alarm or timer is ringing"
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SLEEP, "Sleep sounds", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while sleep sounds are playing, with a button to stop them"
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPCOMING, "Upcoming alarms", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows your next alarm a couple of hours ahead, with a button to dismiss it early"
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
        const val CHANNEL_UPCOMING = "upcoming"
        const val CHANNEL_SLEEP = "sleep"
    }
}
