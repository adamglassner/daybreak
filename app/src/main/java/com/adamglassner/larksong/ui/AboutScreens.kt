package com.adamglassner.larksong.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ANDROID_LIBS = listOf(
    CreditLine("Kotlin and Jetpack Compose", "Apache License 2.0"),
    CreditLine("AndroidX Media3 (ExoPlayer)", "Plays radio streams · Apache License 2.0"),
    CreditLine("Kotlin coroutines", "Apache License 2.0"),
)

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val db = LocalDb.current
    val p = db.p
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        BackHeader(if (db.f.caps) "CREDITS" else "Credits", onBack)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DbCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DText("Larksong", 26.sp, p.text, FontWeight.Bold, family = db.f.display, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
                    DText("Version $APP_VERSION", 14.sp, p.muted, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
                    DText("Created by Adam Glassner · The Glassnerds", 15.sp, p.text, FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), align = TextAlign.Center)
                }
            }
            larksongCredits(ANDROID_LIBS).forEach { g ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(g.title)
                    DbCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            g.lines.forEach { line ->
                                Column {
                                    DText(line.name, 16.sp, p.text, FontWeight.Bold)
                                    if (line.detail.isNotBlank()) DText(line.detail, 13.sp, p.muted)
                                }
                            }
                        }
                    }
                }
            }
            DText("Thank you for using Larksong.", 13.sp, p.muted, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        }
    }
}

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val db = LocalDb.current
    val p = db.p
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        BackHeader(if (db.f.caps) "WHAT'S NEW" else "What's new", onBack)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CHANGELOG.forEach { r ->
                DbCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            DText("Version ${r.version}", 17.sp, p.text, FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (r.version == APP_VERSION) DText("This version", 13.sp, p.link, FontWeight.Bold)
                        }
                        DText(r.date, 12.sp, p.muted)
                        r.changes.forEach { c -> DText("•  $c", 14.sp, p.text) }
                    }
                }
            }
        }
    }
}
