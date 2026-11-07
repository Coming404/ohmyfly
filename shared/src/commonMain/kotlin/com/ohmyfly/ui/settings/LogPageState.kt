// 日志窗口只保留相邻三页正文，异步结果按页面代次隔离。
package com.ohmyfly.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ohmyfly.logging.AppLog
import com.ohmyfly.logging.LogFileCursor
import com.ohmyfly.logging.LogFileInfo
import com.ohmyfly.logging.LogFilePage
import com.ohmyfly.logging.LogLevel
import com.ohmyfly.ui.AppMessageKind
import kotlinx.coroutines.CancellationException

internal class LogPageState(
    private val enumerate: suspend () -> List<LogFileInfo> = AppLog::files,
    private val read: suspend (LogFileInfo, LogLevel, LogFileCursor?) -> LogFilePage = AppLog::filePage,
    private val erase: suspend () -> Unit = AppLog::clear,
    private val notify: (AppMessageKind, String) -> Unit = { _, _ -> },
) {
    var files by mutableStateOf(emptyList<LogFileInfo>())
        private set
    var selected by mutableStateOf<LogFileInfo?>(null)
        private set
    var level by mutableStateOf(LogLevel.Detail)
        private set
    var pages by mutableStateOf(emptyList<LogFilePage>())
        private set
    var busy by mutableStateOf(false)
        private set
    var clearing by mutableStateOf(false)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var revision by mutableStateOf(0)
        private set
    private var generation = 0
    val entries get() = pages.flatMap { it.entries }
    val older get() = pages.lastOrNull()?.older
    val newer get() = pages.firstOrNull()?.newer

    suspend fun refresh(invalidate: Boolean = false): Boolean {
        if (clearing) return false
        val token = ++generation
        busy = true
        failure = null
        if (invalidate) pages = emptyList()
        return try {
            val result = enumerate()
            if (token != generation) return false
            val file = result.firstOrNull { it.id == selected?.id }
            val page = file?.let { read(it, level, null) }
            if (token != generation) return false
            files = result
            selected = file
            pages = emptyList()
            revision++
            if (page != null) accept(page, null) else true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (token == generation) fail("日志读取失败，请重试")
            false
        } finally {
            if (token == generation) busy = false
        }
    }

    suspend fun select(file: LogFileInfo?) {
        if (clearing) return
        generation++
        selected = file
        pages = emptyList()
        failure = null
        busy = false
        revision++
        if (file != null) load(null)
    }

    suspend fun changeLevel(value: LogLevel) {
        if (clearing || level == value) return
        level = value
        select(selected)
    }

    suspend fun more(forward: Boolean) {
        if (busy || clearing || failure != null) return
        val cursor = (if (forward) newer else older) ?: return
        load(cursor)
    }

    suspend fun clear(): Boolean {
        if (clearing) return false
        val token = ++generation
        clearing = true
        busy = true
        failure = null
        return try {
            erase()
            if (token != generation) return false
            files = emptyList()
            selected = null
            pages = emptyList()
            revision++
            notify(AppMessageKind.Success, "日志已清理")
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (token == generation) fail("日志清理失败，请重试")
            false
        } finally {
            if (token == generation) { clearing = false; busy = false }
        }
    }

    fun stop() { generation++; busy = false; clearing = false }

    private suspend fun load(cursor: LogFileCursor?) {
        val file = selected ?: return
        val token = generation
        val requestedLevel = level
        busy = true
        failure = null
        try {
            val page = read(file, requestedLevel, cursor)
            if (token == generation) accept(page, cursor)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (token == generation) fail("日志读取失败，请重试")
        } finally {
            if (token == generation) busy = false
        }
    }

    private fun accept(page: LogFilePage, cursor: LogFileCursor?): Boolean {
        if (page.invalidated) {
            pages = emptyList()
            fail("日志文件已轮转或清理，请刷新")
            return false
        }
        // 仅保留相邻三页；被移出的正文通过双向字节游标重新读取。
        pages = when {
            cursor == null -> listOf(page)
            cursor.forward && page.entries.isEmpty() -> listOf(pages.first().copy(newer = page.newer)) + pages.drop(1)
            !cursor.forward && page.entries.isEmpty() -> pages.dropLast(1) + pages.last().copy(older = page.older)
            cursor.forward -> (listOf(page) + pages).take(3)
            else -> (pages + page).takeLast(3)
        }
        return true
    }

    private fun fail(text: String) {
        failure = text
        notify(AppMessageKind.Failure, text)
    }
}
