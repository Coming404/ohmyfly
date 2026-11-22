// 液态玻璃参数以共享预览即时反馈，结束滑动后再保存。
package com.ohmyfly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.settings.AppSettings
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
import com.ohmyfly.ui.glass.rememberChromeRecording
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
                        preset = referencePreset,
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
    preset: com.ohmyfly.ui.glass.GlassPreset,
    dark: Boolean,
    enabled: Boolean,
) {
    val previewBackground = MiuixTheme.colorScheme.surfaceContainerHigh
    val recording = rememberChromeRecording(MaterialMode.LiquidGlass) {
        drawRect(previewBackground)
        drawContent()
    }
    val shape = NavigationBarShape
    Box(
        Modifier.fillMaxWidth().height(164.dp)
            .clip(com.ohmyfly.shapes.AppCorners.shape(com.ohmyfly.shapes.AppCorners.Content)),
    ) {
        Box(Modifier.fillMaxSize().then(recording.record)) {
            Row(Modifier.fillMaxSize()) {
                PreviewContent(Color(0xFF4F746A), "航班", "东京 23 毫秒", Modifier.weight(1f))
                PreviewContent(Color(0xFFE6BE74), "概览", "今日 08:42", Modifier.weight(1.2f))
                PreviewContent(Color(0xFFAD725F), "塔台", "12 个来源", Modifier.weight(1f))
            }
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.88f).height(70.dp)
                    .chromeMaterial(
                        backdrop = recording.backdrop,
                        enabled = enabled,
                        dark = dark,
                        materialMode = MaterialMode.LiquidGlass,
                        shape = shape,
                        materialSettings = settings,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("玻璃预览", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PreviewContent(color: Color, heading: String, detail: String, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().background(color).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(heading, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(detail, color = Color.White, fontSize = 9.sp, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Color(0xFFF5D47C), Color(0xFFEB8A67), Color(0xFF77A992)).forEach { swatch ->
                Box(Modifier.weight(1f).height(18.dp).clip(CircleShape).background(swatch))
            }
        }
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
