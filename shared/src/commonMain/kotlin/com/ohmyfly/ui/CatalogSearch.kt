package com.ohmyfly.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.SettingsIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.platform.LocalFocusManager
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun CatalogSearch(query: String, label: String, title: String, count: Long,
    onAdd: (() -> Unit)? = null, addEnabled: Boolean = true, onQuery: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val collapse: (Boolean) -> Unit = { value ->
        expanded = value
        if (!value) { onQuery(""); focus.clearFocus() }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            CatalogBadge(count.toString())
            Spacer(Modifier.weight(1f))
            IconButton(onClick = hapticClick { collapse(!expanded) }, modifier = Modifier.size(44.dp)) {
                Icon(SettingsIcons.Search, "搜索$title", tint = MiuixTheme.colorScheme.onSurface)
            }
            onAdd?.let { add ->
                IconButton(onClick = hapticClick(add), enabled = addEnabled, modifier = Modifier.size(44.dp)) {
                    Icon(MirrorIcons.Add, "添加塔台", tint = MiuixTheme.colorScheme.onSurface)
                }
            }
        }
        if (expanded) SearchBar(
            modifier = Modifier.fillMaxWidth(),
            expanded = expanded,
            onExpandedChange = collapse,
            inputField = {
                InputField(query = query, onQueryChange = onQuery, label = label,
                    onSearch = { focus.clearFocus() }, expanded = expanded, onExpandedChange = collapse)
            },
            outsideEndAction = { TextButton("取消", onClick = hapticClick { collapse(false) }) },
        ) {}
    }
}
