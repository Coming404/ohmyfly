// 航班筛选由目录会话持有，抽屉关闭不改变条件，设置页导入规则保持独立。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.FlightFilter
import com.ohmyfly.catalog.appCatalog
import com.ohmyfly.ui.settings.SettingsIcons
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

@Composable
internal fun FlightFilterAction(onClick: () -> Unit) {
    val state by appCatalog.state.collectAsState()
    CatalogToolbarAction(
        label = if (state.appliedFilter.active) "航班筛选（已启用）" else "航班筛选",
        icon = SettingsIcons.Filter,
        onClick = onClick,
    )
}

@Composable
internal fun FlightFilterSheet(show: Boolean, onClose: () -> Unit) {
    val state by appCatalog.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(show) { if (show) expanded = false }
    val height = LocalWindowInfo.current.containerDpSize.height
    val filter = state.flightFilter
    OverlayBottomSheet(surface = appBottomSheetSurface(),
        show = show, title = "航班筛选", onDismissRequest = onClose, insideMargin = catalogSheetMargin(),
        startAction = { CatalogTextButton(text = "重置", onClick = hapticClick { appCatalog.updateFlightFilter(FlightFilter()) }) },
        endAction = { CatalogTextButton(text = "关闭", onClick = hapticClick(onClose)) }, cornerRadius = AppCorners.Sheet,
    ) {
        Column(Modifier.fillMaxWidth().height(height * if (expanded) 0.72f else 0.42f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(when {
                    !state.ready -> "正在读取航班…"
                    state.matchCount == null -> if (state.failure != null) "筛选未完成" else "正在筛选…"
                    else -> "匹配 ${state.matchCount} 个航班"
                }, modifier = Modifier.weight(1f), style = catalogInputStyle())
                CatalogTextButton(text = if (expanded) "收起" else "展开", onClick = hapticClick { expanded = !expanded })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().toggleable(filter.enabled, role = Role.Switch,
                    onValueChange = hapticChange { appCatalog.updateFlightFilter(filter.copy(enabled = it)) })
                    .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("启用筛选", Modifier.weight(1f), style = catalogInputStyle())
                    Switch(checked = filter.enabled, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
                }
                FlightFilterInput("名称包含", filter.includePattern, filter.enabled, onFocus = { expanded = true }) {
                    appCatalog.updateFlightFilter(appCatalog.state.value.flightFilter.copy(includePattern = it), debounce = true)
                }
                FlightFilterInput("名称排除", filter.excludePattern, filter.enabled, onFocus = { expanded = true }) {
                    appCatalog.updateFlightFilter(appCatalog.state.value.flightFilter.copy(excludePattern = it), debounce = true)
                }
                state.filterError?.let { CatalogNotice(it) }
                state.failure?.let { CatalogNotice(it) }
                Text("协议", style = catalogInputStyle())
                (state.protocols + filter.protocols).sorted().forEach { protocol ->
                    FlightProtocolChoice(protocol, protocol in filter.protocols, filter.enabled) {
                        val selected = if (protocol in filter.protocols) filter.protocols - protocol else filter.protocols + protocol
                        appCatalog.updateFlightFilter(filter.copy(protocols = selected))
                    }
                }
                if (state.ready && state.protocols.isEmpty() && filter.protocols.isEmpty()) Text("暂无协议", style = catalogInputStyle())
            }
        }
    }
}

@Composable
private fun FlightProtocolChoice(name: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(selected, enabled = enabled, role = Role.Checkbox,
        onValueChange = hapticChange { onClick() }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = catalogInputStyle())
        Checkbox(state = if (selected) ToggleableState.On else ToggleableState.Off,
            onClick = null, modifier = Modifier.clearAndSetSemantics {})
    }
}
