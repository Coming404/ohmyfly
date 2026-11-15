// 固定状态与自定义分组共用全库筛选，重复点击已选自定义组进入管理。
package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.*
import com.ohmyfly.ui.settings.MirrorIcons
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FlightGroups(catalog: CatalogState) {
    var editing by remember { mutableStateOf<CatalogGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    val filter = catalog.flightFilter
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("all" to "全部", "succeeded" to "起飞成功", "failed" to "起飞失败", "none" to "未起飞").forEach { (status, title) ->
                GroupChip(title, catalog.groups.counts[status], filter.groupId == -1L && filter.status == status) {
                    appCatalog.updateFlightFilter(filter.copy(groupId = -1, status = status))
                }
            }
            catalog.groups.groups.forEach { group ->
                GroupChip(group.name, group.count, filter.groupId == group.id) {
                    if (filter.groupId == group.id) editing = group
                    else appCatalog.updateFlightFilter(filter.copy(groupId = group.id, status = "all"))
                }
            }
            IconButton(onClick = hapticClick { creating = true }, enabled = catalog.ready, modifier = Modifier.size(44.dp)) {
                Icon(MirrorIcons.Add, "新建分组")
            }
        }
    }

    if (creating || editing != null) FlightGroupEditor(editing, catalog.groups.groups) {
        creating = false; editing = null
    }

}

@Composable
private fun GroupChip(title: String, count: Long?, active: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(24.dp))
        .background(if (active) colors.primary.copy(alpha = 0.12f) else colors.surface)
        .clickable(role = Role.Tab, onClick = hapticClick(onClick)).semantics { selected = active }
        .heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = if (active) colors.primary else colors.onSurface, style = catalogInputStyle())
        count?.let { Text(it.toString(), color = colors.onSurfaceVariantSummary, style = catalogInputStyle()) }
    }
}
