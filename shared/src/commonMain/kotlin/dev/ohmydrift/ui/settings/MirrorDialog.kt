package dev.ohmydrift.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ohmydrift.ui.DriftIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DialogCornerRadius = 28.dp
private val DialogOutsideMargin = 16.dp

/**
 * The one dialog shell for manage, add/edit and delete. It is always composed (never wrapped in
 * `if (show)`) so the exit animation and [onDismissFinished] run; the caller keeps the visibility
 * state. There is no dim layer: the page behind uses a light blur owned by the host, and the
 * dialog surface uses the default Miuix surface.
 *
 * The body scrolls inside the window height so the keyboard never pushes the title off screen;
 * safe drawing insets stay with the host scaffold and are not consumed twice here.
 */
@Composable
internal fun MirrorDialog(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    icon: ImageVector = MirrorIcons.Github,
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val containerHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val imeHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    // The keyboard shrinks the usable height; safe drawing insets stay with the host scaffold.
    val maxDialogHeight = (containerHeight - imeHeight - DialogOutsideMargin * 2).coerceAtLeast(200.dp)
    OverlayDialog(
        show = show,
        enableWindowDim = false,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        outsideMargin = DpSize(DialogOutsideMargin, DialogOutsideMargin),
        insideMargin = DpSize(0.dp, 0.dp),
        largeScreen = true,
        maxWidth = 520.dp,
        cornerRadius = DialogCornerRadius,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxDialogHeight)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(
                    onClick = onDismiss,
                    minWidth = 44.dp,
                    minHeight = 44.dp,
                ) {
                    Icon(
                        imageVector = DriftIcons.Clear,
                        contentDescription = "关闭",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(MiuixTheme.colorScheme.dividerLine))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                content()
            }
            footer()
        }
    }
}

/**
 * One action per line inside the manage dialog. The icon sits on a neutral tile; [danger] uses
 * the error role for both icon and label. Disabled rows stay readable but are not clickable.
 */
@Composable
internal fun MirrorDialogAction(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = when {
        !enabled -> MiuixTheme.colorScheme.disabledOnSurface
        danger -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp)
                .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
        Text(text = text, fontSize = 15.sp, color = contentColor, modifier = Modifier.weight(1f))
    }
}
