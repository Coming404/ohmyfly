package com.ohmyfly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.ui.hapticClick
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MirrorSourceHeader() {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 16.dp),
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
    }
}

@Composable
internal fun BoxScope.MirrorDisabledVeil(disabled: Boolean) {
    if (disabled) {
        Box(
            Modifier.matchParentSize()
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .clearAndSetSemantics {},
        )
    }
}

/** Stops an active batch or starts one real test for every configured mirror. */
@Composable
internal fun MirrorProbeBar(
    runner: MirrorProbeRunner,
    mirrors: List<MirrorEntry>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val targets = remember(mirrors) { mirrors.map { it.id to (it as MirrorEntry?) } }
    val busy = runner.running || runner.stopping
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Button(
                onClick = hapticClick {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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
        MirrorDisabledVeil(disabled = !enabled && !runner.running)
    }
}
