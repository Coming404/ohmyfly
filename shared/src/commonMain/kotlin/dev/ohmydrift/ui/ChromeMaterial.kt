package dev.ohmydrift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val ChromeCornerRadius = 50.dp

/** One material policy, applied separately to each small control surface. */
@Composable
internal fun Modifier.chromeMaterial(
    backdrop: Backdrop,
    enabled: Boolean,
    dark: Boolean,
): Modifier {
    val shape = RoundedCornerShape(ChromeCornerRadius)
    val surface = MiuixTheme.colorScheme.surface
    val colors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(surface.copy(alpha = if (dark) 0.28f else 0.22f))),
    )
    val base = this
        .dropShadow(shape = shape, shadow = Shadow(radius = 8.dp, color = Color.Black, alpha = 0.12f))
        .clip(shape)
    val material = if (enabled) {
        base.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = 20f,
            noiseCoefficient = 0f,
            colors = colors,
        )
    } else {
        base.background(surface)
    }
    return material.border(0.5.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape)
}
