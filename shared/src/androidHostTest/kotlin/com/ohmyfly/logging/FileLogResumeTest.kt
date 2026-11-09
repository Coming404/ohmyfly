// 重启和配置变化只续写最新完整文件；损坏尾部保留原样。
package com.ohmyfly.logging

import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FileLogResumeTest {
    private val fs = FileSystem.SYSTEM
    private fun row(id: Int, text: String = "日志$id") = LogEntry("$id", 1000, LogLevel.Info, "test", text)
    private fun inDirectory(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("resume-logs").toString().toPath()
        try { block(dir) } finally { fs.deleteRecursively(dir) }
    }
    private fun openStore(dir: Path, policy: LogPolicy = LogPolicy(1, 4), now: Long = 1000) =
        FileLogStore(fs, dir) { now }.apply { configure(policy) }

    @Test fun restartAppendsToTheSameCompleteFile() = inDirectory { dir ->
        val first = openStore(dir)
        first.append(listOf(row(1)))
        val id = first.listFiles().single().id
        val reopened = openStore(dir)
        reopened.append(listOf(row(2)))
        val file = reopened.listFiles().single()
        assertEquals(id, file.id)
        assertEquals(listOf("2", "1"), reopened.filePage(file, LogLevel.Detail, null).entries.map { it.id })
    }

    @Test fun exactLimitRotatesBeforeTheNextWholeRecord() = inDirectory { dir ->
        val first = openStore(dir)
        first.append(listOf(row(1)))
        val id = first.listFiles().single().id
        val ending = logJson.encodeToString(row(2)) + "\n"
        val next = logJson.encodeToString(row(3)) + "\n"
        val remaining = MIB - first.bytes - ending.toByteArray().size - next.toByteArray().size - 1
        fs.write(dir / id) {
            writeUtf8(logJson.encodeToString(row(1)) + "\n")
            writeUtf8(" ".repeat(remaining.toInt()) + "\n")
            writeUtf8(ending)
        }
        val reopened = openStore(dir)
        reopened.append(listOf(row(3)))
        assertEquals(id, reopened.listFiles().single().id)
        assertEquals(MIB, reopened.bytes)
        reopened.append(listOf(row(4)))
        val files = reopened.listFiles()
        assertEquals(2, files.size)
        assertEquals(MIB, files.single { it.id == id }.bytes)
        assertEquals(listOf("4"), reopened.filePage(files.first(), LogLevel.Detail, null).entries.map { it.id })
    }

    @Test fun raisingAndLoweringLimitsKeepTheWriterWhenItStillFits() = inDirectory { dir ->
        val store = openStore(dir)
        store.append(listOf(row(1)))
        val id = store.listFiles().single().id
        store.configure(LogPolicy(2, 4))
        store.append(listOf(row(2)))
        store.configure(LogPolicy(1, 4))
        store.append(listOf(row(3)))
        assertEquals(id, store.listFiles().single().id)
        assertEquals(listOf("3", "2", "1"), store.filePage(store.listFiles().single(), LogLevel.Detail, null).entries.map { it.id })
    }

    @Test fun loweringLimitBelowCurrentSizePreservesHistoryAndStartsNewFile() = inDirectory { dir ->
        val store = openStore(dir, LogPolicy(2, 4))
        store.append(List(150) { row(it, "x".repeat(8192)) })
        val old = store.listFiles().single()
        assertTrue(old.bytes > MIB)
        store.configure(LogPolicy(1, 4))
        store.append(listOf(row(999)))
        val files = store.listFiles()
        assertEquals(2, files.size)
        assertEquals(old.bytes, fs.metadata(dir / old.id).size)
        assertTrue(files.first().bytes < MIB)
        assertTrue(store.bytes <= 4 * MIB)
    }

    @Test fun incompleteOrMalformedTailIsPreservedAndNeverJoined() = inDirectory { dir ->
        for (suffix in listOf("{\"message\":\"unfinished", "not-json\n", logJson.encodeToString(row(8)))) {
            val store = openStore(dir)
            store.clear()
            store.append(listOf(row(1)))
            val old = store.listFiles().single()
            fs.appendingSink(dir / old.id).use { sink ->
                val buffer = okio.Buffer().writeUtf8(suffix)
                sink.write(buffer, buffer.size)
            }
            val before = fs.read(dir / old.id) { readByteString() }
            val reopened = openStore(dir)
            reopened.append(listOf(row(2)))
            assertEquals(before, fs.read(dir / old.id) { readByteString() })
            val newest = reopened.listFiles().first()
            assertNotEquals(old.id, newest.id)
            assertEquals(listOf("2"), reopened.filePage(newest, LogLevel.Detail, null).entries.map { it.id })
        }
    }

    @Test fun corruptNewestFileNeverBackfillsAnOlderFile() = inDirectory { dir ->
        val first = openStore(dir)
        first.append(listOf(row(1)))
        first.abandonWriter()
        first.append(listOf(row(2)))
        val files = first.listFiles()
        fs.write(dir / files.first().id) { writeUtf8("incomplete") }
        val oldBytes = files.last().bytes
        val reopened = openStore(dir)
        reopened.append(listOf(row(3)))
        assertEquals(3, reopened.fileCount)
        assertEquals(oldBytes, fs.metadata(dir / files.last().id).size)
    }

    @Test fun restartAppliesRetentionAndQuotaBeforeSelectingWriter() = inDirectory { dir ->
        val first = openStore(dir, LogPolicy(1, 4, 7))
        first.append(List(300) { row(it, "x".repeat(8192)) })
        val newest = first.listFiles().first().id
        val limited = openStore(dir, LogPolicy(1, 1, 7))
        assertTrue(limited.bytes <= MIB)
        limited.append(listOf(row(999)))
        assertEquals(newest, limited.listFiles().single().id)
        val expired = openStore(dir, LogPolicy(1, 1, 1), 1000 + 2 * DAY_MILLIS)
        assertEquals(0, expired.fileCount)
        expired.append(listOf(row(1000)))
        assertNotEquals(newest, expired.listFiles().single().id)
    }

    @Test fun largeSparseFileResumesFromBoundedTailAndKeepsScanBudget() = inDirectory { dir ->
        val policy = LogPolicy(2048, 2048)
        val first = openStore(dir, policy)
        first.append(listOf(row(1)))
        val id = first.listFiles().single().id
        val end = 1024 * MIB
        val tail = ("\n" + logJson.encodeToString(row(2)) + "\n").toByteArray()
        RandomAccessFile((dir / id).toString(), "rw").use {
            it.setLength(end)
            it.seek(end - tail.size)
            it.write(tail)
        }
        val reopened = openStore(dir, policy)
        reopened.append(listOf(row(3)))
        val file = reopened.listFiles().single()
        assertEquals(id, file.id)
        val page = reopened.filePage(file, LogLevel.Detail, null)
        assertEquals(listOf("3", "2"), page.entries.map { it.id })
        assertEquals(file.bytes - PAGE_SCAN_BYTES, page.older!!.offset)
        assertTrue(page.older!!.skipping)
    }
}
