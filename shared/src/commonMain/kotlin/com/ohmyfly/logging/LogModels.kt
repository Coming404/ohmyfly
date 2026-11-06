// 日志协议与输入边界：队列仅持有复制后、有界的字符串，不持有异常或业务对象。
package com.ohmyfly.logging

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
internal enum class LogLevel { Info, Detail }

internal data class LogPolicy(
    val fileMiB: Long = 8,
    val totalMiB: Long = 128,
    val retentionDays: Long = 7,
    val level: LogLevel = LogLevel.Detail,
) {
    fun validate() {
        if (fileMiB <= 0 || totalMiB < fileMiB || retentionDays <= 0 ||
            totalMiB > Long.MAX_VALUE / MIB || retentionDays > Long.MAX_VALUE / DAY_MILLIS
        ) throw LogStorageException("日志容量或保留时间无效")
    }
}

@Serializable
internal data class LogEntry(
    val id: String,
    val timestampMillis: Long,
    val level: LogLevel,
    val module: String,
    val message: String,
    val schemaVersion: Int = 1,
)

internal data class LogCursor internal constructor(
    internal val generation: String,
    internal val file: String,
    internal val offset: Long,
    internal val skipping: Boolean = false,
)

internal data class LogPage(
    val entries: List<LogEntry>,
    val next: LogCursor?,
    val invalidated: Boolean = false,
)

internal data class LogStorageState(
    val bytes: Long = 0,
    val files: Int = 0,
    val dropped: Long = 0,
    val failure: String? = null,
    val ready: Boolean = false,
)

internal class LogStorageException(message: String) : Exception(message)
internal const val MIB = 1_048_576L
internal const val DAY_MILLIS = 86_400_000L
internal const val MAX_RECORD_BYTES = 65_536
internal const val PAGE_SCAN_BYTES = 262_144L
internal const val QUEUE_CAPACITY = 256

@OptIn(ExperimentalUuidApi::class)
internal fun newLogId(): String = Uuid.random().toString()
