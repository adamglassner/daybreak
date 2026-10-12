package com.adamglassner.daybreak.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons drawn from SVG path data on a 24×24 grid (tinted by Icon). */
object DbIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun icon(name: String, vararg paths: String, width: Float = 2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Clock = icon("clock", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val Alarm = icon("alarm", circle(12f, 13f, 8f), "M12 9v4l2 2M5 3L2 6M22 6l-3-3")
    val Stopwatch = icon("stopwatch", circle(12f, 14f, 8f), "M12 14V10M10 2h4M18 6l1.5-1.5")
    val Timer = icon("timer", "M6 2h12M6 22h12M8 2c0 6 8 6 8 10s-8 4-8 10M16 2c0 6-8 6-8 10s8 4 8 10")
    val Moon = icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Sun = icon("sun", circle(12f, 12f, 4f), "M12 2v2M12 20v2M4 12H2M22 12h-2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5")
    val Music = icon("music", "M9 18V5l11-2v13", circle(6f, 18f, 3f), circle(17f, 16f, 3f))
    val Settings = icon("settings", "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Pin = icon("pin", "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z", circle(12f, 9.5f, 2.5f))
    val Ramp = icon("ramp", "M3 18h3l12-12M14 6h4v4")
    val Plus = icon("plus", "M12 5v14M5 12h14", width = 2.4f)
    val Back = icon("back", "M15 6l-6 6 6 6", width = 2.2f)
    val Chevron = icon("chevron", "M9 6l6 6-6 6", width = 2.2f)
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Trash = icon("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
    val Play = icon("play", "M7 4l12 8-12 8z")
    val Radio = icon("radio", "M3 9h18v11H3z", "M7 9l10-5", circle(15.5f, 14.5f, 2.5f), "M6.5 12.5h4M6.5 16.5h4")
    val Up = icon("up", "M6 15l6-6 6 6", width = 2.2f)
    val Down = icon("down", "M6 9l6 6 6-6", width = 2.2f)
    val Menu = icon("menu", "M4 6h16M4 12h16M4 18h16")
    val Edit = icon("edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val Download = icon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Bell = icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 21h4")

    private const val CLOUD_HI = "M7 16h10a4 4 0 0 0 0-8 6 6 0 0 0-11.6 1.8A3.2 3.2 0 0 0 7 16z"
    val WxClear = Sun
    val WxPartly = icon(
        "partly", circle(9f, 9f, 3.5f), "M9 2v1.5M3.5 9H2M4.5 4.5l1 1M13.5 4.5l-1 1",
        "M10 20h8a3.5 3.5 0 0 0 0-7 5 5 0 0 0-9.6 1.4A3 3 0 0 0 10 20z",
    )
    val WxCloud = icon("cloud", "M7 19h10a4 4 0 0 0 0-8 6 6 0 0 0-11.6 1.8A3.2 3.2 0 0 0 7 19z")
    val WxRain = icon("rain", CLOUD_HI, "M8 20l1-2M12 20l1-2M16 20l1-2")
    val WxSnow = icon("snow", CLOUD_HI, "M8 19.5v.01M12 21v.01M16 19.5v.01", width = 2.6f)
    val WxStorm = icon("storm", CLOUD_HI, "M12 16l-2 3.5h4l-2 3.5")
    val WxFog = icon("fog", "M4 9h16M4 13h16M6 17h12")
}
