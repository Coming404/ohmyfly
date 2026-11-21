// 捕获真实设置保存链的日志，确认回读与失败不会产生重复或虚假成功。
package com.ohmyfly.settings

import com.ohmyfly.logging.AppLog
import com.ohmyfly.logging.LogLevel
import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSessionLoggingTest {
    @Test fun observerEchoIsSilentButRealRepeatedOperationsRemainVisible() = runTest {
        val logs = SettingsLogCapture()
        val disk = MutableStateFlow(AppSettings())
        val session = SettingsSession(disk) { disk.value = it(disk.value) }
        try {
            backgroundScope.launch { session.run() }
            runCurrent()
            for (enabled in listOf(false, true, false, false)) {
                session.change("启用镜像") { it.copy(mirrorEnabled = enabled) }
                runCurrent()
            }
            val results = logs.messages.filter { it.contains("已保存并读回确认") }
            assertEquals(4, results.size)
            assertTrue(results[0].contains("镜像已停用"))
            assertTrue(results[1].contains("镜像已启用"))
            assertTrue(results[2].contains("镜像已停用"))
            assertTrue(results[3].contains("配置未发生变化"))
            assertEquals(1, logs.messages.count { it.contains("首次读取") })
            assertFalse(logs.messages.any { it.contains("检测到存储更新") || it.contains("剩余 0 项") })
        } finally { logs.close() }
    }

    @Test fun finalResultWaitsForReadbackAndSurvivesInfoLogEvent() = runTest {
        val logs = SettingsLogCapture()
        var disk = AppSettings(logging = com.ohmyfly.logging.LogPolicy(level = LogLevel.Detail))
        var reads = 0
        val write = CompletableDeferred<Unit>()
        val read = CompletableDeferred<Unit>()
        val session = SettingsSession(flow {
            if (++reads > 1) read.await()
            emit(disk)
            awaitCancellation()
        }) { transform -> write.await(); disk = transform(disk) }
        try {
            backgroundScope.launch { session.run() }
            runCurrent()
            session.change("日志保留时间") { it.copy(logging = it.logging.copy(retentionDays = 8)) }
            runCurrent()
            assertFalse(logs.messages.any { it.contains("已保存并读回确认") })
            write.complete(Unit)
            runCurrent()
            assertFalse(logs.messages.any { it.contains("已保存并读回确认") })
            KotlinLoggingConfiguration.direct.logLevel = Level.INFO
            read.complete(Unit)
            runCurrent()
            val results = logs.messages.filter { it.contains("已保存并读回确认") }
            assertEquals(listOf("[设置] 已保存并读回确认：日志保留时间改为 8 天"), results)
            assertEquals(8, session.state.confirmedSettings!!.logging.retentionDays)
        } finally { logs.close() }
    }

    @Test fun writeFailureNeverReportsAnOptimisticResultAsSaved() = runTest {
        val logs = SettingsLogCapture()
        val disk = MutableStateFlow(AppSettings())
        val gate = CompletableDeferred<Unit>()
        val session = SettingsSession(disk) { gate.await(); error("private-storage-error") }
        try {
            backgroundScope.launch { session.run() }
            runCurrent()
            session.change("启用镜像") { it.copy(mirrorEnabled = false) }
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertFalse(logs.messages.any { it.contains("已保存并读回确认") || it.contains("镜像已停用") })
            assertTrue(logs.messages.single { it.contains("保存失败") }.contains("已确认配置保持不变"))
            assertFalse(logs.messages.any { it.contains("private-storage-error") })
            assertTrue(session.state.settings.mirrorEnabled)
        } finally { logs.close() }
    }

    @Test fun failedReadbackReportsUncertaintyWithoutSuccess() = runTest {
        val logs = SettingsLogCapture()
        var reads = 0
        var disk = AppSettings()
        val session = SettingsSession(flow {
            if (++reads > 1) error("private-read-error")
            emit(disk)
            awaitCancellation()
        }) { disk = it(disk) }
        try {
            backgroundScope.launch { session.run() }
            runCurrent()
            session.change("启用镜像") { it.copy(mirrorEnabled = false) }
            runCurrent()
            assertFalse(logs.messages.any { it.contains("已保存并读回确认") || it.contains("镜像已停用") })
            assertEquals(1, logs.messages.count { it.contains("保存结果待确认") })
            assertTrue(session.state.writeUncertain)
            assertFalse(logs.messages.any { it.contains("private-read-error") })
        } finally { logs.close() }
    }

    @Test fun finalDescriptionUsesStoredValueInsteadOfRequestedValue() = runTest {
        val logs = SettingsLogCapture()
        val disk = MutableStateFlow(AppSettings())
        val session = SettingsSession(disk) { disk.value = it(disk.value).copy(mirrorEnabled = true) }
        try {
            backgroundScope.launch { session.run() }
            runCurrent()
            session.change("启用镜像") { it.copy(mirrorEnabled = false) }
            runCurrent()
            val result = logs.messages.single { it.contains("已保存并读回确认") }
            assertTrue(result.contains("配置未发生变化"))
            assertFalse(result.contains("镜像已停用"))
        } finally { logs.close() }
    }
}

private class SettingsLogCapture {
    val messages = mutableListOf<String>()
    private val previousAppender: Appender
    private val previousLevel: Level
    init {
        AppLog.logger
        previousAppender = KotlinLoggingConfiguration.direct.appender
        previousLevel = KotlinLoggingConfiguration.direct.logLevel
        KotlinLoggingConfiguration.direct.logLevel = Level.DEBUG
        KotlinLoggingConfiguration.direct.appender = object : Appender {
            override fun log(loggingEvent: KLoggingEvent) { messages += loggingEvent.message.orEmpty() }
        }
    }
    fun close() {
        KotlinLoggingConfiguration.direct.appender = previousAppender
        KotlinLoggingConfiguration.direct.logLevel = previousLevel
    }
}
