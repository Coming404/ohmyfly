// 液态玻璃参数以共享预览即时反馈，结束滑动后再保存。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.ui.CatalogToolbarAction
import top.yukonga.miuix.kmp.basic.Icon
import com.ohmyfly.settings.GlassMaterialSettings
import com.ohmyfly.settings.MaterialMode
import com.ohmyfly.settings.SettingsLimits
import com.ohmyfly.ui.CatalogTextButton
import com.ohmyfly.ui.LocalChromeAppearance
import com.ohmyfly.ui.NavigationBarShape
import com.ohmyfly.ui.chromeMaterial
import com.ohmyfly.ui.hapticClick
import com.ohmyfly.ui.glass.glassPreset
import com.ohmyfly.ui.glass.glassShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun GlassMaterialSettingsPage(
    settings: AppSettings,
    onChange: (String, (AppSettings) -> AppSettings) -> Unit,
    padding: PaddingValues,
) {
    val appearance = LocalChromeAppearance.current
    val referencePreset = glassPreset(MaterialMode.LiquidGlass, appearance.dark)
    val currentSettings = settings.glassMaterial
    val initialValues = GlassMaterialSettings(
        secondLayerAlpha = currentSettings.secondLayerAlpha ?: referencePreset.surfaceAlpha,
        thirdLayerAlpha = currentSettings.thirdLayerAlpha ?: referencePreset.highlightAlpha,
        blurRadiusDp = currentSettings.blurRadiusDp ?: referencePreset.blur,
        convexLensEnabled = currentSettings.convexLensEnabled,
    )
    var previewSettings by remember(currentSettings, referencePreset) { mutableStateOf(initialValues) }
    val previewAvailable = glassShaderSupported() && !settings.reduceTransparency &&
        !appearance.systemReduceTransparency
    SettingsList(padding) {
        item {
            SettingsPageIntro(
                SettingsPage.Glass.title,
                "调节升级前的玻璃参数，修改后即时预览。",
            )
        }
        item { SettingsSectionTitle("实时预览", SettingsIcons.Material) }
        item {
            SettingsCard {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    GlassMaterialPreview(
                        settings = previewSettings,
                        dark = appearance.dark,
                        enabled = previewAvailable,
                    )
                }
            }
        }
        if (!previewAvailable) item { SettingsHint("当前玻璃渲染已停用，调节值会在恢复后生效。") }
        item { SettingsSectionTitle("调节", SettingsIcons.Tune) }
        item {
            SettingsCard {
                ToggleSetting(
                    title = "凸透镜折射",
                    summary = "增强静止玻璃的立体折射感",
                    value = previewSettings.convexLensEnabled,
                    leadingIcon = SettingsIcons.Material,
                    onChange = { enabled ->
                        previewSettings = previewSettings.copy(convexLensEnabled = enabled)
                        onChange("凸透镜折射") {
                            it.copy(glassMaterial = it.glassMaterial.copy(convexLensEnabled = enabled))
                        }
                    },
                )
            }
        }
        item {
            SettingsCard {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassMaterialSlider(
                        title = "表面填充透明度",
                        value = previewSettings.secondLayerAlpha ?: 0f,
                        range = SettingsLimits.glassSecondLayerAlpha.toFloatRange(),
                        valueLabel = { "${(it * 100f).roundToInt()}%" },
                        minLabel = "0%",
                        maxLabel = "100%",
                        onValueChange = { previewSettings = previewSettings.copy(secondLayerAlpha = it) },
                        onValueChangeFinished = {
                            val next = previewSettings.secondLayerAlpha
                            val current = currentSettings.secondLayerAlpha ?: referencePreset.surfaceAlpha
                            if (next != null && next != current) {
                                onChange("表面填充透明度") {
                                    it.copy(glassMaterial = it.glassMaterial.copy(secondLayerAlpha = next))
                                }
                            }
                        },
                    )
                    GlassMaterialSlider(
                        title = "扩散高光透明度",
                        value = previewSettings.thirdLayerAlpha ?: 0f,
                        range = SettingsLimits.glassThirdLayerAlpha.toFloatRange(),
                        valueLabel = { "${(it * 100f).roundToInt()}%" },
                        minLabel = "0%",
                        maxLabel = "100%",
                        onValueChange = { previewSettings = previewSettings.copy(thirdLayerAlpha = it) },
                        onValueChangeFinished = {
                            val next = previewSettings.thirdLayerAlpha
                            val current = currentSettings.thirdLayerAlpha ?: referencePreset.highlightAlpha
                            if (next != null && next != current) {
                                onChange("扩散高光透明度") {
                                    it.copy(glassMaterial = it.glassMaterial.copy(thirdLayerAlpha = next))
                                }
                            }
                        },
                    )
                    GlassMaterialSlider(
                        title = "背景模糊半径",
                        value = previewSettings.blurRadiusDp ?: referencePreset.blur,
                        range = SettingsLimits.glassBlurRadiusDp.toFloatRange(),
                        valueLabel = { "${(it * 10f).roundToInt() / 10f} dp" },
                        minLabel = "0 dp",
                        maxLabel = "${SettingsLimits.glassBlurRadiusDp.endInclusive.toInt()} dp",
                        onValueChange = { previewSettings = previewSettings.copy(blurRadiusDp = it) },
                        onValueChangeFinished = {
                            val next = previewSettings.blurRadiusDp
                            val current = currentSettings.blurRadiusDp ?: referencePreset.blur
                            if (next != null && next != current) {
                                onChange("背景模糊半径") {
                                    it.copy(glassMaterial = it.glassMaterial.copy(blurRadiusDp = next))
                                }
                            }
                        },
                    )
                    if (currentSettings != GlassMaterialSettings()) {
                        CatalogTextButton(
                            text = "恢复默认参数",
                            onClick = hapticClick {
                                previewSettings = initialValues
                                onChange("玻璃预览参数") {
                                    it.copy(glassMaterial = GlassMaterialSettings())
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun ClosedFloatingPointRange<Double>.toFloatRange(): ClosedFloatingPointRange<Float> =
    start.toFloat()..endInclusive.toFloat()

@Composable
private fun GlassMaterialPreview(
    settings: GlassMaterialSettings,
    dark: Boolean,
    enabled: Boolean,
) {
    val previewBackground = MiuixTheme.colorScheme.surfaceContainerHigh
    val backdrop = rememberLayerBackdrop {
        drawRect(previewBackground)
        drawContent()
    }
    val shape = com.ohmyfly.shapes.AppCorners.shape(com.ohmyfly.shapes.AppCorners.Content)
    Box(Modifier.fillMaxWidth().height(224.dp).clip(shape)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                listOf(Color(0xFF172B67), Color(0xFF167F91), Color(0xFFFF8E63)),
            )))
            Box(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 34.dp)
                .size(82.dp).clip(CircleShape).background(Color(0xFFFFD166)))
            Box(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 45.dp)
                .size(66.dp).clip(CircleShape).background(Color(0xFFEF476F)))
            Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp)) {
                Text("Liquid Glass", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("折射与高光实时预览", color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(Color.White, Color(0xFF06D6A0), Color(0xFFFFD166), Color(0xFFEF476F)).forEach { color ->
                        Box(Modifier.size(width = 24.dp, height = 5.dp).clip(CircleShape).background(color))
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).chromeMaterial(
                    backdrop, enabled, dark, MaterialMode.LiquidGlass, NavigationBarShape, settings,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatalogToolbarAction("搜索", SettingsIcons.Search, {})
                Text(
                    "工具栏",
                    Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                CatalogToolbarAction("设置", com.ohmyfly.ui.DriftIcons.Settings, {})
            }
            Row(
                Modifier.fillMaxWidth().height(62.dp).chromeMaterial(
                    backdrop, enabled, dark, MaterialMode.LiquidGlass, NavigationBarShape, settings,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewNavigationItem(com.ohmyfly.ui.DriftIcons.Overview, "概览", true, Modifier.weight(1f))
                PreviewNavigationItem(com.ohmyfly.ui.DriftIcons.Flights, "航班", false, Modifier.weight(1f))
                PreviewNavigationItem(com.ohmyfly.ui.DriftIcons.Nodes, "塔台", false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewNavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        )
        Text(
            label,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun GlassMaterialSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    minLabel: String,
    maxLabel: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SettingsName(title)
            Text(
                text = valueLabel(value),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                fontSize = 14.sp,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = 0,
            hapticEffect = SliderDefaults.SliderHapticEffect.None,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = title
                stateDescription = valueLabel(value)
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(minLabel, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(maxLabel, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
