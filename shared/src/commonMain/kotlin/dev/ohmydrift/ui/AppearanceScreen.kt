package dev.ohmydrift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppearanceScreen(
    listState: LazyListState,
    themeMode: ThemeMode,
    reduceMotion: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    onPreview: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { SectionHeading("舒服，才是好看", "主题 / 色彩 / 文字 / 动效") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("界面外观", style = MiuixTheme.textStyles.title2)
                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            ChoiceOption(
                                label = mode.label,
                                selected = themeMode == mode,
                                onClick = { onThemeMode(mode) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(
                            value = reduceMotion,
                            role = Role.Switch,
                            onValueChange = onReduceMotion,
                        )
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("减少动态效果", style = MiuixTheme.textStyles.title4)
                        Text(
                            "关闭页面切换与卡片展开过渡；组件自身动效由组件及系统控制。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Switch(
                        checked = reduceMotion,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
        item { SectionHeading("文字也有呼吸感", "优先使用 Miuix 文字样式和平台默认字体。") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("向更远处，漂流。", style = MiuixTheme.textStyles.title1)
                    Text("一眼看清主次", style = MiuixTheme.textStyles.title2)
                    Text("正文不必很大声。用适当的留白、清晰的层级，让信息自己说话。")
                    Text(
                        "辅助说明 · 当前选择与设置只在本次会话内保留。",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("强调色", color = MiuixTheme.colorScheme.primary)
                            Row(
                                Modifier.size(48.dp)
                                    .background(MiuixTheme.colorScheme.primary),
                            ) {}
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("正文色", color = MiuixTheme.colorScheme.onSurface)
                            Row(
                                Modifier.size(48.dp)
                                    .background(MiuixTheme.colorScheme.onSurface),
                            ) {}
                        }
                    }
                }
            }
        }
        item {
            ActionButton(
                label = "回到卡片体验动效",
                icon = DriftIcons.Arrow,
                onClick = onPreview,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { DemoFootnote() }
    }
}
