package com.ohmyfly.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.ui.LocalAppMotionAllowed
import com.ohmyfly.ui.hapticClick
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsExpandableRow(
    title: String,
    value: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    SettingsRowFeedback {
        BasicComponent(
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.semantics { stateDescription = if (expanded) "已展开" else "已收起" },
            startAction = { SettingsLeadingIcon(icon) },
            onClick = hapticClick(onClick),
            onClickLabel = if (expanded) "收起$title" else "展开$title",
            role = Role.Button,
            endActions = {
                Text(value, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                SettingsExpansionArrow(expanded)
            },
        ) { SettingsName(title) }
    }
}

@Composable
private fun SettingsExpansionArrow(expanded: Boolean) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val target = if (expanded) 90f else if (rtl) 180f else 0f
    val rotation = if (LocalAppMotionAllowed.current) {
        val animated by animateFloatAsState(target,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            label = "settings-arrow")
        animated
    } else target
    Icon(MiuixIcons.Basic.ArrowRight, contentDescription = null,
        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        modifier = Modifier.size(width = 10.dp, height = 16.dp).graphicsLayer { rotationZ = rotation })
}

@Composable
internal fun SettingsExpansion(expanded: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (LocalAppMotionAllowed.current) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium,
            ), expandFrom = Alignment.Top),
            exit = shrinkVertically(animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium,
            ), shrinkTowards = Alignment.Top),
        ) { Column(content = content) }
    } else if (expanded) Column(content = content)
}
