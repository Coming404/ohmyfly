// 指标颜色遵循原型阈值，仅根据真实有效测量着色。
package com.ohmyfly.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ohmyfly.catalog.CatalogMetricSummary
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun metricColor(summary: CatalogMetricSummary?): Color {
    val colors = MiuixTheme.colorScheme
    val metric = summary?.valid ?: summary?.latest ?: return colors.onSurfaceVariantSummary
    val value = metric.value ?: return if (metric.status == "failed") colors.error else colors.onSurfaceVariantSummary
    val good = if (metric.kind == "latency") value < 200 else value / 1024 / 1024 >= 10
    val moderate = if (metric.kind == "latency") value < 500 else value / 1024 / 1024 >= 3
    val dark = colors.background.luminance() < 0.5f
    return when {
        good -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        moderate -> if (dark) Color(0xFFFFD54F) else Color(0xFF8A5A00)
        else -> colors.error
    }
}
