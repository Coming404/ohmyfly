// 塔台状态与单选操作菜单；复杂操作通过各自的 Miuix 抽屉继续。
package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.catalog.*
import com.ohmyfly.ui.settings.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 状态独立配色，正常使用绿色对勾，同步中使用琥珀色刷新标记。 */
@Composable
internal fun TowerStatusBadge(tower: CatalogTower, loading: Boolean) {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val color = when {
        loading -> if (dark) Color(0xFFFFD180) else Color(0xFF996000)
        tower.failure.isNotBlank() -> if (dark) Color(0xFFFF8282) else Color(0xFFC62828)
        tower.lastSuccessAt != null -> if (dark) Color(0xFF81C784) else Color(0xFF237C45)
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val text = when {
        loading -> "同步中"
        tower.failure.isNotBlank() -> "异常"
        tower.lastSuccessAt != null -> "正常"
        else -> "待同步"
    }
    val icon = when {
        loading -> SettingsIcons.Retry
        tower.failure.isNotBlank() -> MirrorIcons.Alert
        tower.lastSuccessAt != null -> DriftIcons.Check
        else -> SettingsIcons.Timer
    }
    Row(Modifier.background(color.copy(alpha = if (dark) 0.22f else 0.13f), RoundedCornerShape(24.dp))
        .padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** 菜单自身负责单选关闭，编辑、结果、来源与删除各自进入独立抽屉。 */
@Composable
internal fun TowerMoreMenu(tower: CatalogTower, loading: Boolean, revision: Long, modifier: Modifier,
    onEdit: () -> Unit, onRemove: () -> Unit, onSourceImport: (CatalogSource) -> Unit) {
    var panel by remember(tower.id) { mutableStateOf("") }
    val messages = LocalAppMessages.current
    OverlayIconDropdownMenu(entry = DropdownEntry(items = buildList {
        add(DropdownItem(text = "编辑塔台", enabled = !loading, onClick = onEdit,
            icon = { Icon(SettingsIcons.Rename, null, it) }))
        if (tower.sourceKind == "http" && tower.address.isNotBlank()) {
            add(DropdownItem(text = "复制订阅地址", onClick = {
                copyCatalogText(tower.address)
                messages.show(AppMessageKind.Success, "已复制订阅地址")
            }, icon = { Icon(SettingsIcons.Copy, null, it) }))
        }
        add(DropdownItem(text = "导入结果", onClick = { panel = "result" },
            icon = { Icon(SettingsIcons.Hint, null, it) }))
        if (tower.sourceKind == "merged") add(DropdownItem(text = "管理来源", onClick = { panel = "sources" },
            icon = { Icon(DriftIcons.Subscriptions, null, it) }))
        add(DropdownItem(text = "删除塔台", enabled = !loading, onClick = { panel = "delete" },
            icon = { Icon(MirrorIcons.Trash, null, it, tint = MiuixTheme.colorScheme.error) }))
    }), modifier = modifier, minWidth = 0.dp, minHeight = 44.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(MirrorIcons.More, null, Modifier.size(19.dp), tint = MiuixTheme.colorScheme.primary)
            Text("更多", color = MiuixTheme.colorScheme.primary, autoSize = CatalogTextSizing.CompactButton, maxLines = 1)
        }
    }
    if (panel == "result") TowerImportResultSheet(tower) { panel = "" }
    if (panel == "sources") OverlayBottomSheet(show = true, title = "管理来源", insideMargin = catalogSheetMargin(),
        onDismissRequest = { panel = "" }) {
        TowerSources(tower.id, revision) { source -> panel = ""; onSourceImport(source) }
    }
    if (panel == "delete") OverlayBottomSheet(show = true, title = "删除塔台", insideMargin = catalogSheetMargin(),
        onDismissRequest = { panel = "" }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tower.name, style = catalogInputStyle())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CatalogTextButton("取消", onClick = { panel = "" }, modifier = Modifier.weight(1f))
                CatalogAction("确认删除", MirrorIcons.Trash, Modifier.weight(1f), destructive = true) {
                    panel = ""; onRemove()
                }
            }
        }
    }
}
