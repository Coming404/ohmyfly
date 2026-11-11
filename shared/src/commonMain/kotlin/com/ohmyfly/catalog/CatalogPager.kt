package com.ohmyfly.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.ui.settings.*

@Composable
internal fun CatalogPager(
    index: Int, total: Long, failure: String?, onPrevious: () -> Unit, onNext: () -> Unit, onRetry: () -> Unit,
    loading: Boolean = false,
) {
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsHint(if (loading) "正在读取…" else "共 $total 条 · 第 ${index + 1} 页 · 每页 100 条")
            failure?.let { SettingsHint(it, isError = true) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsButton("上一页", SettingsIcons.Retry, Modifier.weight(1f), enabled = !loading && index > 0, onClick = onPrevious)
                SettingsButton("下一页", SettingsIcons.Retry, Modifier.weight(1f), enabled = !loading && (index + 1L) * 100 < total, onClick = onNext)
            }
            if (failure != null) SettingsButton("重试", SettingsIcons.Retry, onClick = onRetry)
        }
    }
}
