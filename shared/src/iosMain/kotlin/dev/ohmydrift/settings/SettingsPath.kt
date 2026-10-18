package dev.ohmydrift.settings

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS 设置文件路径：Application Support/ohmydrift/settings.preferences_pb。
 * 只计算路径，不在启动阶段创建目录；DataStore 首次写入时创建，失败由读取流上报 UI。
 * 不使用 Documents 或 /tmp。
 */
fun iosSettingsPath(): String {
    val base = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: throw IllegalStateException("无法定位 Application Support 目录")
    return "$base/ohmydrift/settings.preferences_pb"
}
