package com.ohmyfly.catalog

import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.subscription.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal val appCatalog = CatalogStore()

internal class CatalogStore(
    internal val worker: CoroutineDispatcher = Dispatchers.Default,
    private val bridge: (String) -> String = ::nativeCatalog,
    private val open: () -> String = ::openNativeCatalog,
) {
    internal fun call(action: String, block: JsonObjectBuilder.() -> Unit = {}): JsonObject =
        decodeCatalog(bridge(buildJsonObject { put("action", action); block() }.toString()))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pages = CatalogPages()
    private data class Position(
        val index: Int = 0, val anchor: Long = 0, val backwards: Boolean = false,
        val first: Long = 0, val last: Long = 0,
    )
    private val positions = mutableMapOf("towers" to Position(), "flights" to Position())
    private val mutableState = MutableStateFlow(CatalogState())
    val state = mutableState.asStateFlow()
    private var opened = false
    private var active: String? = null
    private var foreground = true
    private var pageJob: Job? = null
    private var initialization: Job? = null
    private var operation: Job? = null
    private var epoch = 0L
    private var filterJob: Job? = null
    private var filterSequence = 0L
    private var reloadSequence = 0L
    private var measurementsSequence = 0L
    private val importer = SubscriptionImporter(SubscriptionFetcher(createSubscriptionClient()), ::parseCatalogSubscription,
        queue = ::CatalogProviderQueue)

    fun start() {
        if (initialization?.isActive == true) return
        initialization = scope.launch {
            try {
                if (!opened) {
                    withContext(worker) { decodeCatalog(open()) }
                    opened = true
                }
                reload()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.update { it.copy(ready = false, failure = "目录打开失败，请检查存储后重试") } }
        }
    }

    // 规则编辑界面尚未确定；调用方通过目录业务接口应用规则，不混入名称/协议过滤。
    fun updateTowerDeduplication(id: Long, rule: DeduplicationRule) = runOperation {
        val encoded = catalogJson.encodeToJsonElement(DeduplicationRule.serializer(), rule)
        call("setTowerDedup") { put("id", id); put("rule", encoded) }
    }

    fun updateOutputDeduplication(rule: DeduplicationRule) = runOperation {
        val encoded = catalogJson.encodeToJsonElement(DeduplicationRule.serializer(), rule)
        call("setDedup") { put("dedup", buildJsonObject { put("output", encoded) }) }
    }

    fun updateFlightFilter(value: FlightFilter, debounce: Boolean = false) {
        mutableState.update { it.copy(flightFilter = value) }
        try { FlightPredicate(value) } catch (_: IllegalArgumentException) {
            mutableState.update { it.copy(filterError = "名称表达式无效，保留上次有效结果") }
            return
        }
        mutableState.update { it.copy(filterError = null, appliedFilter = value) }
        queryFlights(if (debounce) 275L else 0L)
    }

    private fun queryFlights(wait: Long = 0L) {
        filterJob?.cancel()
        val sequence = ++filterSequence
        val revision = state.value.summary.revision
        val filter = state.value.appliedFilter
        if (active == "flights") { pageJob?.cancel(); epoch++ }
        pages.clear()
        positions["flights"] = Position()
        mutableState.update { it.copy(flights = emptyList(), flightPage = 0, filterToken = 0,
            matchCount = null, flightLoading = true, filterRevision = sequence) }
        if (!opened || !foreground || !state.value.ready) return
        filterJob = scope.launch {
            try {
                delay(wait)
                val result = withContext(worker) { queryFlightFilter(revision, FlightPredicate(filter)) }
                if (sequence != filterSequence || revision != state.value.summary.revision || !foreground) return@launch
                mutableState.update { it.copy(filterToken = result.token, matchCount = result.count,
                    protocols = result.protocols, flightLoading = active == "flights", failure = null) }
                if (active == "flights") page("flights", 0)
            } catch (e: CancellationException) { throw e
            } catch (_: CatalogStaleException) {
                if (sequence == filterSequence && foreground) reload()
            } catch (_: Exception) {
                if (sequence == filterSequence) mutableState.update {
                    it.copy(flightLoading = false, failure = "航班筛选读取失败，请重试")
                }
            }
        }
    }

    fun show(kind: String?) {
        active = kind
        pageJob?.cancel()
        epoch++
        mutableState.update { it.copy(towers = emptyList(), flights = emptyList()) }
        pages.trim()
        if (!opened) { start(); return }
        if (kind != null) page(kind, if (kind == "towers") state.value.towerPage else state.value.flightPage)
    }

    fun page(kind: String, index: Int) {
        if (!opened || !state.value.ready || !foreground || index < 0) return
        if (kind == "flights" && state.value.filterToken == 0L) return
        val current = positions.getValue(kind)
        val target = when (index) {
            current.index -> current
            current.index + 1 -> Position(index, current.last)
            current.index - 1 -> Position(index, current.first, backwards = true)
            0 -> Position()
            else -> return
        }
        pageJob?.cancel()
        val token = ++epoch
        val revision = state.value.summary.revision
        val filterToken = if (kind == "flights") state.value.filterToken else 0L
        pages.show(kind, index)
        fun publish(value: CatalogPage) {
            if (token != epoch || value.revision != state.value.summary.revision ||
                (kind == "flights" && value.filterToken != state.value.filterToken)) return
            positions[kind] = target.copy(
                first = if (kind == "towers") value.towers.firstOrNull()?.id ?: 0 else value.flights.firstOrNull()?.id ?: 0,
                last = if (kind == "towers") value.towers.lastOrNull()?.id ?: 0 else value.flights.lastOrNull()?.id ?: 0,
            )
            mutableState.update {
                if (kind == "towers") it.copy(towers = value.towers, towerPage = index, failure = null)
                else it.copy(flights = value.flights, flightPage = index, failure = null,
                    matchCount = value.matchCount, flightLoading = false)
            }
            if (kind == "flights" && (state.value.measurementsPending ||
                value.metricsRevision < state.value.summary.metricsRevision)) measurementsChanged()
        }
        pages.get(kind, index)?.let { publish(it); return }
        mutableState.update { if (kind == "towers") it.copy(towers = emptyList())
            else it.copy(flights = emptyList(), flightLoading = true) }
        pageJob = scope.launch {
            try {
                val result = withContext(worker) {
                    catalogJson.decodeFromJsonElement<CatalogPage>(call("page") {
                        put("kind", kind); put("anchor", target.anchor); put("backwards", target.backwards)
                        put("revision", revision); put("filterToken", filterToken)
                    })
                }
                if (token == epoch && result.revision == state.value.summary.revision &&
                    (kind != "flights" || result.filterToken == state.value.filterToken)) {
                    pages.put(kind, index, result); publish(result)
                }
            } catch (e: CancellationException) { throw e
            } catch (_: CatalogStaleException) {
                if (token == epoch) reload()
            } catch (_: Exception) {
                if (token == epoch) mutableState.update { it.copy(flightLoading = false, failure = "目录页面读取失败，请重试") }
            }
        }
    }

    fun measurementsChanged() {
        // 提交已确认；显示刷新使用目录自身生命周期，不能夺走任务 ID 或变成写入失败。
        scope.launch { refreshMeasurements() }
    }

    suspend fun refreshMeasurements() = withContext(Dispatchers.Main) {
        val sequence = ++measurementsSequence
        mutableState.update { it.copy(measurementsPending = true) }
        while (opened && foreground && state.value.ready) {
            val snapshot = state.value
            val pageEpoch = epoch
            val ids = snapshot.flights.map { it.id }
            if (ids.isEmpty() || snapshot.flightLoading) return@withContext
            val batch = try {
                withContext(worker) {
                    catalogJson.decodeFromJsonElement<CatalogMeasurementBatch>(call("measurements") {
                        put("nodeIds", JsonArray(ids.map { JsonPrimitive(it) })); put("revision", snapshot.summary.revision)
                    })
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                if (sequence != measurementsSequence) return@withContext
                val current = state.value
                if (pageEpoch != epoch || snapshot.summary.revision != current.summary.revision ||
                    snapshot.filterToken != current.filterToken || ids != current.flights.map { it.id }) continue
                mutableState.update {
                    it.copy(measurementsFailure = "结果摘要读取失败，请重新读取目录；已提交数据保留")
                }
                return@withContext
            }
            if (!foreground || sequence != measurementsSequence) return@withContext
            val current = state.value
            // 旧页响应不能抬高新页版本；页面/筛选变化后直接补读当前可见集合。
            if (pageEpoch != epoch || snapshot.summary.revision != current.summary.revision ||
                snapshot.filterToken != current.filterToken || ids != current.flights.map { it.id }) continue
            if (batch.revision != current.summary.revision || batch.metricsRevision < current.summary.metricsRevision) continue
            pages.invalidateMeasurements(batch.metricsRevision)
            val summaries = batch.nodes.associateBy { it.nodeId }
            mutableState.update { value ->
                value.copy(summary = value.summary.copy(metricsRevision = batch.metricsRevision),
                    flights = value.flights.map { it.copy(measurements = summaries.getValue(it.id)) },
                    measurementsPending = false, measurementsFailure = null)
            }
            return@withContext
        }
    }

    private suspend fun reload() {
        val token = ++reloadSequence
        val summary = withContext(worker) {
            catalogJson.decodeFromJsonElement<CatalogSummary>(call("summary"))
        }
        if (token != reloadSequence || summary.revision < state.value.summary.revision) return
        pageJob?.cancel()
        epoch++
        pages.clear()
        positions.keys.forEach { positions[it] = Position() }
        mutableState.update { it.copy(summary = summary, ready = true, failure = null,
            towers = emptyList(), flights = emptyList(), towerPage = 0, flightPage = 0) }
        queryFlights()
        if (active == "towers") page("towers", 0)
    }

    fun add(address: String, settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) {
        validateSubscriptionUrl(address)?.let { failure -> mutableState.update { it.copy(failure = failure) }; return }
        runOperation {
            val id = call("add") { put("address", address.trim()); put("name", "") }["id"]!!.jsonPrimitive.long
            refreshNow(id, settings, mirrors)
        }
    }

    fun refresh(id: Long, settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) = runOperation {
        refreshNow(id, settings, mirrors)
    }

    fun remove(id: Long) = scope.launch {
        if (!opened || !state.value.ready) return@launch
        try {
            withContext(worker) { call("remove") { put("id", id) } }
            reload()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { mutableState.update { it.copy(failure = "删除结果待确认，请重新读取目录") } }
    }

    // 不排队保留正文：全目录仅接受一个重型下载、解析与提交任务。
    private fun runOperation(block: suspend () -> Unit) {
        if (!opened || !state.value.ready || operation?.isActive == true) return
        mutableState.update { it.copy(busy = true, failure = null) }
        operation = scope.launch {
            try { withContext(worker) { block() }; reload()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.update { it.copy(failure = "目录更新未完成，请重新读取目录确认状态") }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    private suspend fun refreshNow(id: Long, settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) {
        CatalogRefreshService(importer).refresh(id, settings, mirrors)
    }

    fun release(background: Boolean) {
        pageJob?.cancel(); epoch++
        if (background) {
            foreground = false; pages.clear(); filterJob?.cancel(); filterSequence++; reloadSequence++
        } else pages.trim()
        if (background) mutableState.update { it.copy(towers = emptyList(), flights = emptyList()) }
    }

    fun resume() {
        foreground = true
        if (opened) scope.launch {
            try { reload() } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.update { it.copy(failure = "目录重新读取失败，请重试") } }
        } else start()
    }
}

public fun releaseCatalogPages() { appCatalog.release(background = true) }
public fun resumeCatalogPages() { appCatalog.resume() }
