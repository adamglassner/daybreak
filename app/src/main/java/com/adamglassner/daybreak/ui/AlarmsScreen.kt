package com.adamglassner.daybreak.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.adamglassner.daybreak.alarm.AlarmService
import com.adamglassner.daybreak.alarm.Scheduler
import com.adamglassner.daybreak.data.Alarm
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.Settings
import com.adamglassner.daybreak.data.Store

data class Issue(val text: String, val action: String, val fix: () -> Unit)

fun untilText(ms: Long): String {
    val mins = ((ms - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
    val h = mins / 60
    val m = mins % 60
    return when {
        h >= 24 -> "${h / 24} d ${h % 24} hr"
        h > 0 -> "$h hr $m min"
        else -> "$m min"
    }
}

fun saveAndSchedule(ctx: Context, alarm: Alarm, announce: Boolean) {
    val a = alarm.copy(skipUntil = 0L) // editing or turning an alarm on clears an early dismissal
    Store.upsertAlarm(a)
    Scheduler.cancelSnooze(ctx, a.id)
    Scheduler.schedule(ctx, a)
    if (announce && a.enabled) {
        Toast.makeText(ctx, "Alarm set for ${untilText(Scheduler.nextTrigger(a))} from now", Toast.LENGTH_SHORT).show()
    }
}

// ---------- list ----------

@Composable
fun AlarmsScreen(
    settings: Settings,
    issues: List<Issue>,
    onEdit: (Int?) -> Unit,
    onBedtime: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val db = LocalDb.current
    val p = db.p
    val ctx = LocalContext.current
    val alarms by Store.alarms.collectAsState()
    val now by rememberNow(30_000L)
    val next = remember(alarms, now) { nextAlarmMillis() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DText(if (db.f.caps) "ALARMS" else "Alarms", 28.sp, weight = FontWeight.Bold, family = db.f.display, italic = db.style == ClockStyle.RADIO)
                    DText(if (next != null) "Next alarm in ${untilText(next)}" else "No alarms on", 14.sp, p.muted)
                }
                IconCircle(DbIcons.Settings, "Settings", onOpenSettings)
            }

            issues.forEach { issue ->
                DbCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), color = p.accentSoft, onClick = issue.fix) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(DbIcons.Bell, null, tint = p.text, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        DText(issue.text, 14.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        DText(issue.action, 14.sp, p.link, FontWeight.Bold)
                    }
                }
            }

            val b = settings.bedtime
            DbCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp), color = p.night, onClick = onBedtime) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(p.nightSoft), contentAlignment = Alignment.Center) {
                        Icon(DbIcons.Moon, null, tint = p.text, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        DText(
                            if (b.enabled) "Bedtime ${formatTime(b.bedHour, b.bedMinute, settings.use24h)}" else "Bedtime reminder",
                            16.sp, if (db.style == ClockStyle.RADIO && p.dark) p.panelText else p.text, FontWeight.Bold,
                        )
                        DText(
                            if (b.enabled) "Reminder ${b.remindMinutes} min before · ${sleepLength(b.bedHour * 60 + b.bedMinute, b.wakeHour * 60 + b.wakeMinute)} sleep"
                            else "Off · tap to set one up",
                            13.sp, p.muted,
                        )
                    }
                    Icon(DbIcons.Chevron, null, tint = p.muted, modifier = Modifier.size(18.dp))
                }
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (alarms.isEmpty()) {
                    DText("No alarms yet. Tap + to add one.", 15.sp, p.muted, modifier = Modifier.padding(8.dp))
                }
                alarms.forEach { a ->
                    AlarmRow(
                        a, settings.use24h, now,
                        onClick = { onEdit(a.id) },
                        onDismiss = {
                            val when_ = ringText(Scheduler.nextTrigger(a), settings.use24h)
                            val u = Scheduler.dismissNext(ctx, a)
                            Toast.makeText(
                                ctx,
                                if (u.enabled) "Dismissed $when_. Next: ${ringText(Scheduler.nextTrigger(u), settings.use24h)}" else "Dismissed $when_",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onUndo = { Scheduler.undoDismiss(ctx, a) },
                    ) { on -> saveAndSchedule(ctx, a.copy(enabled = on), on) }
                }
            }
        }

        val shape = RoundedCornerShape(if (db.style == ClockStyle.STOCK || db.style == ClockStyle.COLORS) 20.dp else 10.dp)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(22.dp).size(64.dp).clip(shape).background(p.accent)
                .clickable { onEdit(null) }.semantics { contentDescription = "Add alarm" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(DbIcons.Plus, null, tint = p.onAccent, modifier = Modifier.size(28.dp))
        }
    }
}

fun sleepLength(bed: Int, wake: Int): String {
    val span = (wake - bed + 1440) % 1440
    return if (span % 60 == 0) "${span / 60} h" else "${span / 60} h ${span % 60} m"
}

/** "Tue 6:00 AM" for a ring time. */
fun ringText(at: Long, use24: Boolean): String {
    val t = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
    val day = t.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
    return "$day ${formatTime(t.hour, t.minute, use24)}"
}

@Composable
private fun AlarmRow(
    a: Alarm,
    use24: Boolean,
    now: Long,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val db = LocalDb.current
    val p = db.p
    val onPanel = db.style == ClockStyle.RADIO && p.dark
    val main = if (onPanel) p.panelText else p.text
    val sub = if (onPanel) p.panelMuted else p.muted
    DbCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (a.label.isNotBlank()) DText(a.label, 13.sp, sub, FontWeight.Medium)
                Row(verticalAlignment = Alignment.Bottom) {
                    val timeColor = when {
                        !a.enabled -> sub
                        db.style == ClockStyle.RADIO -> p.led
                        db.style == ClockStyle.RETRO -> p.accent
                        else -> main
                    }
                    DText(
                        clockDigits(a.hour, a.minute, use24), if (db.style == ClockStyle.RETRO) 48.sp else 40.sp, timeColor,
                        if (db.style == ClockStyle.RETRO) FontWeight.Normal else FontWeight.SemiBold, family = db.f.display,
                        italic = db.style == ClockStyle.RADIO,
                    )
                    if (!use24) DText(" " + ampm(a.hour), 17.sp, sub, FontWeight.Medium, modifier = Modifier.padding(bottom = 7.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DText(describeDays(a.days), 13.sp, sub)
                    if (a.gradual) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(DbIcons.Ramp, null, tint = if (a.enabled) p.link else sub, modifier = Modifier.size(14.dp))
                        DText("Gradual", 13.sp, if (a.enabled) p.link else sub)
                    }
                    DText(a.soundName, 13.sp, sub, maxLines = 1)
                }
                if (a.enabled) {
                    val skipping = a.skipUntil > now
                    val next = Scheduler.nextTrigger(a)
                    if (skipping) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DText("Dismissed ${ringText(a.skipUntil, use24)} · next ${ringText(next, use24)}", 13.sp, sub, modifier = Modifier.weight(1f, fill = false))
                            LinkButton("Undo", onClick = onUndo)
                        }
                    } else if (next - now < 24 * 60 * 60 * 1000L) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DText("Up early? ", 13.sp, sub)
                            LinkButton("Dismiss ${ringText(next, use24)}", onClick = onDismiss)
                        }
                    }
                }
            }
            DbSwitch(a.enabled, onToggle, "Alarm ${formatTime(a.hour, a.minute, use24)}")
        }
    }
}

// ---------- sound picking ----------

fun displayName(ctx: Context, uri: Uri): String =
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "Custom sound"

/** A sound row with a menu: default, a system sound, or an audio file from the phone. */
@Composable
fun SoundPicker(title: String, name: String, allowRadio: Boolean = false, onPicked: (String?, String) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalDb.current.p
    var pickingRadio by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onPicked(uri.toString(), displayName(ctx, uri))
        }
    }
    val systemLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            if (uri != null) {
                val n = runCatching { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) }.getOrNull() ?: "System sound"
                onPicked(uri.toString(), n)
            } else {
                onPicked(null, "Default alarm")
            }
        }
    }

    SettingRow(title, name, onClick = { open = true }) {
        Box {
            Row(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(p.accentSoft)
                    .clickable { open = true }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(DbIcons.Music, null, tint = p.link, modifier = Modifier.size(16.dp))
                DText("Choose", 14.sp, p.link, FontWeight.Bold)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.surface) {
                DropdownMenuItem(text = { DText("Default alarm sound", 15.sp) }, onClick = {
                    open = false; onPicked(null, "Default alarm")
                })
                if (allowRadio) {
                    DropdownMenuItem(text = { DText("Wake to the radio…", 15.sp) }, onClick = { open = false; pickingRadio = true })
                }
                DropdownMenuItem(text = { DText("System sounds…", 15.sp) }, onClick = {
                    open = false
                    systemLauncher.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_NOTIFICATION)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title),
                    )
                })
                DropdownMenuItem(text = { DText("A song or sound from your phone…", 15.sp) }, onClick = {
                    open = false; fileLauncher.launch(arrayOf("audio/*"))
                })
            }
        }
    }

    if (pickingRadio) {
        RadioStationDialog(onDismiss = { pickingRadio = false }) { st ->
            pickingRadio = false
            onPicked(com.adamglassner.daybreak.alarm.RadioAlarm.soundUri(st), com.adamglassner.daybreak.alarm.RadioAlarm.soundName(st))
        }
    }
}

// ---------- edit ----------

private val RAMPS = listOf(30 to "30 s", 60 to "1 min", 180 to "3 min", 300 to "5 min")
private val SNOOZES = listOf(5, 10, 15, 20)

@Composable
fun AlarmEditScreen(alarmId: Int?, settings: Settings, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val db = LocalDb.current
    val p = db.p
    val existing = alarmId?.let { Store.alarm(it) }
    var a by remember {
        mutableStateOf(existing ?: Alarm(id = Store.nextAlarmId(), hour = 7, minute = 0, days = setOf(1, 2, 3, 4, 5)))
    }
    var picking by remember { mutableStateOf(false) }
    BackHandler(onBack = onDone)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LinkButton("Cancel", color = p.muted, onClick = onDone)
            DText(if (existing == null) "New alarm" else "Edit alarm", 17.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center)
            LinkButton("Save") {
                saveAndSchedule(ctx, a.copy(enabled = true), true)
                onDone()
            }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp).clickable { picking = true }
                    .semantics { contentDescription = "Alarm time ${formatTime(a.hour, a.minute, settings.use24h)}. Tap to change." },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (db.style == ClockStyle.RADIO) {
                    LedWindow { SevenSeg(clockDigits(a.hour, a.minute, settings.use24h).padStart(5), 72.dp, p.led, p.ledOff) }
                } else {
                    DText(clockDigits(a.hour, a.minute, settings.use24h), 76.sp, weight = if (db.style == ClockStyle.RETRO) FontWeight.Normal else FontWeight.SemiBold, family = db.f.display)
                }
                if (!settings.use24h) DText(" " + ampm(a.hour), 22.sp, p.accent, FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
            }
            DText("Tap the time to change it", 13.sp, p.muted, modifier = Modifier.fillMaxWidth(), align = androidx.compose.ui.text.style.TextAlign.Center)

            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Repeat")
                DayChips(a.days) { d -> a = a.copy(days = if (d in a.days) a.days - d else a.days + d) }
                DText(describeDays(a.days), 13.sp, p.muted)
            }

            DbCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), padding = 0.dp) {
                Column {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        OutlinedTextField(
                            value = a.label,
                            onValueChange = { a = a.copy(label = it.take(40)) },
                            label = { DText("Label", 14.sp, p.muted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = editText(), unfocusedTextColor = editText(),
                                focusedBorderColor = p.accent, unfocusedBorderColor = p.border, cursorColor = p.accent,
                            ),
                        )
                    }
                    Divider()
                    SoundPicker("Sound", a.soundName, allowRadio = true) { uri, name -> a = a.copy(soundUri = uri, soundName = name) }
                    Divider()
                    SettingRow(
                        "Gradually increase volume",
                        if (a.gradual) "Starts quiet, full volume after ${RAMPS.firstOrNull { it.first == a.rampSeconds }?.second ?: "${a.rampSeconds} s"}" else "Rings at full volume",
                    ) { DbSwitch(a.gradual, { a = a.copy(gradual = it) }, "Gradually increase volume") }
                    if (a.gradual) {
                        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RAMPS.forEach { (sec, label) -> Chip(label, a.rampSeconds == sec, Modifier.weight(1f)) { a = a.copy(rampSeconds = sec) } }
                        }
                    }
                    Divider()
                    SettingRow("Vibrate") { DbSwitch(a.vibrate, { a = a.copy(vibrate = it) }, "Vibrate") }
                    Divider()
                    SettingRow("Snooze length", "${a.snoozeMinutes} minutes")
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SNOOZES.forEach { m -> Chip("$m min", a.snoozeMinutes == m, Modifier.weight(1f)) { a = a.copy(snoozeMinutes = m) } }
                    }
                }
            }

            BigButton("Preview alarm", Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), primary = false) {
                val i = Intent(ctx, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_RING
                    putExtra(Scheduler.EXTRA_KIND, Scheduler.KIND_PREVIEW)
                    putExtra(Scheduler.EXTRA_ID, a.id)
                    putExtra(AlarmService.EXTRA_SOUND, a.soundUri)
                    putExtra(AlarmService.EXTRA_SOUND_NAME, a.soundName)
                    putExtra(AlarmService.EXTRA_GRADUAL, a.gradual)
                    putExtra(AlarmService.EXTRA_RAMP, a.rampSeconds)
                    putExtra(AlarmService.EXTRA_VIBRATE, a.vibrate)
                    putExtra(AlarmService.EXTRA_LABEL, a.label)
                }
                ContextCompat.startForegroundService(ctx, i)
            }

            if (existing != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clickable {
                        Scheduler.cancel(ctx, a.id)
                        Store.deleteAlarm(a.id)
                        onDone()
                    }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DbIcons.Trash, null, tint = p.link, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    DText("Delete alarm", 15.sp, p.link, FontWeight.Bold)
                }
            }
        }
    }

    if (picking) {
        TimePickDialog(a.hour, a.minute, settings.use24h, onDismiss = { picking = false }) { h, m ->
            a = a.copy(hour = h, minute = m); picking = false
        }
    }
}

@Composable
fun editText() = LocalDb.current.let { if (it.style == ClockStyle.RADIO && it.p.dark) it.p.panelText else it.p.text }

// ---------- bedtime ----------

private val REMINDS = listOf(15, 30, 45, 60)

@Composable
fun BedtimeScreen(settings: Settings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = LocalDb.current
    val p = db.p
    var b by remember { mutableStateOf(settings.bedtime) }
    var pickBed by remember { mutableStateOf(false) }
    var pickWake by remember { mutableStateOf(false) }

    fun save() {
        var nb = b
        if (nb.linkAlarm) {
            // Wake days follow bed days, shifted a day when sleep crosses midnight.
            val crosses = nb.wakeHour * 60 + nb.wakeMinute <= nb.bedHour * 60 + nb.bedMinute
            val wakeDays = if (crosses) nb.days.map { it % 7 + 1 }.toSet() else nb.days
            val id = if (nb.linkedAlarmId != 0 && Store.alarm(nb.linkedAlarmId) != null) nb.linkedAlarmId else Store.nextAlarmId()
            val base = Store.alarm(id) ?: Alarm(id = id, label = "Wake up")
            saveAndSchedule(ctx, base.copy(hour = nb.wakeHour, minute = nb.wakeMinute, days = wakeDays, enabled = nb.enabled), false)
            nb = nb.copy(linkedAlarmId = id)
        }
        Store.updateSettings { it.copy(bedtime = nb) }
        Scheduler.scheduleBedtime(ctx)
    }

    BackHandler { save(); onBack() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        BackHeader(if (db.f.caps) "BEDTIME" else "Bedtime", onBack = { save(); onBack() })

        DbCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), color = p.night) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    SleepRing(b.bedHour * 60 + b.bedMinute, b.wakeHour * 60 + b.wakeMinute, 200.dp, p.nightSoft, p.accent)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DText(sleepLength(b.bedHour * 60 + b.bedMinute, b.wakeHour * 60 + b.wakeMinute), 28.sp, nightText(), FontWeight.Bold, family = db.f.display)
                        DText("of sleep", 14.sp, p.muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeBox("Bedtime", DbIcons.Moon, formatTime(b.bedHour, b.bedMinute, settings.use24h), Modifier.weight(1f)) { pickBed = true }
                    TimeBox("Wake up", DbIcons.Sun, formatTime(b.wakeHour, b.wakeMinute, settings.use24h), Modifier.weight(1f)) { pickWake = true }
                }
            }
        }

        DbCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), padding = 0.dp) {
            Column {
                SettingRow("Bedtime reminder", "A notification to start winding down") {
                    DbSwitch(b.enabled, { b = b.copy(enabled = it) }, "Bedtime reminder")
                }
                Divider()
                SettingRow("Remind me", "${b.remindMinutes} min before bedtime")
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    REMINDS.forEach { m -> Chip("$m min", b.remindMinutes == m, Modifier.weight(1f)) { b = b.copy(remindMinutes = m) } }
                }
                Divider()
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DText("Nights", 16.sp, editText())
                    DayChips(b.days) { d -> b = b.copy(days = if (d in b.days) b.days - d else b.days + d) }
                }
                Divider()
                SettingRow("Set a matching wake-up alarm", "Creates a \"Wake up\" alarm at ${formatTime(b.wakeHour, b.wakeMinute, settings.use24h)}") {
                    DbSwitch(b.linkAlarm, { b = b.copy(linkAlarm = it) }, "Set a matching wake-up alarm")
                }
            }
        }
        BigButton("Save", Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            save()
            Toast.makeText(ctx, if (b.enabled) "Bedtime reminder saved" else "Bedtime saved (reminder off)", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    if (pickBed) TimePickDialog(b.bedHour, b.bedMinute, settings.use24h, { pickBed = false }) { h, m -> b = b.copy(bedHour = h, bedMinute = m); pickBed = false }
    if (pickWake) TimePickDialog(b.wakeHour, b.wakeMinute, settings.use24h, { pickWake = false }) { h, m -> b = b.copy(wakeHour = h, wakeMinute = m); pickWake = false }
}

@Composable
private fun nightText() = LocalDb.current.let { if (it.style == ClockStyle.RADIO && it.p.dark) it.p.panelText else it.p.text }

@Composable
private fun TimeBox(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalDb.current.p
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(p.nightSoft).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = p.muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            DText(label, 13.sp, p.muted)
        }
        DText(value, 24.sp, nightText(), FontWeight.Bold)
    }
}
