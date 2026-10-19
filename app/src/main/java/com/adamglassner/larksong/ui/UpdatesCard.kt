package com.adamglassner.larksong.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.adamglassner.larksong.BuildConfig
import com.adamglassner.larksong.update.Updater
import kotlinx.coroutines.launch
import java.io.File

val APP_VERSION: String = BuildConfig.VERSION_NAME

suspend fun checkForUpdate() = Updater.check(APP_VERSION, "android-v", ".apk")

fun updatesDir(ctx: Context) = File(ctx.cacheDir, "updates")

/** Opens Android's installer for a downloaded APK, asking for "install unknown apps" first if needed. */
fun installApk(ctx: Context, file: File) {
    if (!ctx.packageManager.canRequestPackageInstalls()) {
        Notices.show("Allow Larksong to install updates, then tap Install again")
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Notices.show("Couldn't open the installer")
    }
}

@Composable
fun UpdatesCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val p = LocalDb.current.p
    val scope = rememberCoroutineScope()
    val status by Updater.status.collectAsState()

    DbCard(modifier.fillMaxWidth(), padding = 0.dp) {
        Column {
            SettingRow("Larksong $APP_VERSION", when (val s = status) {
                Updater.Status.Idle -> "Tap to check for a newer version"
                Updater.Status.Checking -> "Checking GitHub…"
                Updater.Status.UpToDate -> "You have the latest version"
                is Updater.Status.Available -> "Version ${s.release.version} is available"
                is Updater.Status.Downloading -> "Downloading ${s.release.version}… ${(s.progress * 100).toInt()}%"
                is Updater.Status.Ready -> "Version ${s.release.version} downloaded. Tap Install."
                is Updater.Status.Failed -> s.message
            }) {
                when (val s = status) {
                    is Updater.Status.Available -> LinkButton("Download") {
                        scope.launch { Updater.download(s.release, updatesDir(ctx))?.let { installApk(ctx, it) } }
                    }
                    is Updater.Status.Ready -> LinkButton("Install") { installApk(ctx, s.file) }
                    Updater.Status.Checking, is Updater.Status.Downloading -> {}
                    else -> LinkButton("Check") { scope.launch { checkForUpdate() } }
                }
            }
            val s = status
            if (s is Updater.Status.Downloading) {
                Box(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.surface2)) {
                    Box(Modifier.fillMaxWidth(s.progress.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
                }
            }
            if (s is Updater.Status.Available && s.release.notes.isNotBlank()) {
                DText(s.release.notes.take(300), 13.sp, p.muted, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp))
            }
        }
    }
}
