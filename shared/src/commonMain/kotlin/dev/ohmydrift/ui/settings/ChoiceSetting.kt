package dev.ohmydrift.ui.settings

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
internal fun ChoiceSetting(
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    OverlayDropdownPreference(
        title = title,
        items = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == value },
        onSelectedIndexChange = { index -> onChange(options[index].first) },
    )
}
