package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorOrder
import dev.ohmydrift.mirror.ProbeStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Header of the mirror-source card: one label and one arrow. The arrow cycles
 * manual → latency ascending → latency descending → manual; it shows no text, so the current
 * and the next state are announced instead.
 */
@Composable
internal fun MirrorSourceHeader(order: MirrorOrder, onCycle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = MirrorIcons.List,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "镜像源",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onCycle,
            minWidth = 44.dp,
            minHeight = 44.dp,
            modifier = Modifier.semantics {
                contentDescription = "排序"
                stateDescription = "${order.sortLabel}，点击切换为${order.nextOrder.sortLabel}"
            },
        ) {
            Icon(
                imageVector = order.sortIcon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Bottom action of the mirror page: idle starts one real batch over direct plus every configured
 * mirror, probing stops it, and a stop in progress disables it so no second batch overlaps.
 * Progress is exposed as a live state description rather than extra copy.
 *
 * [enabled] only gates starting a new batch (for example while settings are read-only); stopping
 * stays reachable, and switching mirrors off does not block manually testing candidates.
 * The bar adds no horizontal page padding; the host places it and supplies the insets.
 */
@Composable
internal fun MirrorProbeBar(
    runner: MirrorProbeRunner,
    mirrors: List<MirrorEntry>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val targets = remember(mirrors) { mirrors.map { it.id to (it as MirrorEntry?) } }
    val failed = remember(targets, runner.results) {
        targets.filter { (id, _) -> runner.results[id]?.status?.let { it != ProbeStatus.Success } == true }
    }
    val busy = runner.running || runner.stopping
    Column(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!busy && failed.isNotEmpty()) {
            TextButton(
                text = "重试失败项（${failed.size}）",
                enabled = enabled,
                onClick = { runner.measureAll(failed) },
            )
        }
        Button(
            onClick = {
                if (runner.running) runner.stop() else runner.measureAll(targets)
            },
            enabled = when {
                runner.stopping -> false
                runner.running -> true
                else -> enabled
            },
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                if (busy) {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = "已完成 ${runner.completed}，共 ${runner.total}"
                }
            },
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (busy) MirrorIcons.Stop else MirrorIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = when {
                        runner.stopping -> "正在停止"
                        runner.running -> "停止测试"
                        else -> "测试全部"
                    },
                    fontSize = 15.sp,
                )
            }
        }
    }
}

private val MirrorOrder.sortLabel: String
    get() = when (this) {
        MirrorOrder.Manual -> "手动顺序"
        MirrorOrder.LatencyAscending -> "耗时低到高"
        MirrorOrder.LatencyDescending -> "耗时高到低"
    }

private val MirrorOrder.nextOrder: MirrorOrder
    get() = when (this) {
        MirrorOrder.Manual -> MirrorOrder.LatencyAscending
        MirrorOrder.LatencyAscending -> MirrorOrder.LatencyDescending
        MirrorOrder.LatencyDescending -> MirrorOrder.Manual
    }

private val MirrorOrder.sortIcon
    get() = when (this) {
        MirrorOrder.Manual -> MirrorIcons.Sort
        MirrorOrder.LatencyAscending -> MirrorIcons.Up
        MirrorOrder.LatencyDescending -> MirrorIcons.Down
    }
