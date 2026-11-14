// 选择工具栏共享实际目录操作，抽屉负责启停、删除确认和分享。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.appCatalog
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.subscription.SubscriptionMirrorSettings
import com.ohmyfly.ui.settings.MirrorIcons
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

@Composable
internal fun CatalogSelectionHost(mode: CatalogSelectionMode, nodeIds: Set<Long>, towerIds: Set<Long>,
    settings: AppSettings, onDone: () -> Unit) {
    var sheet by remember(mode) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val messages = LocalAppMessages.current
    val count = if (mode == CatalogSelectionMode.Nodes) nodeIds.size else towerIds.size
    val finish: (Boolean, String) -> Unit = { ok, text ->
        busy = false
        if (ok) { messages.show(AppMessageKind.Success, text); sheet = ""; onDone() }
        else failure = text
    }
    CatalogSelectionToolbar(mode, enabled = count > 0 && !busy,
        onSpeed = { messages.show(AppMessageKind.Uncertain, "测速入口已保留，执行器尚未接入") },
        onToggle = { sheet = "toggle"; failure = null }, onMove = { sheet = "move" },
        onShare = { sheet = "share" }, onDelete = { sheet = "delete"; failure = null },
        onSync = {
            try {
                val mirrors = SubscriptionMirrorSettings(settings.mirrorEnabled, settings.mirrors, settings.selectedMirrorId)
                appCatalog.refreshTowers(towerIds, settings.subscription, mirrors)
                finish(true, "所选塔台已加入同步队列")
            } catch (_: Exception) { messages.show(AppMessageKind.Failure, "同步未加入队列，请稍后重试") }
        })
    if (sheet == "share") CatalogShareSheet(nodeIds) { sheet = "" }
    if (sheet == "move") FlightGroupMove(nodeIds, onMoved = { sheet = ""; onDone() }, onClose = { sheet = "" })
    if (sheet == "toggle" || sheet == "delete") OverlayBottomSheet(show = true,
        title = if (sheet == "toggle") "启停 $count 个航班" else "删除 $count 个${if (mode == CatalogSelectionMode.Nodes) "航班" else "塔台"}",
        onDismissRequest = { if (!busy) sheet = "" }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            failure?.let { CatalogNotice(it) }
            if (sheet == "toggle") {
                CatalogAction("启用", MirrorIcons.Power, enabled = !busy) {
                    busy = true; appCatalog.changeNodes(nodeIds, "enable", finish)
                }
                CatalogAction("停用", MirrorIcons.Power, enabled = !busy) {
                    busy = true; appCatalog.changeNodes(nodeIds, "disable", finish)
                }
            } else {
                Text(if (mode == CatalogSelectionMode.Nodes) "删除所选节点及其来源和记录；下次同步可能重新导入。" else "删除所选塔台及其来源；其他塔台仍引用的节点会保留。")
                CatalogAction("确认删除", MirrorIcons.Trash, destructive = true, enabled = !busy) {
                    busy = true
                    if (mode == CatalogSelectionMode.Nodes) appCatalog.changeNodes(nodeIds, "delete", finish)
                    else appCatalog.removeTowers(towerIds, finish)
                }
            }
            CatalogTextButton("取消", enabled = !busy, onClick = hapticClick { sheet = "" })
        }
    }
}
