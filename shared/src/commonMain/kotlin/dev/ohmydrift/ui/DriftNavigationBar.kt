package dev.ohmydrift.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.blur.Backdrop

@Composable
internal fun DriftNavigationBar(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
) {
    FloatingNavigationBar(
        modifier = Modifier.chromeMaterial(backdrop, glassEnabled, dark),
        color = Color.Transparent,
        cornerRadius = ChromeCornerRadius,
        shadowElevation = 0.dp,
    ) {
        AppDestination.entries.forEach { item ->
            TooltipBox(text = item.label) {
                FloatingNavigationBarItem(
                    selected = destination == item,
                    onClick = { onDestination(item) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
    }
}
