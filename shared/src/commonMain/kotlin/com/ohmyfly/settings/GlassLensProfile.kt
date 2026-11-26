// 凸透镜三档光学参数共用同一黄金比例倍率，档位独立于按压状态。
package com.ohmyfly.settings

/** 长度采用逻辑 dp，由渲染边界转换成对应平台的像素。 */
internal enum class GlassLensProfile(
    val label: String,
    val bezelWidthDp: Float,
    val thicknessDp: Float,
    val blur: Float,
    val lightFillAlpha: Float,
    val darkFillAlpha: Float,
    val refractionScale: Float,
) {
    Pill("pill", 10f, 38f, 0.2f, 0.25f, 0.03f, 0.618f),
    Control("control", 16f, 62f, 0.35f, 0.35f, 0.06f, 0.618f),
    Card("card", 26f, 100f, 0.5f, 0.45f, 0.05f, 0.618f),
}
