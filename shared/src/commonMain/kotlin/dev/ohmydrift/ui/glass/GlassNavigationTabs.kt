// Derived from Melox LiquidGlassNavigationBar.kt, 49ee06a, Apache-2.0.
// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package dev.ohmydrift.ui.glass

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ohmydrift.ui.AppDestination
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The recording copy is drawing only: no selectable, pointer input or focus targets. */
@Composable
internal fun RowScope.GlassNavigationTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    recording: Boolean = false,
    onFocusChange: (Int, Boolean) -> Unit = { _, _ -> },
    sourceScale: () -> Float = { 1f },
) {
    AppDestination.entries.forEachIndexed { index, item ->
        val selected = index == selectedIndex
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val tint = if (recording || selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceVariantSummary
        val color = if (!recording && pressed) tint.copy(alpha = 0.6f) else tint
        val input = if (recording) Modifier else Modifier
            .onFocusChanged { onFocusChange(index, it.isFocused) }
            .onPreviewKeyEvent { event ->
                val activation = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
                if (activation && event.type == KeyEventType.KeyUp) onSelect(index)
                activation
            }
            .selectable(
                selected = selected,
                onClick = { onSelect(index) },
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            )
        Column(
            modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                .then(input)
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .graphicsLayer { scaleX = sourceScale(); scaleY = scaleX },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Icon(item.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
            Text(
                item.label,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
