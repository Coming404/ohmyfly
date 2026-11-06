// Android 日志位于不参与备份的持久沙箱，目录获取与创建均延迟到后台。
package com.ohmyfly.logging

import android.content.Context
import okio.Path.Companion.toPath

private lateinit var appContext: Context

public fun initializeAppLogging(context: Context) {
    val application = context.applicationContext
    appContext = application
    AppLog.initialize { application.noBackupFilesDir.resolve("logs").absolutePath.toPath() }
}

internal actual fun copyLogText(text: String) {
    appContext.getSystemService(android.content.ClipboardManager::class.java)
        .setPrimaryClip(android.content.ClipData.newPlainText("日志", text))
}
