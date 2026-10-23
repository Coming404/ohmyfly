package com.ohmyfly.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.settings.MaterialMode
import com.ohmyfly.ui.glass.GlassNavigationSurface
import top.yukonga.miuix.kmp.blur.Backdrop

@Composable
internal fun DriftNavigationBar(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
    materialMode: MaterialMode,
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassNavigationSurface(
            destination = destination,
            onDestination = onDestination,
            backdrop = backdrop,
            enabled = glassEnabled,
            dark = dark,
            materialMode = materialMode,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        )
    }
}
