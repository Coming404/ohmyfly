// Mihomo YAML 导出提供复制入口；原生桥接返回数据库中已规范化的节点配置。
package com.ohmyfly.ui

import com.ohmyfly.catalog.appCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal expect fun copyCatalogText(text: String)
internal expect fun readCatalogText(): String?
internal expect suspend fun shareCatalogFile(text: String)

internal suspend fun exportCatalogYaml(ids: Set<Long>): String = withContext(Dispatchers.Default) {
    appCatalog.exportYaml(ids)
}
