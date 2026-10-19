package com.adamglassner.daybreak.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.sleep.SleepSound
import com.adamglassner.daybreak.sleep.SleepSounds

/** Sleep sounds on the Clock page: pick a sound, a timer and a volume. Works in every style. */
@Composable
fun SleepSoundsCard(modifier: Modifier = Modifier) {
    val db = LocalDb.current
    val p = db.p
    val st by SleepSounds.state.collectAsState()
    val settings by Store.settings.collectAsState()
    val now by rememberNow(15_000L)
    val onPanel = db.style == ClockStyle.RADIO && p.dark
    val main = if (onPanel) p.panelText else p.text
    val sub = if (onPanel) p.panelMuted else p.muted

    val status = when {
        st.playing && st.endsAt > 0 -> {
            val left = ((st.endsAt - now) / 60_000L).coerceAtLeast(0) + 1
            "${st.sound.label} · turns off in ${SleepSounds.timerLabel(left.toInt())}"
        }
        st.playing -> "${st.sound.label} · playing until you stop it"
        else -> "Tap a sound to play it · timer ${SleepSounds.timerLabel(settings.sleepMinutes).lowercase()}"
    }

    FCard(modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DbIcons.Moon, null, tint = main, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    DText(if (db.f.caps) "SLEEP SOUNDS" else "Sleep sounds", 16.sp, main, FontWeight.Bold)
                    DText(status, 13.sp, sub, maxLines = 2)
                }
                LinkButton(if (st.playing) "Stop" else "Play", icon = if (st.playing) null else DbIcons.Play) { SleepSounds.toggle() }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SleepSound.entries.forEach { s ->
                    Chip(s.label, st.sound == s && st.playing) {
                        if (st.playing && st.sound == s) SleepSounds.stop() else SleepSounds.play(s)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Timer: " + SleepSounds.timerLabel(settings.sleepMinutes).lowercase(), false) { SleepSounds.cycleTimer() }
                Slider(
                    value = settings.sleepVolume,
                    onValueChange = { SleepSounds.setVolume(it) },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Sleep sounds volume" },
                    colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.border),
                )
            }
        }
    }
}
