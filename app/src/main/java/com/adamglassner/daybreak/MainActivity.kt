package com.adamglassner.daybreak

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.adamglassner.daybreak.alarm.Scheduler
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.ui.bevel
import com.adamglassner.daybreak.ui.AlarmEditScreen
import com.adamglassner.daybreak.ui.AlarmsScreen
import com.adamglassner.daybreak.ui.BedtimeScreen
import com.adamglassner.daybreak.ui.ClockScreen
import com.adamglassner.daybreak.ui.DText
import com.adamglassner.daybreak.ui.DaybreakTheme
import com.adamglassner.daybreak.ui.DbIcons
import com.adamglassner.daybreak.ui.Issue
import com.adamglassner.daybreak.ui.LocalDb
import com.adamglassner.daybreak.ui.SettingsScreen
import com.adamglassner.daybreak.ui.StationsScreen
import com.adamglassner.daybreak.ui.StopwatchScreen
import com.adamglassner.daybreak.ui.TimerScreen
import com.adamglassner.daybreak.ui.c

private sealed interface Overlay {
    data class EditAlarm(val id: Int?) : Overlay
    data object Bedtime : Overlay
    data object Settings : Overlay
    data object Stations : Overlay
}

class MainActivity : ComponentActivity() {

    private val startTab = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Scheduler.rescheduleAll(this)
        startTab.intValue = intent?.getIntExtra("tab", 0) ?: 0

        setContent {
            val settings by Store.settings.collectAsState()
            DaybreakTheme(settings.style, settings.mode) {
                val p = LocalDb.current.p
                DisposableEffect(p.dark) {
                    val style = if (p.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                App(startTab.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra("tab")) startTab.intValue = intent.getIntExtra("tab", 0)
    }
}

@Composable
private fun App(initialTab: Int) {
    val ctx = LocalContext.current
    val db = LocalDb.current
    val p = db.p
    val settings by Store.settings.collectAsState()
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    LaunchedEffect(settings.style) {
        if (settings.style != ClockStyle.RADIO) {
            com.adamglassner.daybreak.radio.Radio.stop()
            if (overlay == Overlay.Stations) overlay = null
        }
    }

    // Permission state, refreshed whenever the app comes back to the foreground.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val hasLocation = remember(refresh) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val notifOk = remember(refresh) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val exactOk = remember(refresh) { Scheduler.canScheduleExact(ctx) }
    val fullScreenOk = remember(refresh) {
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    LaunchedEffect(Unit) {
        val st = com.adamglassner.daybreak.ui.checkForUpdate()
        if (st is com.adamglassner.daybreak.update.Updater.Status.Available) {
            com.adamglassner.daybreak.ui.Notices.show("Daybreak ${st.release.version} is available. Update it in Settings.")
        }
    }
    var askedNotif by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!notifOk && !askedNotif && Build.VERSION.SDK_INT >= 33) {
            askedNotif = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val issues = buildList {
        if (!notifOk && Build.VERSION.SDK_INT >= 33) add(Issue("Allow notifications so alarms can ring", "Allow") {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        })
        if (!exactOk && Build.VERSION.SDK_INT >= 31) add(Issue("Allow alarms to ring on time", "Fix") {
            ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        })
        if (!fullScreenOk && Build.VERSION.SDK_INT >= 34) add(Issue("Allow full-screen alarms on the lock screen", "Fix") {
            ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
        })
    }
    val requestLocation = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        val o = overlay
        if (o != null) {
            BackHandler { overlay = null }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).imePadding()) {
                when (o) {
                    is Overlay.EditAlarm -> AlarmEditScreen(o.id, settings) { overlay = null }
                    Overlay.Bedtime -> BedtimeScreen(settings) { overlay = null }
                    Overlay.Stations -> StationsScreen { overlay = null }
                    Overlay.Settings -> SettingsScreen(settings, hasLocation, requestLocation) { overlay = null }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().then(
                        // The radio's woodgrain runs up behind the status bar.
                        if (tab == 0 && db.style == ClockStyle.RADIO) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    ),
                ) {
                    when (tab) {
                        0 -> ClockScreen(settings, hasLocation, requestLocation, { overlay = Overlay.Settings }, { tab = 1 }, onOpenStations = { overlay = Overlay.Stations }, showVolume = false)
                        1 -> AlarmsScreen(settings, issues, { overlay = Overlay.EditAlarm(it) }, { overlay = Overlay.Bedtime }, { overlay = Overlay.Settings })
                        2 -> StopwatchScreen()
                        else -> TimerScreen(settings)
                    }
                }
                NavBar(tab) { tab = it }
            }
        }
        if (db.style == ClockStyle.TERMINAL && p.dark) com.adamglassner.daybreak.ui.Scanlines(Modifier.fillMaxSize())
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("Clock", DbIcons.Clock),
    TabItem("Alarms", DbIcons.Alarm),
    TabItem("Stopwatch", DbIcons.Stopwatch),
    TabItem("Timer", DbIcons.Timer),
)

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    val db = LocalDb.current
    val p = db.p
    val barColor = when (db.style) {
        ClockStyle.STOCK -> if (p.dark) c(0xFF181A20) else c(0xFFFFFFFF)
        ClockStyle.COLORS -> p.surface
        ClockStyle.RETRO -> if (p.dark) c(0xFF16120E) else p.surface
        ClockStyle.RADIO -> if (p.dark) c(0xFF2A2420) else p.surface2
        ClockStyle.TERMINAL -> p.bg
        ClockStyle.CLASSIC -> p.surface
        ClockStyle.MODERN -> p.bg
    }
    Column(Modifier.fillMaxWidth().background(barColor)) {
        val lineColor = when (db.style) {
            ClockStyle.RADIO -> p.stripe
            ClockStyle.COLORS -> barColor
            else -> p.border
        }
        Box(Modifier.fillMaxWidth().height(if (db.style == ClockStyle.STOCK) 1.dp else 2.dp).background(lineColor))
        Row(
            Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEachIndexed { i, item ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).height(80.dp).clickable { onSelect(i) }
                        .semantics { role = Role.Tab; this.selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (db.style) {
                        ClockStyle.RADIO -> {
                            // Slide switch, like the clock radio's SLEEP / WAKE / HOUR / MINUTE switches.
                            Box(
                                Modifier.size(22.dp, 34.dp).clip(RoundedCornerShape(6.dp)).background(c(0xFF0A0606)).padding(3.dp),
                                contentAlignment = if (on) Alignment.TopCenter else Alignment.BottomCenter,
                            ) {
                                Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(if (on) c(0xFFE9DCC4) else c(0xFF8E8378)))
                            }
                        }
                        else -> {
                            val pill = when (db.style) {
                                ClockStyle.COLORS -> p.accent
                                ClockStyle.RETRO -> androidx.compose.ui.graphics.Color.Transparent
                                ClockStyle.CLASSIC -> p.surface2
                                else -> p.accentSoft
                            }
                            val tint = when {
                                !on -> p.muted
                                db.style == ClockStyle.COLORS -> p.onAccent
                                db.style == ClockStyle.STOCK -> p.link
                                else -> p.accent
                            }
                            val classic = db.style == ClockStyle.CLASSIC
                            var m = Modifier.size(60.dp, 32.dp).clip(RoundedCornerShape(if (classic) 0.dp else if (db.style == ClockStyle.RETRO) 6.dp else 16.dp))
                            if (on) m = m.background(pill)
                            if (classic) m = m.bevel(p.dark, sunken = on)
                            Box(m, contentAlignment = Alignment.Center) {
                                Icon(item.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    Box(Modifier.height(4.dp).width(1.dp))
                    DText(
                        if (db.f.caps) item.label.uppercase() else item.label,
                        if (db.f.caps) 11.sp else 12.sp,
                        if (on) p.text else p.muted,
                        if (on) FontWeight.Bold else FontWeight.Medium,
                        italic = db.style == ClockStyle.RADIO,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }
}
