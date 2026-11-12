package com.ohmyfly.catalog

/** 所有列表共享预算，状态只引用缓存中的当前页，不累加 ID 或节点。 */
private const val CatalogCacheBudgetBytes = 50 * 1024 * 1024

internal class CatalogPages {
    private data class Key(val kind: String, val index: Int)
    private data class Entry(val page: CatalogPage, val bytes: Int)
    private val entries = linkedMapOf<Key, Entry>()
    private var bytes = 0
    private var visible: Key? = null

    fun show(kind: String, index: Int) { visible = Key(kind, index) }

    fun get(kind: String, index: Int): CatalogPage? {
        val key = Key(kind, index)
        val entry = entries.remove(key) ?: return null
        entries[key] = entry
        return entry.page
    }

    fun put(kind: String, index: Int, page: CatalogPage) {
        val key = Key(kind, index)
        val size = 256 + page.towers.sumOf { 256 + it.name.length * 2 + it.failure.length * 2 } +
            page.flights.sumOf { 192 + 2 * (it.name.length + it.server.length + it.type.length) + measurementBytes(it.measurements) }
        entries.remove(key)?.let { bytes -= it.bytes }
        if (size > CatalogCacheBudgetBytes) return
        entries[key] = Entry(page, size)
        bytes += size
        while (bytes > CatalogCacheBudgetBytes || entries.keys.count { it.kind == kind } > 5) {
            val sameList = entries.keys.count { it.kind == kind } > 5
            val victim = entries.keys.first { it != visible && (!sameList || it.kind == kind) }
            bytes -= entries.remove(victim)!!.bytes
        }
    }

    fun trim() {
        val retained = visible?.let { key -> entries[key]?.let { key to it } }
        clear()
        retained?.let { (key, entry) -> entries[key] = entry; bytes = entry.bytes; visible = key }
    }

    fun invalidateMeasurements(version: Long) {
        entries.keys.filter { it.kind == "flights" && entries.getValue(it).page.metricsRevision < version }
            .forEach { bytes -= entries.remove(it)!!.bytes }
    }

    private fun measurementBytes(value: CatalogMeasurements): Int =
        256 + (value.latestRun?.let { 192 + it.reason.length * 2 } ?: 0) +
            value.metrics.values.sumOf { metric ->
                listOfNotNull(metric.latest, metric.valid).sumOf { 160 + 2 * (it.reason.length + it.status.length + it.kind.length) }
            } + value.platforms.sumOf { 128 + 2 * (it.platform.length + it.status.length + it.region.length) }

    fun clear() { entries.clear(); bytes = 0; visible = null }
}
