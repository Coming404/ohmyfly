package dev.ohmydrift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsScreen(
    listState: LazyListState,
    themeMode: ThemeMode,
    reduceMotion: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SmallTitle("界面外观") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        .padding(16.dp),
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
        item { SmallTitle("关于当前版本") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "五页功能框架",
                    summary = "订阅导入、真实测速与历史记录尚未接入。节点为演示数据，外观设置只在本次会话内保留。",
                )
            }
        }
    }
}
