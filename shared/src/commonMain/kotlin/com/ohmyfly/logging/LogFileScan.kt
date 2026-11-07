// 单文件双向扫描共用字节边界；每次最多扫描256 KiB、返回200条，不读取整文件。
package com.ohmyfly.logging

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import okio.Buffer
import okio.FileSystem
import okio.use

internal class LogFileScan(private val fs: FileSystem, private val file: LogFile) {
    private val entries = mutableListOf<LogEntry>()
    private val line = ByteArray(MAX_RECORD_BYTES)
    private var length = 0
    private var scanned = 0L
    private var boundary = 0L
    private var skipping = false

    fun page(level: LogLevel, cursor: LogFileCursor?): LogFilePage {
        val forward = cursor?.forward == true
        val start = cursor?.offset ?: file.bytes
        var position = start
        boundary = start
        skipping = cursor?.skipping ?: true
        fs.openReadOnly(file.path).use { handle ->
            while (position in 0..file.bytes && entries.size < 200 && scanned < PAGE_SCAN_BYTES) {
                val remaining = if (forward) file.bytes - position else position
                if (remaining == 0L) break
                val count = minOf(16_384L, remaining, PAGE_SCAN_BYTES - scanned)
                val buffer = Buffer()
                val read = handle.read(if (forward) position else position - count, buffer, count)
                if (read != count) throw LogStorageException("日志文件读取不完整，请刷新")
                scanned += read
                val chunk = buffer.readByteArray()
                val indices = if (forward) chunk.indices else chunk.lastIndex downTo 0
                for (index in indices) {
                    val byte = chunk[index]
                    if (forward) position++ else position--
                    if (byte == 10.toByte()) {
                        if (!skipping && length > 0) add(level, forward)
                        length = 0
                        skipping = false
                        boundary = if (forward) position else position + 1
                        if (entries.size == 200) break
                    } else if (!skipping) {
                        if (length == line.size) { skipping = true; length = 0 }
                        else line[length++] = byte
                    }
                }
            }
        }
        if (!forward && position == 0L && !skipping && length > 0 && entries.size < 200) add(level, false)
        val edge = if (position == 0L || position == file.bytes || skipping) position else boundary
        val continuation = LogFileCursor(edge, forward, if (forward) skipping else true)
        val older = if (forward) {
            if (start == 0L) null else LogFileCursor(start)
        } else if (edge == 0L) null else continuation
        val newer = if (forward) {
            if (edge == file.bytes) null else continuation
        } else if (start == file.bytes) null else LogFileCursor(start, forward = true, skipping = false)
        return LogFilePage(if (forward) entries.asReversed().toList() else entries.toList(), older, newer)
    }

    private fun add(level: LogLevel, forward: Boolean) {
        val bytes = line.copyOfRange(0, length)
        if (!forward) bytes.reverse()
        val entry = try { logJson.decodeFromString<LogEntry>(bytes.decodeToString()) }
        catch (_: SerializationException) { return }
        if (level == LogLevel.Detail || entry.level == LogLevel.Info) entries += entry
    }
}
