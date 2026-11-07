// 后台队列测试验证非阻塞过载、清理屏障以及可见的磁盘失败。
package com.ohmyfly.logging

import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LogWorkerTest {
    @Test fun saturationNeverBlocksAndPublishesDroppedCount() = runTest {
        val worker = LogWorker(backgroundScope)
        repeat(QUEUE_CAPACITY + 10) { worker.enqueue(LogEntry("$it", 1, LogLevel.Info, "t", "记录")) }
        assertEquals(10L, worker.state.value.dropped)
    }

    @Test fun clearDropsEarlierQueuedRowsAndKeepsLaterRows() = runTest {
        val dir = Files.createTempDirectory("log-worker").toString().toPath()
        try {
            val worker = LogWorker(backgroundScope)
            worker.start { dir }
            val configured = async { worker.configure(LogPolicy()) }
            runCurrent(); configured.await()
            repeat(20) { worker.enqueue(LogEntry("old$it", 1, LogLevel.Info, "t", "旧记录")) }
            val clear = async { worker.clear() }
            runCurrent(); clear.await()
            worker.enqueue(LogEntry("new", 2, LogLevel.Info, "t", "新记录"))
            val page = async {
                worker.filePage(worker.files().single(), LogLevel.Detail, null)
            }
            runCurrent()
            assertEquals(listOf("new"), page.await().entries.map { it.id })
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    @Test fun fileListingAndReadUseSameClearBarrier() = runTest {
        val dir = Files.createTempDirectory("log-files-worker").toString().toPath()
        try {
            val worker = LogWorker(backgroundScope)
            worker.start { dir }
            val configured = async { worker.configure(LogPolicy()) }
            runCurrent(); configured.await()
            worker.enqueue(LogEntry("old", 1, LogLevel.Info, "t", "旧记录"))
            val listed = async { worker.files() }
            runCurrent()
            val file = listed.await().single()
            val clearing = async { worker.clear() }
            runCurrent(); clearing.await()
            val stale = async { worker.filePage(file, LogLevel.Detail, null) }
            val empty = async { worker.files() }
            runCurrent()
            assertTrue(stale.await().invalidated)
            assertTrue(empty.await().isEmpty())
            assertEquals(0, worker.state.value.files)
            assertEquals(0L, worker.state.value.bytes)
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    @Test fun brokenDirectoryIsVisibleAndCanBeRetried() = runTest {
        val dir = Files.createTempDirectory("log-failure").toString().toPath()
        try {
            var bad = true
            val worker = LogWorker(backgroundScope)
            worker.start { if (bad) error("拒绝访问") else dir }
            val first = async { runCatching { worker.configure(LogPolicy()) } }
            runCurrent()
            assertTrue(first.await().isFailure)
            assertNotNull(worker.state.value.failure)
            bad = false
            val retry = async { worker.configure(LogPolicy()) }
            runCurrent(); retry.await()
            assertTrue(worker.state.value.ready)
            assertEquals(null, worker.state.value.failure)
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }
}
