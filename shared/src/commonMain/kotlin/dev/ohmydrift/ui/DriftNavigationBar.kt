package dev.ohmydrift.ui

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.TooltipBox

@Composable
internal fun DriftNavigationBar(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
) {
    FloatingNavigationBar {
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
