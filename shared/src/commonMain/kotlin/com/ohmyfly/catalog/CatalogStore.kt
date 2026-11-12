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
    private val operations = CatalogOperations(scope, worker, { change -> mutableState.update(change) }, ::reload)
    private var epoch = 0L
    private var filterJob: Job? = null
    private var filterSequence = 0L
    private var reloadSequence = 0L
    private var measurementsSequence = 0L
    private val fetcher = SubscriptionFetcher(createSubscriptionClient())
    private fun batch(settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) =
        CatalogBatchRunner(settings, mirrors, fetcher, ::call) { change -> mutableState.update(change) }

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
        mutableState.update { it.copy(flights = emptyList(), flightWindow = emptyMap(), towers = emptyList(), towerWindow = emptyMap(), flightPage = 0, filterToken = 0,
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
        mutableState.update { it.copy(towers = emptyList(), flights = emptyList(), towerWindow = emptyMap(), flightWindow = emptyMap()) }
        pages.trim()
        if (!opened) { start(); return }
        if (kind != null) page(kind, if (kind == "towers") state.value.towerPage else state.value.flightPage)
    }

    fun updateTowerSearch(query: String) {
        mutableState.update { it.copy(towerSearch = query, towerMatchCount = null, towers = emptyList(),
            towerWindow = emptyMap(), flights = emptyList(), flightWindow = emptyMap(), towerPage = 0, towerLoading = true) }
        positions["towers"] = Position()
        pages.clear()
        page("towers", 0)
    }

    fun page(kind: String, index: Int) {
        if (!opened || !state.value.ready || !foreground || index < 0) return
        if (kind == "flights" && state.value.filterToken == 0L) return
        val current = positions.getValue(kind)
        val target = when {
            index == current.index -> current
            index == 0 -> Position()
            index > current.index -> Position(current.index + 1, current.last)
            else -> Position(current.index - 1, current.first, backwards = true)
        }
        pageJob?.cancel()
        val token = ++epoch
        val revision = state.value.summary.revision
        val filterToken = if (kind == "flights") state.value.filterToken else 0L
        val towerSearch = state.value.towerSearch
        val countTowers = kind == "towers" && state.value.towerMatchCount == null
        pages.show(kind, index)
        fun publish(value: CatalogPage) {
            if (token != epoch || value.revision != state.value.summary.revision ||
                (kind == "flights" && value.filterToken != state.value.filterToken)) return
            val first = if (kind == "towers") value.towers.firstOrNull()?.id ?: 0 else value.flights.firstOrNull()?.id ?: 0
            positions[kind] = Position(index = index, anchor = (first - 1).coerceAtLeast(0), first = first,
                last = if (kind == "towers") value.towers.lastOrNull()?.id ?: 0 else value.flights.lastOrNull()?.id ?: 0)
            mutableState.update {
                if (kind == "towers") it.copy(towers = value.towers, towerPage = index, failure = null,
                    towerMatchCount = if (countTowers) value.matchCount else it.towerMatchCount, towerLoading = false,
                    towerWindow = pages.window(kind).mapValues { it.value.towers })
                else it.copy(flights = value.flights, flightPage = index, failure = null,
                    flightWindow = pages.window(kind).mapValues { it.value.flights },
                    matchCount = value.matchCount, flightLoading = false)
            }
            if (kind == "flights" && (state.value.measurementsPending ||
                value.metricsRevision < state.value.summary.metricsRevision)) measurementsChanged()
        }
        pages.get(kind, index)?.let { publish(it); return }
        mutableState.update { if (kind == "towers") it.copy(towerLoading = true)
            else it.copy(flights = emptyList(), flightLoading = true) }
        pageJob = scope.launch {
            try {
                if (kind == "towers" && towerSearch.isNotBlank()) delay(275)
                val result = withContext(worker) {
                    loadWindowPage(kind, revision, filterToken, towerSearch, countTowers,
                        target.index, index, target.anchor, target.backwards)
                }
                if (token == epoch && result.revision == state.value.summary.revision &&
                    (kind != "flights" || result.filterToken == state.value.filterToken)) {
                    pages.put(kind, index, result); publish(result)
                }
            } catch (e: CancellationException) { throw e
            } catch (_: CatalogStaleException) {
                if (token == epoch) reload()
            } catch (_: Exception) {
                if (token == epoch) mutableState.update { it.copy(flightLoading = false, towerLoading = false, failure = "目录页面读取失败，请重试") }
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
            val flights = current.flights.map { it.copy(measurements = summaries.getValue(it.id)) }
            pages.put("flights", current.flightPage, CatalogPage(revision = current.summary.revision,
                filterToken = current.filterToken, matchCount = current.matchCount ?: 0,
                metricsRevision = batch.metricsRevision, flights = flights))
            mutableState.update { value ->
                value.copy(summary = value.summary.copy(metricsRevision = batch.metricsRevision),
                    flights = flights, flightWindow = pages.window("flights").mapValues { it.value.flights },
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
            towers = emptyList(), flights = emptyList(), towerPage = 0, flightPage = 0,
            towerMatchCount = null, towerLoading = true,
            towerWindow = emptyMap(), flightWindow = emptyMap()) }
        queryFlights()
        if (active == "towers") page("towers", 0)
    }

    fun add(address: String, name: String, settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) {
        val inputs = subscriptionAddresses(address, settings.addressLimit).map { CatalogImportSource(it, name) }
        importSources(inputs, name, false, settings, mirrors)
    }

    fun importBody(body: SubscriptionBody, settings: SubscriptionSettings, replaceId: Long? = null) {
        importSources(listOf(CatalogImportSource(newBodyAddress(body.kind), body.name) { body }), body.name,
            false, settings, SubscriptionMirrorSettings(), replaceId)
    }

    fun importSources(inputs: List<CatalogImportSource>, name: String, merge: Boolean,
        settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings, replaceId: Long? = null) = runOperation {
        batch(settings, mirrors).importSources(inputs, merge, name, replaceId)
        check(state.value.taskFailed == 0) { "${state.value.taskFailed} 个来源导入失败，已保留上次成功数据" }
    }

    fun cancelOperation() { operations.cancelAll() }

    suspend fun syncAll(settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) = withContext(Dispatchers.Main) {
        runOperation {
            batch(settings, mirrors).sync(0) { state.value.pendingRemoval }
            check(state.value.taskFailed == 0) { "${state.value.taskFailed} 个来源同步失败，已保留上次成功数据" }
        }.await()
    }

    fun refresh(id: Long, settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) = runOperation {
        batch(settings, mirrors).sync(id) { state.value.pendingRemoval }
        check(state.value.taskFailed == 0) { "${state.value.taskFailed} 个来源同步失败，已保留上次成功数据" }
    }

    fun postponeRemoval(id: Long) {
        mutableState.update { it.copy(pendingRemoval = it.pendingRemoval + id) }
    }

    fun cancelRemoval(id: Long) {
        mutableState.update { it.copy(pendingRemoval = it.pendingRemoval - id) }
    }

    suspend fun refreshView() = withContext(Dispatchers.Main) {
        if (!opened) { start(); initialization?.join() } else reload()
        filterJob?.join()
        pageJob?.join()
        state.value.failure?.let { error(it) }
    }

    fun remove(id: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) = scope.launch {
        if (!opened || !state.value.ready) {
            cancelRemoval(id)
            onResult(false, "目录尚未就绪，删除未执行")
            return@launch
        }
        try {
            withContext(worker) { call("remove") { put("id", id) } }
        } catch (e: CancellationException) {
            cancelRemoval(id)
            onResult(false, "删除已取消，请重新读取目录确认")
            throw e
        } catch (_: Exception) {
            cancelRemoval(id)
            onResult(false, "删除结果待确认，请重新读取目录")
            return@launch
        }
        cancelRemoval(id)
        onResult(true, "塔台已删除")
        try { reload()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { onResult(false, "塔台已删除，列表重读失败，请下拉重新读取") }
    }

    private fun runOperation(block: suspend () -> Unit): Deferred<Unit> {
        check(opened && state.value.ready) { "目录尚未就绪，请稍后再试" }
        return operations.submit(block)
    }

    fun release(background: Boolean) {
        pageJob?.cancel(); epoch++
        if (background) {
            foreground = false; pages.clear(); filterJob?.cancel(); filterSequence++; reloadSequence++
        } else pages.trim()
        if (background) mutableState.update { it.copy(towers = emptyList(), flights = emptyList(), towerWindow = emptyMap(), flightWindow = emptyMap()) }
        else mutableState.update { it.copy(
            towerWindow = pages.window("towers").mapValues { entry -> entry.value.towers },
            flightWindow = pages.window("flights").mapValues { entry -> entry.value.flights },
            towers = pages.window("towers")[it.towerPage]?.towers.orEmpty(),
            flights = pages.window("flights")[it.flightPage]?.flights.orEmpty()) }
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
