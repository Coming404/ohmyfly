// iOS 日志使用 Application Support 并显式排除备份；生命周期观察者与进程单例同寿命。
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ohmyfly.logging

import okio.FileSystem
import okio.SYSTEM
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillTerminateNotification
import platform.darwin.NSObjectProtocol

private val lifecycleObservers = mutableListOf<NSObjectProtocol>()

public fun initializeAppLogging() {
    val started = AppLog.initialize {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: throw LogStorageException("无法定位日志持久目录")
        val path = "$base/logs".toPath()
        FileSystem.SYSTEM.createDirectories(path)
        val excluded = NSURL.fileURLWithPath(path.toString()).setResourceValue(
            true, forKey = NSURLIsExcludedFromBackupKey, error = null,
        )
        if (!excluded) throw LogStorageException("无法关闭日志目录备份")
        path
    }
    if (!started) return
    val center = NSNotificationCenter.defaultCenter
    listOf(UIApplicationDidEnterBackgroundNotification, UIApplicationWillTerminateNotification).forEach { name ->
        lifecycleObservers += center.addObserverForName(name, null, NSOperationQueue.mainQueue) {
            flushAppLogsInBackground()
        }
    }
}
