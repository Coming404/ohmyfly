package com.ohmyfly.ui

import androidx.compose.runtime.*
import com.ohmyfly.catalog.CatalogImportSource
import com.ohmyfly.catalog.CatalogTower
import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.subscription.*

// 输入模式切换保留各来源草稿；提交后捕获值，后台不再依赖抽屉生命周期。
@Stable
internal class TowerImportDraft(target: CatalogTower?) {
    var mode by mutableStateOf(if (target?.sourceKind == "text") "text" else if (target != null) "file" else "url")
    var urls by mutableStateOf("")
    var text by mutableStateOf("")
    var name by mutableStateOf(target?.name.orEmpty())
    var merge by mutableStateOf(false)
    var files by mutableStateOf<List<SubscriptionFileSelection>>(emptyList())
    var reading by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)
    val hasInput: Boolean get() = urls.isNotBlank() || text.isNotBlank() || files.isNotEmpty()

    fun inputs(settings: SubscriptionSettings, target: CatalogTower?): List<CatalogImportSource> {
        val result = mutableListOf<CatalogImportSource>()
        val title = name.trim()
        require(title.length <= 256) { "名称最多 256 字符" }
        if (urls.isNotBlank()) subscriptionAddresses(urls, settings.addressLimit).forEach {
            result += CatalogImportSource(it, "")
        }
        require(files.size <= if (target == null) settings.fileLimit else 1) { "所选文件超过数量上限" }
        files.forEach { file ->
            result += CatalogImportSource(newBodyAddress("file"), file.name) {
                val loaded = file.read(settings.maxMiB * 1024 * 1024)
                SubscriptionBody(loaded.name, loaded.content, "file")
            }
        }
        if (text.isNotBlank()) {
            val content = text
            validateSubscriptionBody(content, settings.maxMiB)
            result += CatalogImportSource(newBodyAddress("text"), "文本导入") { SubscriptionBody("文本导入", content, "text") }
        }
        require(result.isNotEmpty()) { "请添加来源" }
        require(target == null || result.size == 1) { "重新导入需选择一个对应来源" }
        if (title.isEmpty() || merge && target == null) return result
        return result.mapIndexed { index, source ->
            val sourceName = if (result.size == 1) title else "$title ${index + 1}"
            val read = source.read
            if (read == null) source.copy(name = sourceName)
            else source.copy(name = sourceName, read = { read().copy(name = sourceName) })
        }
    }
}
