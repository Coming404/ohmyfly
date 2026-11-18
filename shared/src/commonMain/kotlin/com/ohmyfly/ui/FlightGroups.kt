// 固定状态与自定义分组共用全库筛选，重复点击已选自定义组进入管理。
package com.ohmyfly.ui

import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
    // 标题由 Scaffold 固定，默认分组移动后不再保留在原组。
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 分组独立横向滚动；分隔线与添加按钮固定在右侧。
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GroupChip("全部", catalog.groups.counts["default"] ?: 0, filter.groupId == 0L && filter.status == "all") {
                appCatalog.updateFlightFilter(filter.copy(groupId = 0, status = "all"))
            }
            listOf("succeeded" to "起飞成功", "failed" to "起飞失败", "none" to "未起飞").forEach { (status, title) ->
                GroupChip(title, catalog.groups.counts[status] ?: 0, filter.groupId == -1L && filter.status == status) {
                    appCatalog.updateFlightFilter(filter.copy(groupId = -1, status = status))
                }
            }
            catalog.groups.groups.forEach { group ->
                GroupChip(group.name, group.count, filter.groupId == group.id) {
                    if (filter.groupId == group.id) editing = group
                    else appCatalog.updateFlightFilter(filter.copy(groupId = group.id, status = "all"))
                }
            }
        }
        VerticalDivider(Modifier.padding(horizontal = 8.dp).height(24.dp))
        IconButton(onClick = hapticClick { creating = true }, enabled = catalog.ready) {
            Icon(MirrorIcons.Add, "新建分组")
        }
    }

    if (creating || editing != null) FlightGroupEditor(editing, catalog.groups.groups) {
        creating = false; editing = null
    }

}

@Composable
private fun GroupChip(title: String, count: Long, active: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(Modifier.clip(shape)
        .background(if (active) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainer)
        .border(0.5.dp, if (active) colors.primary.copy(alpha = 0.5f) else colors.outline, shape)
        .clickable(role = Role.Tab, onClick = hapticClick(onClick)).semantics { selected = active }
        .heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = if (active) colors.primary else colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(count.toString(), color = if (active) colors.primary else colors.onSurfaceVariantSummary,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
