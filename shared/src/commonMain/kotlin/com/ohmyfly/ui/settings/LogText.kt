// 日期由真实epoch转换为明确的UTC时间；文件身份仍使用完整磁盘文件名。
package com.ohmyfly.ui.settings

import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.LogFileInfo
import kotlin.time.Instant

internal fun logTime(epochMillis: Long): String =
    Instant.fromEpochMilliseconds(epochMillis).toString().replace('T', ' ').removeSuffix("Z") + " UTC"

internal fun logFileName(file: LogFileInfo): String =
    Instant.fromEpochMilliseconds(file.createdMillis).toString()
        .removeSuffix("Z").replace('T', '-').replace(':', '-') + ".log"

internal fun logBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1_048_576 -> "${bytes / 1024} KiB"
    else -> "${bytes / 1_048_576}.${bytes % 1_048_576 * 10 / 1_048_576} MiB"
}

internal fun logText(entries: List<LogEntry>): String = entries.joinToString("\n\n") {
    "${logTime(it.timestampMillis)}  ${it.level.label}\n${it.message}"
}
