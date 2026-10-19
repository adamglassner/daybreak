package com.adamglassner.larksong.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglassner.larksong.alarm.Scheduler
import com.adamglassner.larksong.data.Store
import java.time.LocalDate

private fun restore(ctx: Context, text: String, replace: Boolean) {
    if (replace) Store.alarms.value.forEach { Scheduler.cancel(ctx, it.id) }
    val n = runCatching { Store.importBackup(text, replace) }.getOrElse { -1 }
    if (n < 0) {
        Notices.show("That backup couldn't be read")
        return
    }
    Scheduler.rescheduleAll(ctx)
    Notices.show(if (replace) "Restored $n alarm(s)" else "Added $n alarm(s)")
}

/** Back up alarms to a .json file, or restore them (the same format Larksong Classic uses). */
@Composable
fun BackupCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val p = LocalDb.current.p
    var pending by remember { mutableStateOf<String?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Store.exportBackup("Larksong $APP_VERSION for Android").toByteArray()) }
            }.isSuccess
            Notices.show(if (ok) "Alarms backed up" else "The backup couldn't be saved")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            if (text == null || Store.backupAlarmCount(text) < 0) Notices.show("That doesn't look like a Larksong backup")
            else pending = text
        }
    }

    DbCard(modifier.fillMaxWidth(), padding = 0.dp) {
        Column {
            SettingRow("Back up alarms", "Saves alarms, bedtime and world clocks to a file", onClick = {
                save.launch("larksong-alarms-${LocalDate.now()}.json")
            }) { LinkButton("Back up") { save.launch("larksong-alarms-${LocalDate.now()}.json") } }
            Divider()
            SettingRow("Restore alarms", "From a Larksong or Larksong Classic backup", onClick = {
                open.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            }) { LinkButton("Restore") { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) } }
        }
    }

    pending?.let { text ->
        val count = Store.backupAlarmCount(text)
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = p.surface,
            title = { DText("Restore $count alarm(s)?", 20.sp, weight = FontWeight.Bold) },
            text = {
                DText(
                    "Replace your alarms with the ones in this backup (also restores bedtime and world clocks), or add them to the alarms you have.",
                    15.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { pending = null; restore(ctx, text, replace = true) }) { DText("Replace", 16.sp, p.link, FontWeight.Bold) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { pending = null; restore(ctx, text, replace = false) }) { DText("Add to mine", 16.sp, p.link, FontWeight.Bold) }
                    TextButton(onClick = { pending = null }) { DText("Cancel", 16.sp, p.muted, FontWeight.Bold) }
                }
            },
        )
    }
}
