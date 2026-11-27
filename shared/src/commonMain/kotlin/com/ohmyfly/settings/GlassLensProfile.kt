// 凸透镜三档共享同一参考图配方与黄金比例倍率，档位只控制镜片物理尺度。
package com.ohmyfly.settings

/** 长度采用逻辑 dp，由渲染边界转换成对应平台的像素。 */
internal enum class GlassLensProfile(
    val label: String,
    val bezelWidthDp: Float,
    val thicknessDp: Float,
    val refractionScale: Float,
    val refractionStrength: Float = 0.70f,
    val depthFraction: Float = 0.50f,
    val dispersion: Float = 0.22f,
    val recipeBlurDp: Float = 6f,
) {
    Pill("pill", 10f, 38f, 0.618f),
    Control("control", 16f, 62f, 0.618f),
    Card("card", 26f, 100f, 0.618f);

    /** 将折射70映射为归一化内移增益0.14；这是本渲染器的映射尺度。 */

    val interiorRefractionGain: Float get() = 0.2f * refractionStrength
    val effectiveThicknessDp: Float get() = thicknessDp * depthFraction
}
