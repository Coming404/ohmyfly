// 选择框与连续曲率选中表面共用固定蓝色强调，和原型保持一致。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CatalogSelectionBlue = Color(0xFF3482FF)

/** 蓝色薄覆盖与细轮廓始终遵循同一个连续曲率边界。 */
@Composable
internal fun Modifier.catalogSelectionSurface(selected: Boolean, radius: Dp): Modifier {
    val shape = AppCorners.shape(radius)
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return clip(shape)
        .background(if (selected) CatalogSelectionBlue.copy(alpha = if (dark) 0.22f else 0.10f) else Color.Transparent)
        .then(if (selected) Modifier.border(1.dp, CatalogSelectionBlue.copy(alpha = 0.65f), shape) else Modifier)
}

/** 仅呈现状态，点击与触感由整行负责；允许父行按自身高度居中。 */
@Composable
internal fun CatalogSelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Box(modifier.size(24.dp).background(if (selected) CatalogSelectionBlue else Color.Transparent, AppCorners.Circle)
        .border(1.dp, if (selected) CatalogSelectionBlue else colors.outline, AppCorners.Circle), contentAlignment = Alignment.Center) {
        if (selected) Icon(DriftIcons.Check, "已选择", Modifier.size(16.dp), tint = Color.White)
    }
}
