// 使用 Miuix beta03 材质绘制应用专属五项胶囊导航。
package com.ohmyfly.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
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

private val NavigationBarHeight = 60.dp
private val NavigationBarContentInset = 8.dp
private val NavigationBarIndicatorOverhang = 4.dp
private const val NavigationSelectedIndicatorAlpha = 0.12f
private const val NavigationPressedScaleX = 1.08f
private const val NavigationPressedScaleY = 1.12f

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
        NavigationBarContents(
            destination = destination,
            onDestination = onDestination,
            selectedColor = colors.primary,
            unselectedColor = colors.onSurfaceVariantSummary,
            glassEnabled = glassEnabled,
        )
    }
}

/** 用 G2 选中胶囊承载指示动画，并保持逐项无障碍选择与 RTL 拖动。 */
@Composable
private fun NavigationBarContents(
    destination: AppDestination,
    onDestination: (Int) -> Unit,
    selectedColor: androidx.compose.ui.graphics.Color,
    unselectedColor: androidx.compose.ui.graphics.Color,
    glassEnabled: Boolean,
) {
    val items = remember { AppDestination.entries }
    val index = destination.ordinal
    val selectedIndex = rememberUpdatedState(index)
    val onSelect = rememberUpdatedState(onDestination)
    val dragging = remember { mutableStateOf(false) }
    val pressed = remember { mutableStateOf(false) }
    val focusedIndex = remember { mutableIntStateOf(-1) }
    val dragIndex = remember { mutableFloatStateOf(index.toFloat()) }
    val pressPosition = remember { mutableStateOf(Offset.Zero) }
    val motionAllowed = LocalAppMotionAllowed.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val indicatorIndex by animateFloatAsState(
        targetValue = if (dragging.value) dragIndex.floatValue else index.toFloat(),
        animationSpec = when {
            !motionAllowed -> snap()
            dragging.value -> spring(dampingRatio = 1f, stiffness = 1000f)
            else -> spring(dampingRatio = 0.78f, stiffness = 460f)
        },
        label = "主导航选中胶囊",
    )
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed.value && motionAllowed) 1f else 0f,
        animationSpec = if (motionAllowed) spring(dampingRatio = 0.5f, stiffness = 300f) else snap(),
        label = "主导航按压高光",
    )
    BoxWithConstraints(
        Modifier.fillMaxSize().clip(NavigationBarShape).navigationBarDrag(
            itemCount = items.size,
            horizontalInsetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                NavigationBarContentInset.toPx()
            },
            layoutDirection = layoutDirection,
            dragging = dragging,
            dragIndex = dragIndex,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            pressPosition = pressPosition,
            pressed = pressed,
        ),
    ) {
        val slotWidth = (maxWidth - NavigationBarContentInset * 2) / items.size
        NavigationBarSelectionIndicator(
            index = indicatorIndex,
            slotWidth = slotWidth,
            layoutDirection = layoutDirection,
            pressProgress = pressProgress,
            pressPosition = pressPosition.value,
            focusedIndex = focusedIndex.intValue,
            glassEnabled = glassEnabled,
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
                    onFocusChange = { focused ->
                        if (focused) focusedIndex.intValue = itemIndex
                        else if (focusedIndex.intValue == itemIndex) focusedIndex.intValue = -1
                    },
                )
            }
        }
    }
}

/** 选中胶囊和触点高光共用导航外轮廓，避免点击反馈产生第二套圆角。 */
@Composable
private fun NavigationBarSelectionIndicator(
    index: Float,
    slotWidth: androidx.compose.ui.unit.Dp,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    pressProgress: Float,
    pressPosition: Offset,
    focusedIndex: Int,
    glassEnabled: Boolean,
) {
    val physicalIndex = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) {
        index
    } else {
        AppDestination.entries.lastIndex - index
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.offset(x = NavigationBarContentInset + slotWidth * physicalIndex - NavigationBarIndicatorOverhang)
                .width(slotWidth + NavigationBarIndicatorOverhang * 2)
                .height(NavigationBarHeight - 6.dp)
                .graphicsLayer {
                    scaleX = 1f + (NavigationPressedScaleX - 1f) * pressProgress
                    scaleY = 1f + (NavigationPressedScaleY - 1f) * pressProgress
                }
                .clip(NavigationBarShape)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = NavigationSelectedIndicatorAlpha))
                .then(
                    if (glassEnabled) Modifier.navigationInnerShadow(NavigationBarShape) {
                        if (pressProgress > 0f) {
                            NavigationInnerShadow(radius = 8.dp * pressProgress, alpha = pressProgress)
                        } else {
                            null
                        }
                    } else Modifier,
                ),
        )
        Box(Modifier.fillMaxSize().drawWithContent {
            drawContent()
            if (pressProgress > 0f) {
                val spotColor = Color.White.copy(alpha = 0.12f * pressProgress)
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to spotColor,
                            0.5f to spotColor,
                            1f to Color.Transparent,
                        ),
                        center = pressPosition,
                        radius = size.minDimension * 1.2f,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        })
        if (focusedIndex in AppDestination.entries.indices) {
            val physicalFocusIndex = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) {
                focusedIndex
            } else {
                AppDestination.entries.lastIndex - focusedIndex
            }
            Box(
                Modifier.offset(
                    x = NavigationBarContentInset + slotWidth * physicalFocusIndex + 1.dp,
                    y = 3.dp,
                )
                    .width(slotWidth - 2.dp)
                    .height(NavigationBarHeight - 6.dp)
                    .border(2.dp, MiuixTheme.colorScheme.onSurface, AppCorners.shape(AppCorners.Content)),
            )
        }
    }
}

/** 保持Tab语义与主题选中色，按压仅淡化字形，不额外绘制默认波纹。 */
@Composable
private fun RowScope.NavigationBarItem(
    destination: AppDestination,
    selected: Boolean,
    selectedColor: androidx.compose.ui.graphics.Color,
    unselectedColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (selected) selectedColor else unselectedColor
    val color = if (pressed) tint.copy(alpha = 0.6f) else tint
    Column(
        Modifier.weight(1f).fillMaxHeight().padding(horizontal = 1.dp, vertical = 3.dp)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
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
