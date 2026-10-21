package dev.ohmydrift.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

@Composable
internal actual fun SettingsInputMenu(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextToolbar provides HiddenSettingsTextToolbar, content = content)
}

/** Public Compose toolbar hook; no UIKit swizzling or global responder changes. */
private object HiddenSettingsTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
    override fun hide() = Unit
}
