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

internal enum class MirrorTestPhase { Idle, Queued, Running, Stopped }

/** [Muted] is "no measurement yet", [Normal] is a measured pass, [Active] is a live probe. */
internal enum class MirrorTone { Muted, Normal, Active, Error }

/** Row-sized status: one short token; [mirrorStatusDetail] carries the full reason for readers. */
internal fun mirrorStatusText(result: MirrorProbeResult?, phase: MirrorTestPhase): String = when {
    phase == MirrorTestPhase.Queued -> "排队中"
    phase == MirrorTestPhase.Running -> "测试中…"
    phase == MirrorTestPhase.Stopped -> "已停止"
    result == null -> "未测试"
    result.status == ProbeStatus.Success -> result.latencyMs?.let { "$it ms" } ?: "已通过"
    result.status == ProbeStatus.Timeout -> "请求超时"
    else -> "不可用"
}

/** Full reason for the accessibility label; never a placeholder and never a zero-millisecond pass. */
internal fun mirrorStatusDetail(result: MirrorProbeResult?, phase: MirrorTestPhase): String = when (phase) {
    MirrorTestPhase.Queued -> "排队等待测试"
    MirrorTestPhase.Running -> "正在测试固定样本"
    MirrorTestPhase.Stopped -> "已停止，未完成本轮测试"
    MirrorTestPhase.Idle -> when {
        result == null -> "尚未测试"
        result.status == ProbeStatus.Success -> buildString {
            append(result.latencyMs?.let { "HTTP 首字节 $it ms" } ?: "内容校验通过")
            if (result.successes > 0) append("，成功 ${result.successes} 次")
            if (result.detail.isNotBlank()) append("，${result.detail}")
        }
        result.status == ProbeStatus.Timeout -> "请求超时"
        result.status == ProbeStatus.InvalidContent -> result.detail.ifBlank { "返回内容与固定样本不一致" }
        else -> result.detail.ifBlank { "请求失败" }
    }
}

internal fun mirrorTone(result: MirrorProbeResult?, phase: MirrorTestPhase): MirrorTone = when (phase) {
    MirrorTestPhase.Queued, MirrorTestPhase.Running -> MirrorTone.Active
    MirrorTestPhase.Stopped -> MirrorTone.Muted
    MirrorTestPhase.Idle -> when {
        result == null -> MirrorTone.Muted
        result.status == ProbeStatus.Success -> MirrorTone.Normal
        else -> MirrorTone.Error
    }
}

/**
 * Runs at most four probes at once. Each row commits and returns to Idle as soon as its own
 * probe finishes, so ordering updates during a batch. Starting a batch drops the targets'
 * previous results, so a stopped run never presents stale numbers as this run's output.
 *
 * Stopping is asynchronous: finished rows keep their result, unfinished rows become
 * [MirrorTestPhase.Stopped], and [running] stays true until the cancelled job has really
 * finished, so a new batch cannot overlap the cleanup or be overwritten by the old run.
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
    var stopping by mutableStateOf(false)
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
        if (!running && !stopping) start(listOf(key to entry))
    }

    fun measureAll(targets: List<Pair<String, MirrorEntry?>>) {
        if (!running && !stopping && targets.isNotEmpty()) start(targets)
    }

    fun stop() {
        if (!running || stopping) return
        val current = job ?: return
        runId += 1L
        val unfinished = phases
            .filterValues { it == MirrorTestPhase.Queued || it == MirrorTestPhase.Running }
            .keys
        if (unfinished.isNotEmpty()) {
            phases = phases.mapValues { (key, phase) ->
                if (key in unfinished) MirrorTestPhase.Stopped else phase
            }
        }
        stopped = unfinished.isNotEmpty()
        stopping = true
        current.cancel()
        scope.launch {
            current.join()
            if (job === current) {
                job = null
                running = false
                stopping = false
            }
        }
    }

    private fun start(targets: List<Pair<String, MirrorEntry?>>) {
        job?.cancel()
        runId += 1L
        val id = runId
        running = true
        stopping = false
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
