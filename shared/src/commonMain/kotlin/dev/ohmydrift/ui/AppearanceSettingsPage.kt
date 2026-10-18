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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppearanceSettingsPage(
    listState: LazyListState,
    themeMode: ThemeMode,
    reduceTransparency: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onReduceTransparency: (Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding.withPageSpacing(LocalLayoutDirection.current, horizontal = 12.dp),
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
                            value = reduceTransparency,
                            role = Role.Switch,
                            onValueChange = onReduceTransparency,
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("减少透明效果", style = MiuixTheme.textStyles.title4)
                        Text(
                            "使用不透明背景，减少内容透过导航栏造成的视觉干扰。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Switch(
                        checked = reduceTransparency,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}
