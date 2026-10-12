package com.adamglassner.daybreak.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.ui.BigButton
import com.adamglassner.daybreak.ui.BigReadout
import com.adamglassner.daybreak.ui.DText
import com.adamglassner.daybreak.ui.DaybreakTheme
import com.adamglassner.daybreak.ui.DbIcons
import com.adamglassner.daybreak.ui.LedWindow
import com.adamglassner.daybreak.ui.LocalDb
import com.adamglassner.daybreak.ui.ampm
import com.adamglassner.daybreak.ui.clockDigits
import com.adamglassner.daybreak.ui.rememberNow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Back does nothing: choose Snooze or Stop.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        setContent {
            val settings by Store.settings.collectAsState()
            val ringing by AlarmService.ringing.collectAsState()
            LaunchedEffect(ringing) { if (ringing == null) finish() }
            DaybreakTheme(settings.style, settings.mode) {
                val r = ringing
                if (r != null) RingScreen(
                    r,
                    settings.use24h,
                    onSnooze = { AlarmService.snooze(this@RingActivity) },
                    onStop = { AlarmService.stop(this@RingActivity) },
                )
            }
        }
    }
}

@Composable
private fun RingScreen(r: AlarmService.Ringing, use24: Boolean, onSnooze: () -> Unit, onStop: () -> Unit) {
    val db = LocalDb.current
    val p = db.p
    val now by rememberNow(1000L)
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val isTimer = r.kind == Scheduler.KIND_TIMER
    Box(Modifier.fillMaxSize().background(p.bg).windowInsetsPadding(WindowInsets.systemBars)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(44.dp)).background(p.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isTimer) DbIcons.Timer else DbIcons.Alarm, null, tint = p.accent, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(24.dp))
            DText(r.title, 20.sp, p.muted, FontWeight.Bold, family = db.f.display)
            Spacer(Modifier.height(8.dp))
            if (db.style == ClockStyle.RADIO) {
                LedWindow { BigReadout(clockDigits(t.hour, t.minute, use24), 70.sp) }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    BigReadout(clockDigits(t.hour, t.minute, use24), 92.sp)
                    if (!use24) DText(" " + ampm(t.hour), 22.sp, p.muted, FontWeight.Bold, modifier = Modifier.padding(bottom = 18.dp))
                }
            }
            DText(t.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), 18.sp, p.muted)

            if (r.gradual) {
                Spacer(Modifier.height(36.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DText("Volume rising", 13.sp, p.muted)
                    DText("${(r.volume * 100).toInt()}%", 13.sp, p.muted)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.surface2)) {
                    Box(Modifier.fillMaxWidth(r.volume.coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
                }
            }
            Spacer(Modifier.height(10.dp))
            DText(r.soundName, 13.sp, p.muted)

            Spacer(Modifier.weight(1f))
            if (r.canSnooze) {
                BigButton("Snooze ${r.snoozeMinutes} min", Modifier.fillMaxWidth(), primary = false, onClick = onSnooze)
                Spacer(Modifier.height(14.dp))
            }
            BigButton(if (isTimer) "Stop" else "Dismiss", Modifier.fillMaxWidth(), onClick = onStop)
        }
    }
}
