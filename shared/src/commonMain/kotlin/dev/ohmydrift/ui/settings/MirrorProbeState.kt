package dev.ohmydrift.ui.settings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorProbe
import dev.ohmydrift.mirror.MirrorProbeResult
import dev.ohmydrift.mirror.ProbeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** The direct connection shares the probe result map with mirrors under this fixed key. */
internal const val DIRECT_MIRROR_KEY = "direct"

internal enum class MirrorTestPhase { Idle, Queued, Running }

internal enum class MirrorTone { Normal, Active, Error }

/** Only claims what the probe returned; a timeout is never rendered as 0 ms. */
internal fun mirrorStatusText(result: MirrorProbeResult?, phase: MirrorTestPhase): String = when {
    phase == MirrorTestPhase.Queued -> "排队中"
    phase == MirrorTestPhase.Running -> "测试中…"
    result == null -> "未测试"
    result.status == ProbeStatus.Success -> buildString {
        append(result.latencyMs?.let { "HTTP 首字节 $it ms" } ?: "内容校验通过")
        if (result.successes > 0) append(" · 成功 ${result.successes} 次")
    }
    result.status == ProbeStatus.Timeout -> "请求超时"
    result.status == ProbeStatus.InvalidContent -> "内容校验未通过"
    else -> result.detail.takeIf { it.isNotBlank() }?.let { "失败：$it" } ?: "请求失败"
}

internal fun mirrorTone(result: MirrorProbeResult?, phase: MirrorTestPhase): MirrorTone = when {
    phase != MirrorTestPhase.Idle -> MirrorTone.Active
    result == null -> MirrorTone.Normal
    result.status == ProbeStatus.Success -> MirrorTone.Active
    else -> MirrorTone.Error
}

/**
 * Runs at most four probes at once. Each row commits and returns to Idle as soon as its own
 * probe finishes, so ordering updates during a batch. Starting a batch drops the targets'
 * previous results, so a stopped run never presents stale numbers as this run's output.
 */
@Stable
internal class MirrorProbeRunner(
    private val probe: MirrorProbe,
    private val scope: CoroutineScope,
) {
    var results by mutableStateOf<Map<String, MirrorProbeResult>>(emptyMap())
        private set
    var phases by mutableStateOf<Map<String, MirrorTestPhase>>(emptyMap())
        private set
    var running by mutableStateOf(false)
        private set
    var stopped by mutableStateOf(false)
        private set
    var completed by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set

    private val gate = Semaphore(4)
    private val tokens = mutableMapOf<String, Long>()
    private var job: Job? = null
    private var runId = 0L

    fun result(key: String): MirrorProbeResult? = results[key]

    fun phase(key: String): MirrorTestPhase = phases[key] ?: MirrorTestPhase.Idle

    /** Invalidates a stored result and any probe still in flight for that key. */
    fun forget(key: String) {
        tokens[key] = (tokens[key] ?: 0L) + 1L
        results = results - key
        phases = phases - key
    }

    fun measure(key: String, entry: MirrorEntry?) {
        if (!running) start(listOf(key to entry))
    }

    fun measureAll(targets: List<Pair<String, MirrorEntry?>>) {
        if (!running && targets.isNotEmpty()) start(targets)
    }

    fun stop() {
        if (!running) return
        runId += 1L
        job?.cancel()
        running = false
        stopped = true
        phases = emptyMap()
    }

    private fun start(targets: List<Pair<String, MirrorEntry?>>) {
        job?.cancel()
        runId += 1L
        val id = runId
        running = true
        stopped = false
        completed = 0
        total = targets.size
        val keys = targets.map { it.first }.toSet()
        results = results - keys
        phases = keys.associateWith { MirrorTestPhase.Queued }
        job = scope.launch {
            try {
                coroutineScope {
                    targets.map { (key, entry) -> async { probeOne(id, key, entry) } }.awaitAll()
                }
            } finally {
                if (id == runId) running = false
            }
        }
    }

    private suspend fun probeOne(run: Long, key: String, entry: MirrorEntry?) {
        val token = nextToken(key)
        gate.withPermit {
            if (run != runId) return@withPermit
            if (tokens[key] != token) {
                if (run == runId) {
                    completed += 1
                    phases = phases - key
                }
                return@withPermit
            }
            if (run == runId) phases = phases + (key to MirrorTestPhase.Running)
            val outcome = try {
                probe.measure(entry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                MirrorProbeResult(
                    status = ProbeStatus.Failed,
                    latencyMs = null,
                    successes = 0,
                    detail = "探测未完成，请重试",
                )
            }
            if (run == runId && tokens[key] == token) {
                results = results + (key to outcome)
                phases = phases - key
                completed += 1
            }
        }
    }

    private fun nextToken(key: String): Long {
        val next = (tokens[key] ?: 0L) + 1L
        tokens[key] = next
        return next
    }
}
