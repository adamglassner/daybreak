package com.adamglassner.daybreak

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.adamglassner.daybreak.alarm.AlarmService
import com.adamglassner.daybreak.alarm.Scheduler
import com.adamglassner.daybreak.data.Store
import com.adamglassner.daybreak.ui.App
import com.adamglassner.daybreak.ui.DaybreakTheme
import com.adamglassner.daybreak.ui.RingScreen
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Dimension
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

private const val INSTANCE_PORT = 47821

fun main(args: Array<String>) {
    // Only one Daybreak at a time (two would ring every alarm twice).
    // A second launch asks the running one to show its window, then exits.
    val server = try {
        ServerSocket(INSTANCE_PORT, 4, InetAddress.getLoopbackAddress())
    } catch (e: Exception) {
        runCatching { Socket(InetAddress.getLoopbackAddress(), INSTANCE_PORT).use { it.getOutputStream().write(1) } }
        return
    }
    val showRequests = MutableStateFlow(0)
    thread(isDaemon = true, name = "daybreak-instance") {
        while (true) runCatching { server.accept().use { showRequests.value += 1 } }
    }

    Store.init(Platform.dataDir())
    val startInTray = "--tray" in args

    application {
        var visible by remember { mutableStateOf(!startInTray) }
        val trayState = rememberTrayState()
        val settings by Store.settings.collectAsState()
        val ringing by AlarmService.ringing.collectAsState()
        val requests by showRequests.collectAsState()
        val icon = painterResource("icon.png")

        LaunchedEffect(requests) { if (requests > 0) visible = true }
        LaunchedEffect(Unit) {
            Scheduler.loop { title, text -> trayState.sendNotification(Notification(title, text, Notification.Type.Info)) }
        }

        Tray(
            icon = icon,
            state = trayState,
            tooltip = "Daybreak",
            onAction = { visible = true },
            menu = {
                Item("Open Daybreak", onClick = { visible = true })
                Separator()
                Item("Quit Daybreak", onClick = {
                    AlarmService.stop()
                    exitApplication()
                })
            },
        )

        val mainState = rememberWindowState(size = DpSize(440.dp, 880.dp), position = WindowPosition(Alignment.Center))
        if (visible) {
            Window(
                onCloseRequest = { visible = false },
                state = mainState,
                title = "Daybreak",
                icon = icon,
            ) {
                LaunchedEffect(Unit) { window.minimumSize = Dimension(380, 640) }
                LaunchedEffect(requests) { window.toFront() }
                DaybreakTheme(settings.style, settings.mode) { App() }
            }
        }

        val r = ringing
        if (r != null) {
            val ringState = rememberWindowState(size = DpSize(420.dp, 720.dp), position = WindowPosition(Alignment.Center))
            Window(
                onCloseRequest = { AlarmService.stop() },
                state = ringState,
                title = r.title,
                icon = icon,
                alwaysOnTop = true,
            ) {
                LaunchedEffect(r.startedAt) {
                    window.toFront()
                    window.requestFocus()
                }
                DaybreakTheme(settings.style, settings.mode) { RingScreen(r, settings.use24h) }
            }
        }
    }
}
