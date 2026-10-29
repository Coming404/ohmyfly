package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.ui.hapticClick
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ChoiceSetting(
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    icon: ImageVector,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == value }?.second.orEmpty()
    // The stock preference hardcodes Medium; only its row is replaced to meet 15sp Normal.
    val entry = DropdownEntry(options.map { (key, label) ->
        DropdownItem(text = label, selected = key == value,
            onClick = { if (key != value) onChange(key) },
            icon = { modifier -> Icon(icon, null, modifier.size(20.dp)) })
    })
    BasicComponent(
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.semantics { stateDescription = current },
        startAction = { SettingsLeadingIcon(icon) },
        role = Role.DropdownList,
        holdDownState = held,
        enabled = options.isNotEmpty(),
        onClick = hapticClick { expanded = !expanded; if (expanded) held = true },
        onClickLabel = "选择$title",
        endActions = {
            Text(current, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
            DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions)
            OverlayDropdownPopup(entry = entry, show = expanded,
                onDismiss = { expanded = false }, onDismissFinished = { held = false },
                maxHeight = null, dropdownColors = DropdownDefaults.dropdownColors(),
                renderInRootScaffold = true, collapseOnSelection = true)
        },
    ) { SettingsName(title) }
}
