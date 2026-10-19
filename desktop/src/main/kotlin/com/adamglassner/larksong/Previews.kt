package com.adamglassner.larksong

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.adamglassner.larksong.data.ClockStyle
import com.adamglassner.larksong.data.Store
import com.adamglassner.larksong.data.ThemeMode
import com.adamglassner.larksong.ui.ClockScreen
import com.adamglassner.larksong.ui.LarksongTheme
import com.adamglassner.larksong.ui.LocalDb
import com.adamglassner.larksong.ui.skyBackground
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

/**
 * Renders the phone screens (the desktop app keeps a copy of them) to PNG files at phone sizes,
 * so layouts can be checked without a phone. Run with: gradle -p desktop renderPreviews
 */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "previews" }).apply { mkdirs() }
    Store.init(Files.createTempDirectory("larksong-preview").toFile())

    fun shot(name: String, style: ClockStyle, mode: ThemeMode, widthDp: Int, heightDp: Int, content: @Composable () -> Unit) {
        Store.updateSettings { it.copy(style = style, mode = mode) }
        val d = 2f
        val scene = ImageComposeScene((widthDp * d).toInt(), (heightDp * d).toInt(), Density(d)) {
            LarksongTheme(style, mode) {
                Box(Modifier.fillMaxSize().then(skyBackground(LocalDb.current.p))) { content() }
            }
        }
        var t = 0L
        repeat(5) { scene.render(t); t += 200_000_000L }
        val img = scene.render(t)
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("rendered $name")
    }

    for (w in listOf(320, 360, 412)) {
        shot("radio-dark-$w", ClockStyle.RADIO, ThemeMode.DARK, w, 1950) {
            ClockScreen(Store.settings.value, false, {}, {}, {})
        }
    }
    shot("radio-light-360", ClockStyle.RADIO, ThemeMode.LIGHT, 360, 1950) {
        ClockScreen(Store.settings.value, false, {}, {}, {})
    }
    exitProcess(0)
}
