// 全选动作绑定页面版本，退出选择模式后自动取消未完成读取。
package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.appCatalog
import com.ohmyfly.catalog.selectionIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CatalogSelectAllAction(mode: CatalogSelectionMode, towerId: Long,
    onNodes: (Set<String>) -> Unit, onTowers: (Set<Long>) -> Unit) {
    val scope = rememberCoroutineScope()
    val messages = LocalAppMessages.current
    var busy by remember { mutableStateOf(false) }
    HeaderTextAction(if (busy) "读取中" else "全选", DriftIcons.Check, enabled = !busy, onClick = {
        busy = true
        scope.launch {
            try {
                val ids = appCatalog.selectionIds(if (mode == CatalogSelectionMode.Nodes) "flights" else "towers", towerId)
                if (mode == CatalogSelectionMode.Nodes) onNodes(ids.mapTo(linkedSetOf()) { it.toString() }) else onTowers(ids)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { messages.show(AppMessageKind.Failure, "全选失败，请重新读取目录")
            } finally { busy = false }
        }
    })
}

@Composable
internal fun CatalogSelectionCheck(selected: Boolean) {
    val colors = MiuixTheme.colorScheme
    Box(Modifier.size(24.dp).background(if (selected) colors.primary else colors.surface, CircleShape)
        .border(1.dp, colors.primary, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) Icon(DriftIcons.Check, "已选择", Modifier.size(16.dp), tint = colors.onPrimary)
    }
}
