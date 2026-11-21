// 使用 Miuix beta03 材质绘制应用专属五项胶囊导航。
package com.ohmyfly.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.ohmyfly.shapes.AppCorners
import com.ohmyfly.ui.glass.rememberGlassStyle
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val NavigationBarHeight = 54.dp
private val NavigationBarContentInset = 8.dp
private val NavigationBarIndicatorOverhang = 4.dp
private const val NavigationSelectedIndicatorAlpha = 0.12f

/** 设置安全区、共享背景采样和主题光照，持有五个入口的导航状态。 */
@Composable
internal fun DriftNavigationBar(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    dark: Boolean,
) {
    val style = rememberGlassStyle(glassEnabled, dark)
    val material = navigationBarMaterial()
    Box(
        modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        NavigationBarSurface(
            destination = destination,
            onDestination = hapticChange { onDestination(AppDestination.entries[it]) },
            backdrop = backdrop,
            glassEnabled = glassEnabled,
            style = style.value,
            material = material,
        )
    }
}

/** 以同一 G2 轮廓绘制折射表面、边缘光带和不透明降级表面。 */
@Composable
private fun NavigationBarSurface(
    destination: AppDestination,
    onDestination: (Int) -> Unit,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    style: GlassStyle,
    material: top.yukonga.miuix.kmp.material.GlassMaterial,
) {
    val colors = MiuixTheme.colorScheme
    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().height(NavigationBarHeight)
        .glassPanel(
            backdrop = backdrop.takeIf { glassEnabled },
            shape = NavigationBarShape,
            style = style,
            material = material,
            stroke = if (glassEnabled) GlassNavigationBarDefaults.stroke() else null,
            shadow = if (glassEnabled) GlassShadows.Float else null,
            shading = glassEnabled,
            fallback = Modifier.clip(NavigationBarShape).background(colors.surfaceContainer),
        ), contentAlignment = Alignment.Center,
    ) {
        NavigationBarContents(destination, onDestination, colors.primary, colors.onSurfaceVariantSummary)
    }
}

/** 用 G2 选中胶囊承载指示动画，并保持逐项无障碍选择与 RTL 拖动。 */
@Composable
private fun NavigationBarContents(
    destination: AppDestination,
    onDestination: (Int) -> Unit,
    selectedColor: androidx.compose.ui.graphics.Color,
    unselectedColor: androidx.compose.ui.graphics.Color,
) {
    val items = remember { AppDestination.entries }
    val index = destination.ordinal
    val selectedIndex = rememberUpdatedState(index)
    val onSelect = rememberUpdatedState(onDestination)
    val dragging = remember { mutableStateOf(false) }
    val dragIndex = remember { mutableFloatStateOf(index.toFloat()) }
    val motionAllowed = LocalAppMotionAllowed.current
    val indicatorIndex by animateFloatAsState(
        targetValue = if (dragging.value) dragIndex.floatValue else index.toFloat(),
        animationSpec = if (dragging.value || !motionAllowed) snap()
        else spring(dampingRatio = 0.78f, stiffness = 460f),
        label = "主导航选中胶囊",
    )
    BoxWithConstraints(
        Modifier.fillMaxSize().clip(NavigationBarShape).navigationBarDrag(
            itemCount = items.size,
            horizontalInsetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                NavigationBarContentInset.toPx()
            },
            layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current,
            dragging = dragging,
            dragIndex = dragIndex,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
        ),
    ) {
        val slotWidth = (maxWidth - NavigationBarContentInset * 2) / items.size
        Box(
            Modifier.offset(x = NavigationBarContentInset + slotWidth * indicatorIndex - NavigationBarIndicatorOverhang)
                .width(slotWidth + NavigationBarIndicatorOverhang * 2)
                .height(NavigationBarHeight - 6.dp)
                .clip(NavigationBarShape)
                .background(selectedColor.copy(alpha = NavigationSelectedIndicatorAlpha)),
        )
        Row(
            Modifier.fillMaxSize().selectableGroup().padding(horizontal = NavigationBarContentInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { itemIndex, item ->
                NavigationBarItem(
                    destination = item,
                    selected = itemIndex == index,
                    selectedColor = selectedColor,
                    unselectedColor = unselectedColor,
                    onClick = { onSelect.value(itemIndex) },
                )
            }
        }
    }
}

/** 选中项使用主题色、中粗图标和粗标签，反馈限制在自身 G2 轮廓内。 */
@Composable
private fun RowScope.NavigationBarItem(
    destination: AppDestination,
    selected: Boolean,
    selectedColor: androidx.compose.ui.graphics.Color,
    unselectedColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val color = if (selected) selectedColor else unselectedColor
    Column(
        Modifier.weight(1f).fillMaxHeight().padding(horizontal = 1.dp, vertical = 3.dp)
            .clip(AppCorners.shape(AppCorners.Selection))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        top.yukonga.miuix.kmp.basic.Icon(
            imageVector = destination.navigationIcon(selected),
            contentDescription = null,
            modifier = Modifier.size(25.dp),
            tint = color,
        )
        top.yukonga.miuix.kmp.basic.Text(
            text = destination.label,
            color = color,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

/** 切换到 Glass Medium 字重时保持五个入口各自的语义图标。 */
private fun AppDestination.navigationIcon(selected: Boolean) = when (this) {
    AppDestination.Overview -> if (selected) DriftIcons.OverviewSelected else DriftIcons.Overview
    AppDestination.Subscriptions -> if (selected) DriftIcons.SubscriptionsSelected else DriftIcons.Subscriptions
    AppDestination.Speed -> if (selected) DriftIcons.SpeedSelected else DriftIcons.Speed
    AppDestination.NodeLibrary -> if (selected) DriftIcons.FlightsSelected else DriftIcons.Flights
    AppDestination.Settings -> if (selected) DriftIcons.SettingsSelected else DriftIcons.Settings
}
