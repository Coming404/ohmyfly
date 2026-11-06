// 反向字节分页：每次最多读取 256 KiB，最多返回 200 条，损坏长行也不会无界分配。
package com.ohmyfly.logging

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import okio.Buffer
import okio.FileSystem
import okio.use

internal class LogReader(
    private val fs: FileSystem,
    private val files: List<LogFile>,
    private val generation: String,
) {
    private var scanned = 0L
    private val entries = mutableListOf<LogEntry>()
    private data class Position(val offset: Long, val skipping: Boolean)

    fun page(level: LogLevel, cursor: LogCursor?): LogPage {
        var index = if (cursor == null) files.lastIndex else files.indexOfFirst { it.path.name == cursor.file }
        if (index < 0) return LogPage(emptyList(), null)
        var position = Position(cursor?.offset ?: files[index].bytes, cursor?.skipping ?: true)
        while (index >= 0 && entries.size < 200 && scanned < PAGE_SCAN_BYTES) {
            position = scan(files[index], position, level)
            if (position.offset == 0L) {
                index--
                if (index >= 0) position = Position(files[index].bytes, true)
            }
        }
        val next = if (index < 0) null else LogCursor(generation, files[index].path.name, position.offset, position.skipping)
        return LogPage(entries.toList(), next)
    }

    private fun scan(file: LogFile, start: Position, level: LogLevel): Position {
        var position = start.offset.coerceAtMost(file.bytes)
        var boundary = position
        var skipping = start.skipping
        val line = ByteArray(MAX_RECORD_BYTES)
        var length = 0
        fs.openReadOnly(file.path).use { handle ->
            while (position > 0 && entries.size < 200 && scanned < PAGE_SCAN_BYTES) {
                val count = minOf(16_384L, position, PAGE_SCAN_BYTES - scanned)
                val buffer = Buffer()
                val read = handle.read(position - count, buffer, count)
                if (read != count) throw LogStorageException("日志文件读取不完整，请刷新")
                scanned += read
                val chunk = buffer.readByteArray()
                for (i in chunk.lastIndex downTo 0) {
                    position--
                    val byte = chunk[i]
                    if (byte == 10.toByte()) {
                        if (!skipping && length > 0) add(line, length, level)
                        length = 0
                        skipping = false
                        boundary = position
                        if (entries.size == 200) break
                    } else if (!skipping) {
                        if (length == line.size) {
                            skipping = true
                            length = 0
                        } else line[length++] = byte
                    }
                }
            }
        }
        if (position == 0L) {
            if (!skipping && length > 0 && entries.size < 200) add(line, length, level)
            return Position(0, false)
        }
        // 普通跨块行留到下页完整重读；超长损坏行只保存跳过状态，不携带正文。
        return if (skipping) Position(position, true) else Position(boundary, false)
    }

    private fun add(reverse: ByteArray, length: Int, level: LogLevel) {
        val entry = try {
            logJson.decodeFromString<LogEntry>(reverse.copyOfRange(0, length).reversedArray().decodeToString())
        } catch (_: SerializationException) {
            return
        }
        if (level == LogLevel.Detail || entry.level == LogLevel.Info) entries += entry
    }
}
