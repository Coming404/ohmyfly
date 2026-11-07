// 真实文件系统测试覆盖轮转、重启、分页边界和损坏尾部；临时目录自动清理。
package com.ohmyfly.logging

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class FileLogStoreTest {
    private val fs = FileSystem.SYSTEM
    private fun row(id: Int, message: String = "日志$id", level: LogLevel = LogLevel.Info) =
        LogEntry(id.toString(), 1, level, "test", message)

    private fun inDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("ohmyfly-logs").toString().toPath()
        try { block(directory) } finally { fs.deleteRecursively(directory) }
    }

    @Test fun rotationHonorsFileAndTotalByteLimits() = inDirectory { dir ->
        val store = FileLogStore(fs, dir) { 1_000 }
        store.configure(LogPolicy(1, 2))
        repeat(20) { batch -> store.append(List(100) { row(batch * 100 + it, "字".repeat(2048)) }) }
        assertTrue(store.bytes <= 2 * MIB)
        assertTrue(fs.list(dir).all { fs.metadata(it).size!! <= MIB })
        val file = store.listFiles().first()
        assertTrue(store.filePage(file, LogLevel.Detail, null).entries.isNotEmpty())
    }

    @Test fun pagesHaveNoDuplicatesOrGapsAndRestartKeepsFiles() = inDirectory { dir ->
        FileLogStore(fs, dir).apply {
            configure(LogPolicy())
            repeat(10) { n -> append(List(100) { row(n * 100 + it) }) }
        }
        val reopened = FileLogStore(fs, dir).apply { configure(LogPolicy()) }
        val file = reopened.listFiles().single()
        var cursor: LogFileCursor? = null
        val ids = mutableListOf<Int>()
        do {
            val page = reopened.filePage(file, LogLevel.Detail, cursor)
            assertTrue(page.entries.size <= 200)
            ids += page.entries.map { it.id.toInt() }
            cursor = page.older
        } while (cursor != null)
        assertEquals((999 downTo 0).toList(), ids)
    }

    @Test fun sparseLevelFilterAdvancesWithBoundedScan() = inDirectory { dir ->
        val store = FileLogStore(fs, dir).apply { configure(LogPolicy()) }
        store.append(listOf(row(1)))
        repeat(12) { n -> store.append(List(100) { row(n * 100 + it + 2, "x".repeat(8192), LogLevel.Detail) }) }
        val files = store.listFiles()
        val firstFile = files.first()
        val first = store.filePage(firstFile, LogLevel.Info, null)
        assertTrue(first.entries.isEmpty())
        assertTrue(first.older != null)
        assertEquals(1, store.filePage(firstFile, LogLevel.Detail, null).entries.map { it.level }.distinct().size)
        var steps = 0
        val found = mutableListOf<String>()
        files.forEach { file ->
            var cursor: LogFileCursor? = null
            do {
                val page = store.filePage(file, LogLevel.Info, cursor)
                found += page.entries.map { it.id }
                assertTrue(page.older != cursor || page.older == null)
                cursor = page.older
                steps++
            } while (cursor != null && steps < 100)
        }
        assertEquals(listOf("1"), found)
    }

    @Test fun clearInvalidatesCursorButDoesNotDeleteOtherFiles() = inDirectory { dir ->
        fs.write(dir / "keep.txt") { writeUtf8("用户文件") }
        val store = FileLogStore(fs, dir).apply { configure(LogPolicy()) }
        store.append(List(250) { row(it) })
        val file = store.listFiles().single()
        val cursor = store.filePage(file, LogLevel.Detail, null).older!!
        store.clear()
        assertTrue(store.filePage(file, LogLevel.Detail, cursor).invalidated)
        assertTrue(fs.exists(dir / "keep.txt"))
        assertEquals(0, store.fileCount)
        store.append(listOf(row(999)))
        assertEquals(listOf("999"),
            store.filePage(store.listFiles().single(), LogLevel.Detail, null).entries.map { it.id })
    }

    @Test fun retentionAndPolicyReductionRemoveOldestWithoutTruncation() = inDirectory { dir ->
        var now = 1_000L
        val store = FileLogStore(fs, dir) { now }
        store.configure(LogPolicy(1, 4, 7))
        store.append(listOf(row(1)))
        now += DAY_MILLIS * 2
        store.configure(LogPolicy(1, 4, 1))
        assertEquals(0L, store.bytes)
        repeat(8) { n -> store.append(List(100) { row(n * 100 + it, "x".repeat(8192)) }) }
        store.configure(LogPolicy(1, 1, 1))
        assertTrue(store.bytes <= MIB)
    }

    @Test fun partialTrailingLineDoesNotHideCompleteRecords() = inDirectory { dir ->
        val store = FileLogStore(fs, dir).apply { configure(LogPolicy()) }
        store.append(listOf(row(1), row(2)))
        fs.appendingSink(fs.list(dir).single()).use { sink ->
            val tail = okio.Buffer().writeUtf8("{\"message\":\"unfinished")
            sink.write(tail, tail.size)
        }
        val reopened = FileLogStore(fs, dir).apply { configure(LogPolicy()) }
        assertEquals(listOf("2", "1"),
            reopened.filePage(reopened.listFiles().single(), LogLevel.Detail, null).entries.map { it.id })
    }

    @Test fun gigabyteFileStillReadsOnlyOneBoundedWindow() = inDirectory { dir ->
        val policy = LogPolicy(1024, 2048, 7)
        FileLogStore(fs, dir).apply { configure(policy); append(listOf(row(1))) }
        val path = fs.list(dir).single()
        val size = 1024 * MIB
        // 稀疏文件验证真实偏移读取，无需在测试中分配或写入1 GiB正文。
        java.io.RandomAccessFile(path.toString(), "rw").use { it.setLength(size) }
        val store = FileLogStore(fs, dir).apply { configure(policy) }
        val page = store.filePage(store.listFiles().single(), LogLevel.Detail, null)
        assertTrue(page.entries.isEmpty())
        assertEquals(size - PAGE_SCAN_BYTES, page.older!!.offset)
        assertTrue(page.older!!.skipping)
    }

    @Test fun informationModeDoesNotPersistDetailedEvents() = inDirectory { dir ->
        val store = FileLogStore(fs, dir).apply { configure(LogPolicy(level = LogLevel.Info)) }
        store.append(listOf(row(1), row(2, level = LogLevel.Detail)))
        val page = store.filePage(store.listFiles().single(), LogLevel.Detail, null)
        assertEquals(listOf("1"), page.entries.map { it.id })
        assertFalse(page.invalidated)
    }
}
