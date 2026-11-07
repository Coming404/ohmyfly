// 单文件读取验证隔离、双向边界、快照追加与轮转失效，使用真实临时文件。
package com.ohmyfly.logging

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class LogFileReadTest {
    private val fs = FileSystem.SYSTEM
    private fun row(id: Int, text: String = "日志$id", level: LogLevel = LogLevel.Info) =
        LogEntry("$id", 1, level, "test", text)

    private fun inDirectory(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("single-log").toString().toPath()
        try { block(dir) } finally { fs.deleteRecursively(dir) }
    }

    @Test fun enumerationUsesRealNamesSizesAndOnlySelectedFileIsRead() = inDirectory { dir ->
        val store = FileLogStore(fs, dir) { 1234 }.apply { configure(LogPolicy(1, 8)) }
        store.append(List(400) { row(it, "x".repeat(8192)) })
        val files = store.listFiles()
        assertTrue(files.size > 1)
        assertEquals(store.bytes, files.sumOf { it.bytes })
        assertEquals(files.size, files.map { it.id }.toSet().size)
        val ids = files.map { file ->
            assertEquals(fs.metadata(dir / file.id).size, file.bytes)
            assertTrue(file.createdMillis >= 1234)
            var cursor: LogFileCursor? = null
            val result = mutableSetOf<String>()
            do {
                val page = store.filePage(file, LogLevel.Detail, cursor)
                assertTrue(page.entries.size <= 200)
                result += page.entries.map { it.id }
                cursor = page.older
            } while (cursor != null)
            result
        }
        assertEquals((0 until 400).map { "$it" }.toSet(), ids.flatten().toSet())
        assertEquals(400, ids.sumOf { it.size })
    }

    @Test fun reverseThenForwardPagesHaveNoGapsOrDuplicates() = inDirectory { dir ->
        val store = FileLogStore(fs, dir).apply { configure(LogPolicy()) }
        store.append(List(1000) { row(it) })
        val file = store.listFiles().single()
        var page = store.filePage(file, LogLevel.Detail, null)
        val reverse = page.entries.map { it.id }.toMutableList()
        while (page.older != null) {
            page = store.filePage(file, LogLevel.Detail, page.older)
            reverse += page.entries.map { it.id }
        }
        assertEquals((999 downTo 0).map { "$it" }, reverse)
        val forward = page.entries.map { it.id }.toMutableList()
        while (page.newer != null) {
            page = store.filePage(file, LogLevel.Detail, page.newer)
            forward.addAll(0, page.entries.map { it.id })
        }
        assertEquals((999 downTo 0).map { "$it" }, forward)
    }

    @Test fun snapshotTailDoesNotIncludeConcurrentAppends() = inDirectory { dir ->
        val store = FileLogStore(fs, dir).apply { configure(LogPolicy()) }
        store.append(listOf(row(1)))
        val before = store.listFiles().single()
        store.append(listOf(row(2)))
        assertEquals(listOf("1"), store.filePage(before, LogLevel.Detail, null).entries.map { it.id })
        val after = store.listFiles().single()
        assertTrue(after.bytes > before.bytes)
        assertEquals(listOf("2", "1"), store.filePage(after, LogLevel.Detail, null).entries.map { it.id })
    }

    @Test fun clearAndPolicyChangeInvalidateEvenInitialFileRead() = inDirectory { dir ->
        val store = FileLogStore(fs, dir).apply { configure(LogPolicy()) }
        store.append(listOf(row(1)))
        val old = store.listFiles().single()
        store.configure(LogPolicy(level = LogLevel.Info))
        assertTrue(store.filePage(old, LogLevel.Detail, null).invalidated)
        val configured = store.listFiles().single()
        assertFalse(store.filePage(configured, LogLevel.Detail, null).invalidated)
        store.clear()
        assertTrue(store.filePage(configured, LogLevel.Detail, null).invalidated)
        assertTrue(store.listFiles().isEmpty())
    }

    @Test fun sparseLargeFileAdvancesWithinSingleScanBudgetBothDirections() = inDirectory { dir ->
        val policy = LogPolicy(1024, 2048)
        FileLogStore(fs, dir).apply { configure(policy); append(listOf(row(1))) }
        val path = fs.list(dir).single()
        val size = 1024 * MIB
        java.io.RandomAccessFile(path.toString(), "rw").use { it.setLength(size) }
        val store = FileLogStore(fs, dir).apply { configure(policy) }
        val file = store.listFiles().single()
        val reverse = store.filePage(file, LogLevel.Detail, null)
        assertTrue(reverse.entries.isEmpty())
        assertEquals(size - PAGE_SCAN_BYTES, reverse.older!!.offset)
        val forward = store.filePage(file, LogLevel.Detail, LogFileCursor(0, true, false))
        assertEquals(listOf("1"), forward.entries.map { it.id })
        assertEquals(PAGE_SCAN_BYTES, forward.newer!!.offset)
    }
}
