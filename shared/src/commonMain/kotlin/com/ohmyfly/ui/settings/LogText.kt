// 显示与复制统一使用北京时间；只转换展示值，不改变磁盘记录的 epoch 和文件身份。
package com.ohmyfly.ui.settings

import com.ohmyfly.logging.LogEntry
import com.ohmyfly.logging.LogFileInfo
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private fun beijingTime(epochMillis: Long): String =
    (Instant.fromEpochMilliseconds(epochMillis) + 8.hours).toString().removeSuffix("Z")

internal fun logTime(epochMillis: Long): String =
    beijingTime(epochMillis).substringBefore('.').replace('T', ' ')

internal fun logFileName(file: LogFileInfo): String =
    beijingTime(file.createdMillis).substringBefore('.').replace("-", "")
        .replace(":", "").replace('T', '-') + ".log"

// 只有同秒文件才附加序号，显示排序改变时名称保持一致。
internal fun logFileNames(files: List<LogFileInfo>): Map<String, String> = buildMap {
    files.groupBy(::logFileName).forEach { (name, group) ->
        group.sortedWith(compareBy<LogFileInfo> { it.createdMillis }.thenBy { it.id })
            .forEachIndexed { index, file ->
                put(file.id, if (index == 0) name else name.removeSuffix(".log") + "-${index + 1}.log")
            }
    }
}

internal fun logBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1_048_576 -> "${bytes / 1024} KiB"
    else -> "${bytes / 1_048_576}.${bytes % 1_048_576 * 10 / 1_048_576} MiB"
}

internal fun logText(entries: List<LogEntry>): String = entries.joinToString("\n") {
    "${logTime(it.timestampMillis)}  详情\n${it.message}"
}
