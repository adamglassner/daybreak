package com.adamglassner.larksong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.larksong.Platform

private val DESKTOP_LIBS = listOf(
    CreditLine("Kotlin and Compose Multiplatform", "Apache License 2.0"),
    CreditLine("JLayer", "Plays MP3 sounds and radio streams · GNU LGPL"),
    CreditLine("JSON-java (org.json)", "Public domain"),
    CreditLine("Kotlin coroutines", "Apache License 2.0"),
)

/** The Credits dialog's contents. */
@Composable
fun ColumnScope.CreditsContent() {
    val db = LocalDb.current
    val p = db.p
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DText("Larksong for ${Platform.name}", 18.sp, p.text, FontWeight.SemiBold, family = db.f.display)
        DText("Version $APP_VERSION", 13.sp, p.muted)
        DText("Created by Adam Glassner · The Glassnerds", 14.sp, p.text, FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
    larksongCredits(DESKTOP_LIBS).forEach { g ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DText(g.title, 14.sp, p.text, FontWeight.SemiBold)
            g.lines.forEach { line ->
                Column {
                    DText(line.name, 14.sp, p.text)
                    if (line.detail.isNotBlank()) DText(line.detail, 12.sp, p.muted)
                }
            }
        }
    }
    DText("Thank you for using Larksong.", 12.sp, p.muted)
}

/** The What's new dialog's contents. */
@Composable
fun ColumnScope.ChangelogContent() {
    val p = LocalDb.current.p
    CHANGELOG.forEach { r ->
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row {
                DText("Version ${r.version}", 15.sp, p.text, FontWeight.SemiBold)
                if (r.version == APP_VERSION) DText("   This version", 12.sp, p.link, FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
            }
            DText(r.date, 12.sp, p.muted)
            r.changes.forEach { c -> DText("•  $c", 13.sp, p.text) }
        }
    }
}
