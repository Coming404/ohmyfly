package com.ohmyfly.startup

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.Date

@Suppress("DEPRECATION")
fun recordAndroidStartupEntry(activity: Activity) {
    val entry = startupUptimeMillis()
    val display = activity.windowManager.defaultDisplay
    StartupRecords.begin(StartupDevice(
        entryUptimeMillis = entry,
        zeroUptimeMillis = Process.getStartUptimeMillis().toDouble(),
        origin = "Android Process.getStartUptimeMillis / SystemClock.uptimeMillis",
        processStartKnown = true,
        recordedAt = Date().toString(),
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        systemVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        refreshRate = display.refreshRate.toDouble(),
        maximumRefreshRate = display.supportedModes.maxOf { it.refreshRate.toDouble() },
        path = File(activity.filesDir, "startup/startup-records.txt").absolutePath,
    ))
}

internal actual fun startupUptimeMillis(): Double = SystemClock.uptimeMillis().toDouble()

@Composable
internal actual fun rememberStartupShare(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { path ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.startup-records", File(path))
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("启动记录", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出启动记录"))
        }
    }
}
