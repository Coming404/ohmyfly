// 在固定七日横轴上绘制真实观测。缺少快照的日期断线，不补零或连到下一次记录。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.catalog.CatalogDay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** 用十进制单位，保留至多一位小数，数字不使用千位分隔符。 */
internal fun controlBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var divisor = 1L
    var index = 0
    while (index < units.lastIndex && bytes / divisor >= 1000) { divisor *= 1000; index++ }
    val whole = bytes / divisor
    val tenth = (bytes % divisor) * 10 / divisor
    return if (tenth == 0L) "$whole ${units[index]}" else "$whole.$tenth ${units[index]}"
}

@Composable
internal fun TowerTrend(days: List<CatalogDay>, fuel: Boolean, onMetric: (Boolean) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val ink = if (fuel) colors.secondary else colors.primary
    val values = remember(days, fuel) { days.map { if (fuel) it.fuelBytes else it.flights } }
    var day by rememberSaveable(days.lastOrNull()?.day) { mutableIntStateOf(days.lastIndex.coerceAtLeast(0)) }
    fun format(value: Long) = if (fuel) controlBytes(value) else "$value 个"
    fun label(i: Int) = days.getOrNull(i)?.day?.takeLast(5)?.replace('-', '/') ?: "—"
    Column(Modifier.fillMaxWidth().background(colors.surfaceContainer, AppCorners.shape(AppCorners.Content)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("近 7 日趋势", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                listOf(false, true).forEach { metric ->
                    val selected = metric == fuel
                    Box(Modifier.clip(AppCorners.shape(AppCorners.Compact))
                        .background(if (selected) colors.surface else Color.Transparent)
                        .selectable(selected, role = Role.Tab, onClick = hapticClick { onMetric(metric) })
                        .heightIn(min = 32.dp).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                        Text(if (metric) "燃油" else "航班", fontSize = 12.sp,
                            color = if (selected) colors.onSurface else colors.onSurfaceVariantSummary)
                    }
                }
            }
        }
        if (values.none { it != null }) {
            Box(Modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.Center) {
                Text(if (fuel) "暂无燃油记录" else "暂无航班记录", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            }
        } else {
            val current = values[day]
            val first = values.first()
            val difference = if (current != null && first != null) current - first else null
            val reading = current?.let(::format) ?: "无记录"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${label(day)}  $reading", fontSize = 12.sp)
                if (difference != null) Text("较首日 ${if (difference > 0) "+" else if (difference < 0) "−" else ""}${format(kotlin.math.abs(difference))}",
                    fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            }
            TrendPlot(values, day, { day = it }, ink, "${label(day)} $reading", ::format)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, days.lastIndex / 2, days.lastIndex).forEach {
                    Text(label(it), fontSize = 10.sp, color = colors.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable
private fun TrendPlot(values: List<Long?>, selected: Int, select: (Int) -> Unit, ink: Color,
    reading: String, format: (Long) -> String) {
    val present = values.filterNotNull()
    val low = present.min()
    val high = present.max()
    val grid = MiuixTheme.colorScheme.dividerLine
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.height(50.dp).padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(format(high), fontSize = 9.sp, color = labelColor)
            if (low != high) Text(format(low), fontSize = 9.sp, color = labelColor)
        }
        val liveSelect by rememberUpdatedState(select)
        fun pick(x: Float, width: Int): Int = ((x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * values.lastIndex).roundToInt()
        Canvas(Modifier.weight(1f).height(56.dp).semantics {
            contentDescription = "逐日趋势"; stateDescription = reading
            progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..values.lastIndex.toFloat(), values.size - 2)
            setProgress { liveSelect(it.roundToInt().coerceIn(values.indices)); true }
        }.pointerInput(values.size) { detectTapGestures { liveSelect(pick(it.x, size.width)) } }
            .pointerInput(values.size) { detectHorizontalDragGestures { change, _ -> liveSelect(pick(change.position.x, size.width)) } }) {
            val pad = 4.dp.toPx(); val span = size.height - 2 * pad
            fun x(i: Int) = pad + (size.width - 2 * pad) * i / values.lastIndex
            fun y(v: Long) = if (high == low) size.height / 2 else pad + span * ((high - v).toDouble() / (high - low)).toFloat()
            for (i in 0..2) drawLine(grid, Offset(0f, pad + span * i / 2), Offset(size.width, pad + span * i / 2), 1.dp.toPx())
            drawLine(ink.copy(alpha = .28f), Offset(x(selected), pad), Offset(x(selected), size.height - pad), 1.dp.toPx())
            val fill = Brush.verticalGradient(listOf(ink.copy(alpha = 0.18f), ink.copy(alpha = 0.01f)))
            var previous: Offset? = null
            values.forEachIndexed { i, value ->
                val point = value?.let { Offset(x(i), y(it)) }
                if (point != null) {
                    previous?.let { start ->
                        val area = Path().apply {
                            moveTo(start.x, size.height - pad); lineTo(start.x, start.y)
                            lineTo(point.x, point.y); lineTo(point.x, size.height - pad); close()
                        }
                        drawPath(area, fill)
                        drawLine(ink, start, point, 2.dp.toPx())
                    }
                    drawCircle(ink, if (i == selected) 3.5.dp.toPx() else 2.dp.toPx(), point)
                }
                previous = point
            }
        }
    }
}
