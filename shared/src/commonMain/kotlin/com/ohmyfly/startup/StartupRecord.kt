package com.ohmyfly.startup

import com.ohmyfly.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.math.ceil
import kotlin.math.roundToLong

internal data class StartupDevice(
    val entryUptimeMillis: Double,
    val zeroUptimeMillis: Double,
    val origin: String,
    val processStartKnown: Boolean,
    val recordedAt: String,
    val model: String,
    val systemVersion: String,
    val refreshRate: Double,
    val maximumRefreshRate: Double,
    val path: String,
)

internal expect fun startupUptimeMillis(): Double

internal object StartupRecords {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fileMutex = Mutex()
    private var count = 0
    lateinit var current: StartupRecord
        private set
    var writeFailed = false
        private set

    fun begin(device: StartupDevice) {
        val record = StartupRecord(device, ++count)
        current = record
        scope.launch {
            delay(record.remainingMillis())
            record.stop()
            try {
                withContext(Dispatchers.IO) { persist(record) }
                writeFailed = false
            } catch (_: IOException) {
                writeFailed = true
            }
        }
    }

    fun appComposed(version: String): StartupRecord = current.also { it.appComposed(version) }

    fun mark(label: String) = current.mark(label)

    private suspend fun persist(record: StartupRecord) = fileMutex.withLock {
        val fs = FileSystem.SYSTEM
        val path = record.device.path.toPath()
        val previous = if (fs.exists(path)) fs.read(path) { readUtf8() } else ""
        val text = retainStartupRecords(previous, record.report())
        fs.createDirectories(path.parent!!)
        val temporary = path.parent!! / "startup-records.tmp"
        fs.write(temporary) { writeUtf8(text) }
        fs.atomicMove(temporary, path)
    }
}

internal class StartupRecord(val device: StartupDevice, private val sequence: Int) {
    val deadlineMillis = device.entryUptimeMillis + STARTUP_WINDOW_MILLIS
    var recording = true
        private set
    private val capacity = ceil(device.maximumRefreshRate * STARTUP_WINDOW_MILLIS / 1000).toInt() + 2
    private val frameNanos = LongArray(capacity)
    private val frameElapsedMillis = DoubleArray(capacity)
    private var frameCount = 0
    private var version = "未进入 App()"
    private var compositionMillis: Double? = null
    private var settingsMillis: Double? = null
    private var settingsSucceeded = false
    private var firstDrawMillis: Double? = null
    private var appearance: AppSettings? = null
    private var dark = false
    private val presentNanos = LongArray(capacity)
    private val presentElapsedMillis = DoubleArray(capacity)
    private var presentCount = 0
    private var droppedPresents = 0
    private var presentHookInstalled = false
    private var observedDrawables = 0
    private val events = ArrayList<StartupEvent>()
    private var eventsTruncated = false

    val presentSampling: Boolean get() = withinWindow()

    fun remainingMillis(): Long = ceil(deadlineMillis - startupUptimeMillis()).toLong().coerceAtLeast(0)

    fun appComposed(value: String) {
        if (compositionMillis == null && withinWindow()) {
            compositionMillis = elapsedMillis()
            version = value
        }
    }

    fun settingsRead(succeeded: Boolean) {
        if (settingsMillis == null && withinWindow()) {
            settingsMillis = elapsedMillis()
            settingsSucceeded = succeeded
        }
    }

    fun appearance(settings: AppSettings, isDark: Boolean) {
        if (withinWindow()) {
            appearance = settings
            dark = isDark
        }
    }

    fun firstDraw() {
        if (firstDrawMillis == null && withinWindow()) firstDrawMillis = elapsedMillis()
    }

    fun sampleFrame(nanos: Long) {
        val now = startupUptimeMillis()
        if (!recording || now >= deadlineMillis || frameCount == capacity) return
        frameNanos[frameCount] = nanos
        frameElapsedMillis[frameCount] = now - device.zeroUptimeMillis
        frameCount += 1
    }

    fun mark(label: String) {
        if (!withinWindow()) return
        if (events.size == MAX_STARTUP_EVENTS) eventsTruncated = true
        else events += StartupEvent(elapsedMillis(), label)
    }

    fun markPresentHookInstalled() { presentHookInstalled = true }

    fun observeDrawable() {
        if (withinWindow()) observedDrawables += 1
    }

    /** iOS presented handler, delivered on main. presentedTime 0 means the drawable never reached the screen. */
    fun samplePresent(presentedMillis: Double) {
        if (!withinWindow()) return
        if (presentedMillis <= 0.0) {
            droppedPresents += 1
            return
        }
        if (presentCount == capacity) return
        presentNanos[presentCount] = (presentedMillis * 1_000_000).roundToLong()
        presentElapsedMillis[presentCount] = presentedMillis - device.zeroUptimeMillis
        presentCount += 1
    }

    fun stop() { recording = false }

    private fun withinWindow(): Boolean = recording && startupUptimeMillis() < deadlineMillis
    private fun elapsedMillis(): Double = startupUptimeMillis() - device.zeroUptimeMillis

    fun report(): String = buildString {
        appendLine(STARTUP_RECORD_HEADER)
        appendLine("记录时间: ${device.recordedAt}")
        appendLine("版本: $version")
        appendLine("机型: ${device.model}")
        appendLine("系统: ${device.systemVersion}")
        appendLine("刷新率: ${device.refreshRate} Hz（入口时设备报告值）")
        appendLine("进程内第 $sequence 段: ${if (sequence == 1) "本进程第一次记录" else "本进程后续记录"}")
        appendLine("时间原点: ${device.origin}；以下时间均相对此原点，单位 ms")
        appendLine("进程启动: ${if (device.processStartKnown) "0 ms" else "未知；使用平台入口为 0"}")
        appendLine("平台入口: ${(device.entryUptimeMillis - device.zeroUptimeMillis).ms()} ms")
        appendLine("App() 首次组合: ${compositionMillis.stage()}")
        val readResult = if (settingsSucceeded) "成功" else "失败"
        appendLine("设置首次读取完成: ${settingsMillis.stage()}${if (settingsMillis != null) "（$readResult）" else ""}")
        appendLine("首帧绘制: ${firstDrawMillis.stage()}（Compose 根内容 drawContent 完成，非 GPU 呈现）")
        appendLine("记录窗口: 平台入口起 $STARTUP_WINDOW_MILLIS ms；窗口外帧不纳入统计")
        appendAppearance(appearance, dark)
        val frames = startupIntervals(frameNanos, frameElapsedMillis, frameCount)
        val presents = startupIntervals(presentNanos, presentElapsedMillis, presentCount)
        appendFrames(summarizeStartupIntervals(frames, frameCount, device.refreshRate))
        appendPresents(summarizeStartupIntervals(presents, presentCount, device.refreshRate))
        appendEvents(frames, presents)
    }

    private fun StringBuilder.appendAppearance(settings: AppSettings?, isDark: Boolean) {
        if (settings == null) {
            appendLine("外观: 未在窗口内进入设置内容")
        } else {
            appendLine("窗口结束时外观: materialMode=${settings.materialMode.name}, " +
                "reduceTransparency=${settings.reduceTransparency}, reduceMotion=${settings.reduceMotion}, " +
                "${if (isDark) "深色" else "浅色"}")
        }
    }

    private fun StringBuilder.appendFrames(summary: StartupFrameSummary) {
        appendLine("帧采样: App 根部 withFrameNanos；间隔取相邻帧时间戳差；时间点为区间结束回调时间")
        appendLine("帧数: ${summary.frameCount}；间隔数: ${(summary.frameCount - 1).coerceAtLeast(0)}")
        appendIntervals(summary, "帧")
    }

    private fun StringBuilder.appendPresents(summary: StartupFrameSummary) {
        appendLine("上屏采样: CAMetalDrawable presentedTime（画面实际显示到屏幕的时间）；时间点为该次上屏时间")
        appendLine("上屏钩子: ${if (presentHookInstalled) "已安装" else "未安装"}；窗口内取得绘制缓冲 $observedDrawables 次")
        if (summary.frameCount == 0 && droppedPresents == 0) {
            appendLine("上屏次数: 0（Android 不记录；iOS 为 0 表示没有收到上屏回调）")
            return
        }
        appendLine("上屏次数: ${summary.frameCount}；未上屏（presentedTime 为 0）: $droppedPresents")
        appendIntervals(summary, "上屏间隔")
    }

    private fun StringBuilder.appendIntervals(summary: StartupFrameSummary, kind: String) {
        appendLine("间隔中位数: ${summary.medianMillis.statistic()}；P95: ${summary.p95Millis.statistic()}（nearest-rank）")
        appendLine("长${kind}阈值: > ${(1.5 * 1000 / device.refreshRate).ms()} ms（1.5 × 刷新周期）")
        appendLine("长${kind}次数: ${summary.longFrames.size}")
        summary.longFrames.take(MAX_LISTED_INTERVALS).forEach { appendInterval(it) }
        if (summary.longFrames.size > MAX_LISTED_INTERVALS) {
            appendLine("  其余 ${summary.longFrames.size - MAX_LISTED_INTERVALS} 次未列出")
        }
        appendLine("最长的 5 个${kind}（不足 5 个间隔则全部列出）:")
        summary.longestFrames.forEach { appendInterval(it) }
    }

    private fun StringBuilder.appendInterval(interval: StartupFrameInterval) =
        appendLine("  时间点 ${interval.endMillis.ms()} ms，时长 ${interval.durationMillis.ms()} ms")

    private fun StringBuilder.appendEvents(frames: List<StartupFrameInterval>, presents: List<StartupFrameInterval>) {
        val truncated = if (eventsTruncated) "，超过 $MAX_STARTUP_EVENTS 条后不再记录" else ""
        appendLine("操作事件: ${events.size} 条$truncated；每条附其后 ${STARTUP_EVENT_WINDOW_MILLIS.ms()} ms 内最长帧间隔和最长上屏间隔")
        events.forEach { event ->
            val frame = longestIntervalAfter(frames, event.elapsedMillis, STARTUP_EVENT_WINDOW_MILLIS)
            val present = longestIntervalAfter(presents, event.elapsedMillis, STARTUP_EVENT_WINDOW_MILLIS)
            appendLine("  ${event.elapsedMillis.ms()} ms ${event.label}：帧 ${frame?.durationMillis.measured()}，" +
                "上屏 ${present?.durationMillis.measured()}")
        }
    }
}

private const val MAX_STARTUP_EVENTS = 200
private const val MAX_LISTED_INTERVALS = 30

private fun Double?.measured(): String = if (this == null) "无数据" else "${ms()} ms"
private fun Double.ms(): String = ((this * 1000).roundToLong() / 1000.0).toString()
private fun Double?.stage(): String = if (this == null) "未在窗口内到达" else "${ms()} ms"
private fun Double?.statistic(): String = if (this == null) "无足够帧" else "${ms()} ms"
