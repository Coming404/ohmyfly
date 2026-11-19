// 总控只展示目录汇总；过滤塔台名称或翻页不会改变全库读数。
package com.ohmyfly.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.catalog.CatalogState
import com.ohmyfly.ui.settings.SettingsIcons
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TowerControl(state: CatalogState, ready: Boolean, onCancel: () -> Unit, onSync: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var fuel by rememberSaveable { mutableStateOf(false) }
    val summary = state.summary
    val colors = MiuixTheme.colorScheme
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ControlTowerMark(colors.primary)
                Text("总控", Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = hapticClick { expanded = !expanded }, modifier = Modifier.size(44.dp)) {
                    CatalogExpansionArrow(expanded)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ControlMetric("塔台", if (state.ready) summary.towers.toString() else "—")
                ControlMetric("航班", if (state.ready) summary.flights.toString() else "—")
                ControlMetric("燃油", if (state.ready && summary.fuelReported) controlBytes(summary.usedTrafficBytes) else "—")
            }
            if (expanded) {
                TowerTrend(summary.trend, fuel, { fuel = it })
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider()
            val enabled = !state.stopping && (state.busy || (ready && state.ready && summary.syncableTowers > 0))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(controlStatus(state, ready), Modifier.weight(1f).padding(end = 8.dp),
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                SyncControl(state.busy, enabled, if (state.busy) onCancel else onSync)
            }
        }
    }
}

// 复用设计稿的塔台轮廓，不用链接图标替代管理入口。
@Composable
private fun ControlTowerMark(color: Color) {
    val path = remember { PathParser().parsePathString("M5 4H19L17 10H7Z M9 10V14H15V10 M10 14L8 21 M14 14L16 21 M6 21H18 M12 1V4").toPath() }
    Canvas(Modifier.size(28.dp).background(color.copy(alpha = 0.10f), RoundedCornerShape(9.dp)).padding(5.dp)) {
        withTransform({ scale(size.width / 24f, size.height / 24f, androidx.compose.ui.geometry.Offset.Zero) }) {
            drawPath(path, color, style = Stroke(width = 1.7f))
        }
    }
}

@Composable
private fun RowScope.ControlMetric(label: String, value: String) {
    Row(Modifier.weight(1f).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

/** 取消请求发送后保持停止状态，直到批次清理和目录重读完成。 */
internal fun controlStatus(state: CatalogState, ready: Boolean): String = when {
    state.stopping -> "正在停止全部任务…"
    state.busy && state.sourceTasks.isNotEmpty() -> {
        val phase = state.sourceTasks.values.firstOrNull { it.phase != "排队" }?.phase ?: "排队"
        "$phase　${state.taskCompleted}/${state.taskTotal}" + if (state.taskFailed > 0) "，${state.taskFailed} 个失败" else ""
    }
    state.busy && state.taskTotal > 0 && state.taskCompleted >= state.taskTotal -> "正在刷新目录…"
    state.busy && state.queuedBatches > 0 -> "等待处理 ${state.queuedBatches} 个批次"
    state.busy -> "正在准备任务…"
    state.failure != null && state.taskFailed > 0 -> "${state.taskFailed} 个来源处理失败"
    state.failure != null -> state.failure
    state.stopped -> "已停止，已提交的数据保留"
    !state.ready -> "正在读取目录…"
    !ready -> "同步设置未就绪"
    state.taskFailed > 0 -> "${state.taskFailed} 个来源处理失败"
    state.summary.failedTowers > 0 -> "${state.summary.failedTowers} 个塔台异常"
    state.summary.syncableTowers == 0L -> "没有可同步的订阅塔台"
    state.taskCompleted > 0 -> "已完成 ${state.taskCompleted} 个来源"
    else -> "可同步 ${state.summary.syncableTowers} 个塔台"
}

@Composable
private fun SyncControl(busy: Boolean, enabled: Boolean, action: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val background = if (busy) colors.error else if (enabled) colors.primary else colors.surfaceContainer
    val content = if (busy) colors.onError else if (enabled) colors.onPrimary else colors.disabledOnSurface
    Box(Modifier.heightIn(min = 44.dp).clip(CircleShape).clickable(enabled = enabled,
        role = Role.Button, onClick = hapticClick(action)), contentAlignment = Alignment.Center) {
        Row(Modifier.background(background, CircleShape).heightIn(min = 30.dp).padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (busy) Box(Modifier.size(11.dp).background(content, RoundedCornerShape(2.dp)))
            else Icon(SettingsIcons.Retry, null, Modifier.size(14.dp), tint = content)
            Text(if (busy) "全部停止" else "全部同步", fontSize = 12.sp, color = content)
        }
    }
}
