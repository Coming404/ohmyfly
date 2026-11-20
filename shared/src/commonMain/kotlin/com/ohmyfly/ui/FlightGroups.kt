// 固定状态与自定义分组共用全库筛选，重复点击已选自定义组进入管理。
package com.ohmyfly.ui

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
    // 标题固定，分组随航班列表滚动；默认分组移动后不再保留在原组。
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 分组独立横向滚动，右侧添加按钮保持强调。
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
        Spacer(Modifier.width(8.dp))
        InlineChromeIconButton("新建分组", MirrorIcons.Add,
            onClick = { creating = true }, enabled = catalog.ready, tint = MiuixTheme.colorScheme.primary)
    }

    if (creating || editing != null) FlightGroupEditor(editing, catalog.groups.groups) {
        creating = false; editing = null
    }

}

@Composable
private fun GroupChip(title: String, count: Long, active: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val duration = if (LocalAppMotionAllowed.current) 160 else 0
    val background by androidx.compose.animation.animateColorAsState(
        if (active) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainer.copy(alpha = 0.55f),
        androidx.compose.animation.core.tween(duration), label = "分组背景")
    val foreground by androidx.compose.animation.animateColorAsState(
        if (active) colors.primary else colors.onSurfaceVariantSummary,
        androidx.compose.animation.core.tween(duration), label = "分组文字")
    // 缩小可见胶囊但保留 44 dp 触控高度；未选项不以粗体或边框抢占注意力。
    Box(Modifier.heightIn(min = ChromeMetrics.ControlSize)
        .clickable(role = Role.Tab, onClick = hapticClick(onClick)).semantics { selected = active },
        contentAlignment = Alignment.Center) {
        Row(Modifier.clip(shape).background(background)
            .heightIn(min = 30.dp).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = foreground, fontSize = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            Text(count.toString(), color = foreground, fontSize = 11.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
