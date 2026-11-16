package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.CatalogTower
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TowerImportResultSheet(tower: CatalogTower, onClose: () -> Unit) {
    val result = tower.importResult
    OverlayBottomSheet(show = true, title = "导入结果", insideMargin = catalogSheetMargin(), onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportCount(result.added, "新增", Modifier.weight(1f))
                ImportCount(result.updated, "更新", Modifier.weight(1f))
                ImportCount(result.removed, "移除", Modifier.weight(1f))
                ImportCount(result.failed, "失败", Modifier.weight(1f))
            }
            if (result.details.isNotEmpty()) {
                HorizontalDivider()
                result.details.forEach { detail -> CatalogNotice(detail, CatalogNoticeKind.Warning) }
            }
            if (tower.failure.isNotBlank()) CatalogNotice(tower.failure)
            CatalogTextButton("完成", onClick = hapticClick(onClose), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ImportCount(count: Long, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(count.toString(), style = MiuixTheme.textStyles.main.copy(fontSize = 20.sp))
        Text(label, style = catalogInputStyle(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
