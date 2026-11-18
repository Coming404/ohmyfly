// 分组编辑和移动在提交确认前保持输入；删除分组不删除节点。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.*
import com.ohmyfly.ui.settings.MirrorIcons
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

@Composable
internal fun FlightGroupEditor(group: CatalogGroup?, groups: List<CatalogGroup>, onClose: () -> Unit) {
    var name by remember(group?.id) { mutableStateOf(group?.name.orEmpty()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val messages = LocalAppMessages.current
    val submit: (String) -> Unit = { action ->
        busy = true; failure = null
        appCatalog.changeGroup(action, group?.id ?: 0, name.trim()) { success, text ->
            busy = false
            if (success) { messages.show(AppMessageKind.Success, text); onClose() }
            else failure = text
        }
    }
    OverlayBottomSheet(show = true, insideMargin = catalogSheetMargin(), title = if (group == null) "新建分组" else "管理分组",
        onDismissRequest = { if (!busy) onClose() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(value = name, textStyle = catalogInputStyle(), onValueChange = { name = it; failure = null }, label = "分组名称",
                singleLine = true, enabled = !busy)
            failure?.let { CatalogNotice(it) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            CatalogTextButton(if (group == null) "添加" else "保存名称", enabled = !busy,
                onClick = hapticClick {
                    val value = name.trim()
                    when {
                        value.isEmpty() || value.count { !it.isLowSurrogate() } > 16 -> failure = "名称须为 1 至 16 个字符"
                        groups.any { it.id != group?.id && it.name == value } -> failure = "该分组名称已存在"
                        else -> submit(if (group == null) "groupCreate" else "groupRename")
                    }
                })
            if (group != null) {
                if (deleting) CatalogNotice("删除“${group.name}”后，节点移至“全部”分组。", CatalogNoticeKind.Warning)
                CatalogAction(if (deleting) "确认删除分组" else "删除分组", MirrorIcons.Trash,
                    destructive = true, enabled = !busy) {
                    if (deleting) submit("groupDelete") else deleting = true
                }
            }
            CatalogTextButton("取消", enabled = !busy, onClick = hapticClick(onClose))
        }
    }
}

@Composable
internal fun FlightGroupMove(nodeIds: Set<Long>, onMoved: () -> Unit, onClose: () -> Unit) {
    val state by appCatalog.state.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val messages = LocalAppMessages.current
    val move: (Long) -> Unit = { id ->
        busy = true; failure = null
        appCatalog.changeGroup("groupMove", id, nodeIds = nodeIds) { success, text ->
            busy = false
            if (success) { messages.show(AppMessageKind.Success, text); onMoved() }
            else failure = text
        }
    }
    OverlayBottomSheet(show = true, insideMargin = catalogSheetMargin(), title = "移动 ${nodeIds.size} 个航班",
        onDismissRequest = { if (!busy) onClose() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * 0.65f)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            failure?.let { CatalogNotice(it) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            CatalogAction("全部", DriftIcons.Nodes, Modifier.fillMaxWidth(), enabled = !busy) { move(0) }
            state.groups.groups.forEach { group ->
                CatalogAction(group.name, DriftIcons.Nodes, Modifier.fillMaxWidth(), enabled = !busy) { move(group.id) }
            }
            CatalogTextButton("取消", enabled = !busy, onClick = hapticClick(onClose))
        }
    }
}
