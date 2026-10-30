package com.ohmyfly.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.ui.DriftIcons
import com.ohmyfly.ui.hapticClick
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DialogCornerRadius = 28.dp
private val DialogOutsideMargin = 16.dp

/** One Miuix dialog shell for add/edit and delete confirmation. */
@Composable
internal fun MirrorDialog(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    icon: ImageVector = MirrorIcons.Github,
    dismissible: Boolean = true,
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
        modifier = Modifier.shadow(18.dp, RoundedCornerShape(DialogCornerRadius), clip = false),
        enableWindowDim = true,
        onDismissRequest = { if (dismissible) onDismiss() },
        onDismissFinished = onDismissFinished,
        outsideMargin = DpSize(DialogOutsideMargin, DialogOutsideMargin),
        insideMargin = DpSize(0.dp, 0.dp),
        largeScreen = true,
        maxWidth = 520.dp,
        cornerRadius = DialogCornerRadius,
    ) {
        // Content registers after Miuix's handler, so blocked back never drives its predictive animation.
        PredictiveBackHandler(
            enabled = show && !dismissible,
            onProgress = { events -> events.collect {} },
            onCommit = {},
            onCancel = {},
        )
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
                if (dismissible) {
                    IconButton(
                        onClick = hapticClick(onDismiss),
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
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(MiuixTheme.colorScheme.dividerLine))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                content()
            }
            footer()
        }
    }
}
