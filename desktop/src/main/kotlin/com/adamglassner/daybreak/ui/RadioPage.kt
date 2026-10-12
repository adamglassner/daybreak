package com.adamglassner.daybreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** World radio (Clock radio style only): the tuner beside the station browser. */
@Composable
fun RadioPage() {
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp)) {
        PageHeader("Radio", "Live stations from around the world · Radio Browser directory")
        BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 24.dp)) {
            if (maxWidth > 860.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) { TunerPanel(null, showVolume = true, modifier = Modifier) }
                    FCard(Modifier.weight(1.1f).fillMaxSize(), padding = 0.dp) {
                        Box(Modifier.fillMaxSize().padding(top = 12.dp)) { StationsScreen(showHeader = false) {} }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TunerPanel(null, showVolume = true, modifier = Modifier)
                    FCard(Modifier.fillMaxWidth().weight(1f), padding = 0.dp) {
                        Box(Modifier.fillMaxSize().padding(top = 12.dp)) { StationsScreen(showHeader = false) {} }
                    }
                }
            }
        }
    }
}

