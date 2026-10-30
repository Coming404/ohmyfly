@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ohmyfly.startup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ohmyfly.settings.iosSettingsPath
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import okio.Path.Companion.toPath
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIScreen
import platform.UIKit.UIWindowScene
import platform.darwin.CTL_KERN
import platform.darwin.KERN_PROC
import platform.darwin.KERN_PROC_PID
import platform.darwin.kinfo_proc
import platform.darwin.sysctl
import platform.darwin.sysctlbyname
import platform.posix.getpid

fun recordIosStartupEntry() {
    val entry = startupUptimeMillis()
    val processStart = processStartEpochMillis()
    val zero = if (processStart == null) entry else {
        startupUptimeMillis() - (NSDate().timeIntervalSince1970 * 1000 - processStart)
    }
    val device = UIDevice.currentDevice
    val refreshRate = UIScreen.mainScreen.maximumFramesPerSecond.toDouble()
    StartupRecords.begin(StartupDevice(
        entryUptimeMillis = entry,
        zeroUptimeMillis = zero,
        origin = if (processStart == null) "MainViewController 入口（sysctl 不可用；非进程启动）"
            else "sysctl kinfo_proc.p_starttime，入口时映射至 CACurrentMediaTime 单调时钟",
        processStartKnown = processStart != null,
        recordedAt = NSDate().description,
        model = machineModel() ?: device.model,
        systemVersion = "${device.systemName} ${device.systemVersion}",
        refreshRate = refreshRate,
        maximumRefreshRate = refreshRate,
        path = (iosSettingsPath().toPath().parent!! / "startup-records.txt").toString(),
    ))
}

internal actual fun startupUptimeMillis(): Double = CACurrentMediaTime() * 1000

private fun processStartEpochMillis(): Double? = memScoped {
    val mib = allocArray<IntVar>(4)
    mib[0] = CTL_KERN
    mib[1] = KERN_PROC
    mib[2] = KERN_PROC_PID
    mib[3] = getpid()
    val info = alloc<kinfo_proc>()
    val size = alloc<ULongVar>()
    size.value = sizeOf<kinfo_proc>().toULong()
    if (sysctl(mib, 4u, info.ptr, size.ptr, null, 0uL) != 0 || size.value != sizeOf<kinfo_proc>().toULong()) {
        null
    } else {
        // p_starttime is a C macro; cinterop exposes the underlying union field instead.
        val start = info.kp_proc.p_un.__p_starttime
        start.tv_sec * 1000.0 + start.tv_usec / 1000.0
    }
}

private fun machineModel(): String? = memScoped {
    val size = alloc<ULongVar>()
    if (sysctlbyname("hw.machine", null, size.ptr, null, 0uL) != 0) return@memScoped null
    val buffer = allocArray<ByteVar>(size.value.toInt())
    if (sysctlbyname("hw.machine", buffer, size.ptr, null, 0uL) != 0) null else buffer.toKString()
}

@Composable
internal actual fun rememberStartupShare(): (String) -> Unit = remember {
    { path ->
        val window = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
            .filter { it.activationState == UISceneActivationStateForegroundActive }
            .firstNotNullOfOrNull { it.keyWindow } ?: error("未找到当前 key window")
        var presenter = window.rootViewController ?: error("未找到分享视图控制器")
        while (true) presenter = presenter.presentedViewController ?: break
        val activity = UIActivityViewController(
            activityItems = listOf(NSURL.fileURLWithPath(path)),
            applicationActivities = null,
        )
        activity.popoverPresentationController?.apply {
            sourceView = presenter.view
            sourceRect = presenter.view.bounds
        }
        presenter.presentViewController(activity, animated = true, completion = null)
    }
}
