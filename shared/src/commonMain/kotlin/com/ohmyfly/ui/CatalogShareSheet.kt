// 单节点与多节点共享同一个 YAML 导出抽屉，成功反馈保留在抽屉内。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.settings.SettingsIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

@Composable
internal fun CatalogShareSheet(ids: Set<Long>, onClose: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val export: (Boolean) -> Unit = { file ->
        busy = true; notice = null
        scope.launch {
            try {
                val yaml = exportCatalogYaml(ids)
                if (file) shareCatalogFile(yaml) else copyCatalogText(yaml)
                failed = false
                notice = if (file) "已打开系统分享" else "已复制 Mihomo YAML"
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { failed = true; notice = "导出失败，请重试"
            } finally { busy = false }
        }
    }
    OverlayBottomSheet(show = true, title = "分享 ${ids.size} 个航班", onDismissRequest = { if (!busy) onClose() }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            notice?.let { CatalogNotice(it, if (failed) CatalogNoticeKind.Error else CatalogNoticeKind.Success) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            CatalogAction("导出 YAML 文件", SettingsIcons.Backup, Modifier.fillMaxWidth(), enabled = !busy && ids.isNotEmpty()) { export(true) }
            CatalogAction("复制 YAML", SettingsIcons.Backup, Modifier.fillMaxWidth(), enabled = !busy && ids.isNotEmpty()) { export(false) }
            CatalogTextButton("完成", enabled = !busy, onClick = hapticClick(onClose))
        }
    }
}
