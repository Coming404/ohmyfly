package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CatalogSelectionCheck(selected: Boolean) {
    val colors = MiuixTheme.colorScheme
    Box(Modifier.size(24.dp).background(if (selected) colors.primary else colors.surface, CircleShape)
        .border(1.dp, colors.primary, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) Icon(DriftIcons.Check, "已选择", Modifier.size(16.dp), tint = colors.onPrimary)
    }
}
