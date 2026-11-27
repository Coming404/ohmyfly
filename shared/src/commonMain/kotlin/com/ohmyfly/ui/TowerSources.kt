package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.*
import com.ohmyfly.ui.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.Text

// 来源列表每页最多100条，重导入始终绑定独立来源身份，不覆盖合并塔台其他来源。
@Composable
internal fun TowerSources(parentId: Long, revision: Long, onImport: (CatalogSource) -> Unit) {
    var anchor by remember(parentId) { mutableStateOf(0L) }
    var sources by remember(parentId) { mutableStateOf<List<CatalogSource>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf(false) }
    LaunchedEffect(parentId, revision, anchor) {
        loading = true; failure = false
        try {
            sources = withContext(Dispatchers.IO) {
                catalogJson.decodeFromJsonElement<List<CatalogSource>>(catalogCall("sources") {
                    put("id", parentId); put("anchor", anchor)
                }.getValue("sources"))
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { failure = true
        } finally { loading = false }
    }
    if (loading) CatalogLoading()
    if (failure) CatalogNotice("来源读取失败，请重新打开")
    if (!loading && !failure) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sources, key = { it.id }) { source ->
            Column {
                Text(source.name.ifBlank { "来源 ${source.id}" })
                Text(catalogAge(source.lastSuccessAt))
                if (source.failure.isNotBlank()) CatalogNotice(source.failure)
                if (source.local) CatalogAction("重新导入", SettingsIcons.File) { onImport(source) }
            }
        }
        item {
            Row {
                if (anchor > 0) CatalogAction("回到首批", SettingsIcons.Retry) { anchor = 0 }
                if (sources.size == 100) CatalogAction("后续来源", DriftIcons.Nodes) { anchor = sources.last().id }
            }
        }
    }
}
