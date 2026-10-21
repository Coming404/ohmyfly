package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ohmydrift.mirror.MirrorProbeResult
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One mirror source. The whole row selects; the trailing `⋯` is the only management entry and
 * never selects. The radio glyph is decorative because the row itself carries the radio role.
 *
 * [selectionEnabled] false means the mirror switch is off: the stored choice is kept, the row
 * cannot be selected, and a selected row reads "已保留" instead of pretending to be in use.
 * [menuFocusRequester] lets the owner return keyboard focus to this row's `⋯` after a dialog
 * finishes dismissing; when null the button keeps its normal traversal position.
 */
@Composable
internal fun MirrorRow(
    name: String,
    address: String,
    selected: Boolean,
    selectionEnabled: Boolean,
    result: MirrorProbeResult?,
    phase: MirrorTestPhase,
    onSelect: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    menuFocusRequester: FocusRequester? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                enabled = selectionEnabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .semantics {
                if (!selectionEnabled && selected) stateDescription = "已保留"
            }
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = address,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MirrorStatusLabel(result, phase)
        IconButton(
            onClick = onMenu,
            minWidth = 44.dp,
            minHeight = 44.dp,
            modifier = if (menuFocusRequester != null) Modifier.focusRequester(menuFocusRequester) else Modifier,
        ) {
            Icon(
                imageVector = MirrorIcons.More,
                contentDescription = "管理$name",
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Short visible token; the measured reason and its samples are exposed to the reader instead. */
@Composable
private fun MirrorStatusLabel(result: MirrorProbeResult?, phase: MirrorTestPhase) {
    val color = when (mirrorTone(result, phase)) {
        MirrorTone.Muted -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        MirrorTone.Normal -> MiuixTheme.colorScheme.onSurface
        MirrorTone.Active -> MiuixTheme.colorScheme.primary
        MirrorTone.Error -> MiuixTheme.colorScheme.error
    }
    Text(
        text = mirrorStatusText(result, phase),
        fontSize = 12.sp,
        color = color,
        maxLines = 1,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = mirrorStatusDetail(result, phase)
        },
    )
}
