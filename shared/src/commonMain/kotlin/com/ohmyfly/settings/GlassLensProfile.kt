// 凸透镜三档光学参数共用同一黄金比例倍率，档位独立于按压状态。
package com.ohmyfly.settings

/** 长度采用逻辑 dp，由渲染边界转换成对应平台的像素。 */
internal enum class GlassLensProfile(
    val label: String,
    val bezelWidthDp: Float,
    val thicknessDp: Float,
) {
    Pill("pill", 10f, 38f),
    Control("control", 16f, 62f),
    Card("card", 26f, 100f),
}
