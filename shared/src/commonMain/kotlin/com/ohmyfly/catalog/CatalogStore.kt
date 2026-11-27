package com.ohmyfly.catalog

import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.logging.logger
import com.ohmyfly.subscription.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal var trendWriteJob: Job? = null
    internal var lastTrendObservation: CatalogSummary? = null
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
    internal val operations = CatalogOperations(scope, worker, { change -> mutableState.update(change) }) { reload() }
    internal var runningBatch: CatalogBatchRunner? = null
    private var epoch = 0L
    private var filterJob: Job? = null
    private var filterSequence = 0L
    private var reloadSequence = 0L
    private var measurementsSequence = 0L
    internal val syncResults = Channel<CatalogSyncResult>(Channel.BUFFERED)
    private val fetcher = SubscriptionFetcher(createSubscriptionClient())
    internal fun batch(settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings) =
        CatalogBatchRunner(settings, mirrors, fetcher, ::call,
            changed = { withContext(Dispatchers.Main) { reload(); pageJob?.join() } },
            finished = {
                val result = state.value
                logger.debug { "[塔台航班] 批次结束：${result.taskCompleted} 个来源，${result.taskFailed} 个失败；当前 ${result.summary.towers} 个塔台，${result.summary.flights} 个航班" }
                syncResults.trySend(CatalogSyncResult(result.taskCompleted, result.taskFailed, result.taskMessages))
            },
        ) { change -> mutableState.update(change) }

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
            } catch (error: Exception) {
                val reason = logCatalogFailure("打开塔台与航班数据", error)
                mutableState.update { it.copy(ready = false, failure = "塔台与航班数据打开失败：$reason") }
            }
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
        val preserveResults = state.value.matchCount != null
        queryFlights(if (debounce) 275L else 0L, preserveResults = preserveResults)
    }

    private fun queryFlights(wait: Long = 0L, preserveResults: Boolean = false) {
        filterJob?.cancel()
        val sequence = ++filterSequence
        val revision = state.value.summary.revision
        val filter = state.value.appliedFilter
        if (active == "flights") { pageJob?.cancel(); epoch++ }
        if (!preserveResults) {
            pages.clear("flights")
            positions["flights"] = Position()
        }
        mutableState.update { current ->
            if (preserveResults) current.copy(filterToken = 0, flightLoading = false, filterRevision = sequence)
            else current.copy(flights = emptyList(), flightWindow = emptyMap(), flightPage = 0, filterToken = 0,
                matchCount = null, flightLoading = true, filterRevision = sequence)
        }
        if (!opened || !foreground || !state.value.ready) return
        filterJob = scope.launch {
            try {
                delay(wait)
                val result = withContext(worker) { queryFlightFilter(revision, filter) }
                if (sequence != filterSequence || revision != state.value.summary.revision || !foreground) return@launch
                // 新过滤令牌不能复用旧窗口，否则命中旧缓存后被令牌校验丢弃，列表不再加载。
                pages.clear("flights")
                positions["flights"] = Position()
                mutableState.update { it.copy(filterToken = result.token, matchCount = result.count,
                    flightPage = 0, flightWindow = emptyMap(),
                    groups = result.groups, protocols = result.protocols, flightLoading = active == "flights", failure = null) }
                if (active == "flights") page("flights", 0)
            } catch (e: CancellationException) { throw e
            } catch (_: CatalogStaleException) {
                if (sequence == filterSequence && foreground) reload()
            } catch (error: Exception) {
                val reason = logCatalogFailure("航班筛选", error)
                if (sequence == filterSequence) mutableState.update {
                    it.copy(flightLoading = false, failure = "航班筛选读取失败：$reason")
                }
            }
        }
    }

    fun show(kind: String?) {
        active = kind
        pageJob?.cancel()
        epoch++
        // 正常切页保留两边的窗口；预算淘汰与系统内存压力才回收缓存。
        if (!opened) { start(); return }
        if (kind == "flights" && state.value.filterToken == 0L && filterJob?.isActive != true) queryFlights()
        if (kind != null) page(kind, if (kind == "towers") state.value.towerPage else state.value.flightPage)
    }

    fun updateTowerSearch(query: String) {
        mutableState.update { it.copy(towerSearch = query, towerMatchCount = null, towers = emptyList(),
            towerWindow = emptyMap(), towerPage = 0, towerLoading = true) }
        positions["towers"] = Position()
        pages.clear("towers")
        page("towers", 0)
    }

    fun page(kind: String, index: Int) {
        if (active != kind || !opened || !state.value.ready || !foreground || index < 0) return
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
                // 状态只引用 LRU 仍保留的页，另一标签被预算淘汰时同步释放其旧引用。
                val retained = it.copy(towerWindow = pages.window("towers").mapValues { page -> page.value.towers },
                    flightWindow = pages.window("flights").mapValues { page -> page.value.flights },
                    towers = pages.window("towers")[it.towerPage]?.towers.orEmpty(),
                    flights = pages.window("flights")[it.flightPage]?.flights.orEmpty())
                if (kind == "towers") retained.copy(towers = value.towers, towerPage = index, failure = null,
                    towerMatchCount = if (countTowers) value.matchCount else it.towerMatchCount, towerLoading = false)
                else retained.copy(flights = value.flights, flightPage = index, failure = null,
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
            } catch (error: Exception) {
                val listName = if (kind == "towers") "塔台" else "航班"
                val reason = logCatalogFailure("读取${listName}列表", error)
                if (token == epoch) mutableState.update { it.copy(flightLoading = false, towerLoading = false, failure = "${listName}列表读取失败：$reason") }
            }
        }
    }

    fun measurementsChanged() {
        // 提交已确认；显示刷新使用目录自身生命周期，不能夺走任务 ID 或变成写入失败。
        if (state.value.appliedFilter.status != "all") {
            queryFlights()
            return
        }
        scope.launch {
            refreshMeasurements()
            val snapshot = state.value
            if (snapshot.filterToken == 0L || !foreground) return@launch
            try {
                val groups = withContext(worker) {
                    catalogJson.decodeFromJsonElement<CatalogGroups>(call("groups") {
                        put("filterTicket", buildJsonObject {
                            put("token", snapshot.filterToken); put("revision", snapshot.summary.revision)
                        })
                    })
                }
                if (state.value.filterToken == snapshot.filterToken && state.value.summary.revision == groups.revision) {
                    mutableState.update { it.copy(groups = groups) }
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(measurementsFailure = "状态计数读取失败，请刷新塔台与航班数据") }
            }
        }
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
                    it.copy(measurementsFailure = "结果摘要读取失败，请刷新塔台与航班数据；已提交数据保留")
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

    private suspend fun reload(invalidate: Boolean = false) {
        val token = ++reloadSequence
        val summary = withContext(worker) {
            catalogJson.decodeFromJsonElement<CatalogSummary>(call("summary"))
        }
        if (token != reloadSequence || summary.revision < state.value.summary.revision) return
        recordTrend(summary)
        val current = state.value
        if (!invalidate && current.ready && current.summary.revision == summary.revision) {
            mutableState.update { it.copy(summary = summary, ready = true, failure = null) }
            if (current.filterToken == 0L && filterJob?.isActive != true) queryFlights()
            active?.let { kind -> page(kind, if (kind == "towers") current.towerPage else current.flightPage) }
            return
        }
        val preserveFlightResults = current.filterToken != 0L && current.matchCount != null
        pageJob?.cancel()
        epoch++
        pages.clear()
        positions.keys.forEach { positions[it] = Position() }
        mutableState.update { it.copy(summary = summary, ready = true, failure = null,
            towers = emptyList(), flights = if (preserveFlightResults) current.flights else emptyList(),
            towerPage = 0, flightPage = 0, towerMatchCount = null, towerLoading = true,
            matchCount = current.matchCount.takeIf { preserveFlightResults }, filterToken = 0,
            flightLoading = !preserveFlightResults, towerWindow = emptyMap(),
            flightWindow = if (preserveFlightResults) current.flightWindow else emptyMap()) }
        queryFlights(preserveResults = preserveFlightResults)
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
        runBatch(settings, mirrors) { it.importSources(inputs, merge, name, replaceId) }
        check(state.value.taskFailed == 0) { "${state.value.taskFailed} 个来源导入失败，已保留上次成功数据" }
    }

    suspend fun exportYaml(ids: Set<Long>): String = withContext(worker) {
        require(ids.isNotEmpty()) { "请先选择航班" }
        // 每批只跨桥接传输一百个配置，最终保持一个 proxies 集合。
        buildString {
            append("proxies:\n")
            ids.sorted().chunked(100).forEach { batch ->
                val yaml = call("exportYAML") {
                    put("nodeIds", JsonArray(batch.map { JsonPrimitive(it) }))
                }.getValue("yaml").jsonPrimitive.content
                append(yaml.removePrefix("proxies:\n"))
            }
        }
    }

    // 组操作使用目录生命周期；先确认写入，再独立报告列表重读失败。
    fun changeGroup(action: String, id: Long = 0, name: String = "", nodeIds: Set<Long> = emptySet(),
        onResult: (Boolean, String) -> Unit) = scope.launch {
        if (!opened || !state.value.ready) {
            onResult(false, "塔台与航班数据尚未准备好")
            return@launch
        }
        try {
            withContext(worker) {
                call(action) {
                    put("groupId", id); put("name", name)
                    put("nodeIds", JsonArray(nodeIds.map { JsonPrimitive(it) }))
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            onResult(false, "分组操作未确认：${logCatalogFailure("变更分组 $action", e)}")
            return@launch
        }
        if (action == "groupDelete" && state.value.flightFilter.groupId == id) {
            mutableState.update { it.copy(flightFilter = it.flightFilter.copy(groupId = 0),
                appliedFilter = it.appliedFilter.copy(groupId = 0)) }
        }
        try { refreshView(invalidate = true)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            val reason = logCatalogFailure("分组保存后刷新列表", e)
            mutableState.update { it.copy(failure = "分组已保存，列表重读失败：$reason") }
        }
        onResult(true, when (action) {
            "groupCreate" -> "分组已添加"
            "groupRename" -> "分组已重命名"
            "groupDelete" -> "分组已删除，航班已移至全部"
            else -> "节点已移动"
        })
    }

    // 写入确认与目录重读分别报告，不把刷新失败误报成写入失败。
    fun changeNodes(ids: Set<Long>, mode: String, onResult: (Boolean, String) -> Unit) = scope.launch {
        try {
            withContext(worker) { call("nodeChange") {
                put("nodeIds", JsonArray(ids.map { JsonPrimitive(it) })); put("mode", mode)
            } }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { onResult(false, "操作未确认：${logCatalogFailure("变更节点 $mode", e)}"); return@launch }
        try { refreshView(invalidate = true)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { mutableState.update { it.copy(failure = "操作已保存，列表重读失败：${logCatalogFailure("节点变更后刷新", e)}") } }
        onResult(true, when (mode) { "enable" -> "已启用"; "disable" -> "已停用"; else -> "已删除" })
    }

    // 批量删除结束后仅重读一次，失败保留未完成选择供重试。
    fun removeTowers(ids: Set<Long>, onResult: (Boolean, String) -> Unit) = scope.launch {
        try {
            withContext(worker) { call("removeTowers") {
                put("nodeIds", JsonArray(ids.map { JsonPrimitive(it) }))
            } }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { onResult(false, "塔台删除未确认，请刷新塔台与航班数据"); return@launch }
        onResult(true, "所选塔台已删除")
        try { reload()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { mutableState.update { it.copy(failure = "删除已完成，列表重读失败") } }
    }

    fun updateTower(id: Long, address: String, name: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) = scope.launch {
        try {
            withContext(worker) { call("updateTower") { put("id", id); put("address", address); put("name", name) } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onResult(false, "塔台保存失败，请检查名称和地址")
            return@launch
        }
        onResult(true, "塔台已保存")
        try { reload() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableState.update { it.copy(failure = "塔台已保存，列表重读失败") } }
    }

    fun postponeRemoval(id: Long) {
        mutableState.update { it.copy(pendingRemoval = it.pendingRemoval + id) }
    }

    fun cancelRemoval(id: Long) {
        mutableState.update { it.copy(pendingRemoval = it.pendingRemoval - id) }
    }

    suspend fun refreshView(invalidate: Boolean = false) = withContext(Dispatchers.Main) {
        if (!opened) { start(); initialization?.join() } else reload(invalidate)
        filterJob?.join()
        pageJob?.join()
        state.value.failure?.let { error(it) }
    }

    fun remove(id: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) = scope.launch {
        if (!opened || !state.value.ready) {
            cancelRemoval(id)
            onResult(false, "塔台与航班数据尚未准备好，删除未执行")
            return@launch
        }
        try {
            withContext(worker) { call("remove") { put("id", id) } }
        } catch (e: CancellationException) {
            cancelRemoval(id)
            onResult(false, "删除已取消，请刷新塔台与航班数据确认")
            throw e
        } catch (_: Exception) {
            cancelRemoval(id)
            onResult(false, "删除结果待确认，请刷新塔台与航班数据")
            return@launch
        }
        cancelRemoval(id)
        onResult(true, "塔台已删除")
        try { reload()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { onResult(false, "塔台已删除，列表重读失败，请下拉重新读取") }
    }

    internal fun runOperation(key: String? = null, block: suspend () -> Unit): Deferred<Unit> {
        check(opened && state.value.ready) { "塔台与航班数据尚未准备好，请稍后再试" }
        return operations.submit(key, block)
    }

    fun release(background: Boolean) {
        pageJob?.cancel(); epoch++
        if (background) {
            foreground = false; filterJob?.cancel(); filterSequence++; reloadSequence++
        } else pages.trim()
        if (!background) mutableState.update { it.copy(
            towerWindow = pages.window("towers").mapValues { entry -> entry.value.towers },
            flightWindow = pages.window("flights").mapValues { entry -> entry.value.flights },
            towers = pages.window("towers")[it.towerPage]?.towers.orEmpty(),
            flights = pages.window("flights")[it.flightPage]?.flights.orEmpty()) }
    }

    fun resume() {
        foreground = true
        if (opened) scope.launch {
            try { reload() } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.update { it.copy(failure = "塔台与航班数据刷新失败，请重试") } }
        } else start()
    }
}

public fun releaseCatalogPages() { appCatalog.release(background = true) }
public fun resumeCatalogPages() { appCatalog.resume() }
