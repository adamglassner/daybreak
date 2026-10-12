package com.adamglassner.daybreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.daybreak.Platform
import com.adamglassner.daybreak.data.ClockStyle
import com.adamglassner.daybreak.data.Settings
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.data.ThemeMode
import com.adamglassner.daybreak.update.Updater
import kotlinx.coroutines.launch
import java.io.File

/** The running app's version (from the build). */
val APP_VERSION: String by lazy {
    runCatching {
        Thread.currentThread().contextClassLoader.getResourceAsStream("daybreak-version.txt")?.bufferedReader()?.use { it.readText().trim() }
    }.getOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("$") } ?: "dev"
}

suspend fun checkForUpdate() = Updater.check(APP_VERSION, "windows-v", ".exe")

/** Runs a downloaded installer and closes Daybreak so it can be replaced. */
fun installUpdate(file: File) {
    runCatching { ProcessBuilder(file.absolutePath).start() }
        .onSuccess { DesktopApp.exit() }
        .onFailure { Notices.show("Couldn't start the installer") }
}

object DesktopApp {
    var exit: () -> Unit = {}
}

@Composable
fun SettingsPage(settings: Settings) {
    val p = LocalDb.current.p
    val scope = rememberCoroutineScope()
    val update by Updater.status.collectAsState()
    var addingCity by remember { mutableStateOf(false) }
    var autostart by remember { mutableStateOf(Platform.isAutostartOn()) }

    ScrollPage(maxWidth = 1000.dp) {
        PageHeader("Settings")

        SectionTitle("Appearance")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsCard("App style", "Colors, fonts and the clock face. Radio adds a world radio tuner.", DbIcons.Clock, below = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClockStyle.entries.forEach { s ->
                        StyleCard(s, settings.style == s, Modifier.weight(1f)) { Store.updateSettings { it.copy(style = s) } }
                    }
                }
            })
            SettingsCard("App theme", "Light, dark, or follow Windows", DbIcons.Sun) {
                val modes = listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "Use system setting")
                ComboBox(modes.first { it.first == settings.mode }.second, modes.map { it.second }) { i ->
                    Store.updateSettings { it.copy(mode = modes[i].first) }
                }
            }
        }

        SectionTitle("Time")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsCard("24-hour clock", if (settings.use24h) "Shows 18:30" else "Shows 6:30 PM", DbIcons.Clock) {
                FToggle(settings.use24h, { v -> Store.updateSettings { it.copy(use24h = v) } }, "24-hour clock")
            }
            SettingsCard("Show seconds", null, DbIcons.Stopwatch) {
                FToggle(settings.showSeconds, { v -> Store.updateSettings { it.copy(showSeconds = v) } }, "Show seconds")
            }
        }

        SectionTitle("Weather")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsCard("Temperature units", null, DbIcons.WxPartly) {
                ComboBox(if (settings.celsius) "Celsius (°C)" else "Fahrenheit (°F)", listOf("Fahrenheit (°F)", "Celsius (°C)")) { i ->
                    Store.updateSettings { it.copy(celsius = i == 1) }
                }
            }
            SettingsCard("Use my approximate location", "Based on your internet connection", DbIcons.Pin) {
                FToggle(settings.useGps, { v -> Store.updateSettings { it.copy(useGps = v) } }, "Use my approximate location")
            }
            SettingsCard("Saved cities", if (settings.cities.isEmpty()) "None yet" else "${settings.cities.size} saved", DbIcons.Pin, below = {
                settings.cities.forEach { city ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            DText(city.name, 14.sp, p.text)
                            DText(city.region, 12.sp, p.muted)
                        }
                        FIconButton(DbIcons.Close, "Remove ${city.name}") { Store.updateSettings { it.copy(cities = it.cities - city) } }
                    }
                }
                Spacer(Modifier.height(4.dp))
                FButton("Add a city", icon = DbIcons.Plus) { addingCity = true }
            })
        }

        SectionTitle("Windows")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsCard(
                "Start with Windows",
                if (Platform.canAutostart) "Opens quietly in the notification area when you sign in, so alarms are always ready"
                else "Available in the installed app",
                DbIcons.Bell,
            ) {
                FToggle(autostart, { v -> Platform.setAutostart(v); autostart = Platform.isAutostartOn() }, "Start with Windows", enabled = Platform.canAutostart)
            }
            SettingsCard(
                "Closing the window",
                "Daybreak keeps running in the notification area so alarms still ring. To exit, right-click its icon and choose Quit. A sleeping PC can't ring an alarm.",
                DbIcons.Close,
            )
        }

        SectionTitle("Updates")
        val dl = update as? Updater.Status.Downloading
        val progressRow: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? =
            if (dl != null) { { ProgressBar(dl.progress) } } else null
        SettingsCard(
            "Daybreak $APP_VERSION",
            when (val s = update) {
                Updater.Status.Idle -> "Check GitHub for a newer version"
                Updater.Status.Checking -> "Checking for updates…"
                Updater.Status.UpToDate -> "You're up to date"
                is Updater.Status.Available -> "Version ${s.release.version} is available"
                is Updater.Status.Downloading -> "Downloading version ${s.release.version}… ${(s.progress * 100).toInt()}%"
                is Updater.Status.Ready -> "Version ${s.release.version} is ready to install. Daybreak will close and reopen after setup."
                is Updater.Status.Failed -> s.message
            },
            DbIcons.Download,
            below = progressRow,
        ) {
            when (val s = update) {
                is Updater.Status.Available -> FButton("Download and install", accent = true) {
                    scope.launch {
                        val dir = File(System.getProperty("java.io.tmpdir"), "Daybreak-update")
                        Updater.download(s.release, dir)?.let { installUpdate(it) }
                    }
                }
                is Updater.Status.Ready -> FButton("Install now", accent = true) { installUpdate(s.file) }
                Updater.Status.Checking, is Updater.Status.Downloading -> {}
                else -> FButton("Check for updates") { scope.launch { checkForUpdate() } }
            }
        }

        SectionTitle("About")
        SettingsCard(
            "Daybreak for Windows",
            "Version $APP_VERSION · Weather by Open-Meteo · Radio by radio-browser.info · Keyboard: Ctrl+1–5 switch pages, Ctrl+N new alarm, Ctrl+, settings, Esc closes dialogs",
            DbIcons.Alarm,
        )
        Spacer(Modifier.width(1.dp))
    }

    if (addingCity) {
        CitySearchDialog(onDismiss = { addingCity = false }) { city ->
            addingCity = false
            Store.updateSettings { s -> if (s.cities.any { it.name == city.name && it.region == city.region }) s else s.copy(cities = s.cities + city) }
        }
    }
}
