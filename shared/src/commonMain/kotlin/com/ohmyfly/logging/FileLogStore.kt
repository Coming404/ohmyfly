// 单写者文件存储：只管理专用命名文件，写前预留配额，旧文件永不截断。
package com.ohmyfly.logging

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import kotlin.time.Clock

internal data class LogFile(val path: Path, val created: Long, var bytes: Long)

internal class FileLogStore(
    private val fs: FileSystem,
    private val directory: Path,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val files = mutableListOf<LogFile>()
    private var current: LogFile? = null
    private var policy = LogPolicy()
    private var generation = newLogId()
    private var loaded = false
    private var lastMaintenance = 0L
    val bytes: Long get() = files.sumOf { it.bytes }
    val fileCount: Int get() = files.size

    fun configure(value: LogPolicy) {
        value.validate()
        if (!loaded) recover()
        if (value.fileMiB != policy.fileMiB) current = null
        policy = value
        maintain()
    }

    private fun recover() {
        fs.createDirectories(directory)
        files.clear()
        fs.list(directory).forEach { path ->
            val match = managedFile.matchEntire(path.name) ?: return@forEach
            val metadata = fs.metadata(path)
            if (metadata.isRegularFile && metadata.symlinkTarget == null) {
                files += LogFile(path, match.groupValues[1].toLong(), metadata.size ?: 0)
            }
        }
        files.sortBy { it.path.name }
        current = null
        loaded = true
    }

    fun append(entries: List<LogEntry>) {
        maintainIfDue()
        val pending = Buffer()
        for (entry in entries) {
            if (policy.level == LogLevel.Info && entry.level != LogLevel.Info) continue
            val line = encode(entry)
            var target = current ?: newFile()
            if (target.bytes + pending.size + line.size > policy.fileMiB * MIB ||
                pending.size + line.size > 131_072
            ) {
                writeBatch(pending)
                if (target.bytes + line.size > policy.fileMiB * MIB) target = newFile()
            }
            current = target
            pending.write(line, line.size)
        }
        writeBatch(pending)
    }

    private fun encode(entry: LogEntry): Buffer {
        val line = Buffer().writeUtf8(logJson.encodeToString(entry)).writeByte(10)
        if (line.size <= MAX_RECORD_BYTES) return line
        val summary = entry.copy(
            message = entry.message.take(1024) + "…[超出单条字节限制]",
        )
        return Buffer().writeUtf8(logJson.encodeToString(summary)).writeByte(10)
    }

    private fun newFile(): LogFile {
        val timestamp = maxOf(now(), (files.lastOrNull()?.created ?: 0L) + 1)
        val name = "ohmyfly-${timestamp.toString().padStart(19, '0')}-${newLogId()}.jsonl"
        val file = LogFile(directory / name, timestamp, 0)
        fs.write(file.path, mustCreate = true) {}
        files += file
        current = file
        return file
    }

    private fun writeBatch(buffer: Buffer) {
        if (buffer.size == 0L) return
        val target = current!!
        val size = buffer.size
        trimTo(policy.totalMiB * MIB - size, target)
        try {
            fs.appendingSink(target.path, mustExist = true).buffer().use { sink ->
                sink.write(buffer, size)
                sink.flush()
            }
        } finally {
            // 部分写入失败仍按真实文件大小记账；下次写入另起文件，避免接续残缺 JSON。
            target.bytes = fs.metadata(target.path).size ?: 0
        }
    }

    fun abandonWriter() {
        current = null
    }

    fun maintainIfDue() {
        if (now() - lastMaintenance >= 60_000) maintain()
    }

    fun maintain() {
        val cutoff = now() - policy.retentionDays * DAY_MILLIS
        files.toList().filter { it.created < cutoff || it.bytes == 0L }.forEach { remove(it) }
        trimTo(policy.totalMiB * MIB)
        lastMaintenance = now()
    }

    private fun trimTo(limit: Long, protected: LogFile? = null) {
        var total = bytes
        val iterator = files.toList().iterator()
        while (total > limit && iterator.hasNext()) {
            val file = iterator.next()
            if (file === protected) continue
            remove(file)
            total -= file.bytes
        }
    }

    private fun remove(file: LogFile) {
        fs.delete(file.path)
        files.remove(file)
        if (current === file) current = null
        generation = newLogId()
    }

    fun clear() {
        generation = newLogId()
        files.toList().forEach { remove(it) }
        current = null
    }

    fun page(level: LogLevel, cursor: LogCursor?): LogPage {
        maintainIfDue()
        if (cursor != null && (cursor.generation != generation || files.none { it.path.name == cursor.file })) {
            return LogPage(emptyList(), null, invalidated = true)
        }
        return LogReader(fs, files, generation).page(level, cursor)
    }
}

internal val logJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val managedFile = Regex("ohmyfly-([0-9]{19})-[0-9a-f-]{36}\\.jsonl")
