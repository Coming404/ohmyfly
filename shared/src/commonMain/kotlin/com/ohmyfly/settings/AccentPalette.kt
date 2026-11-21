// Monet 使用的种子色选择；自动项由平台提供种子或回落产品色。
package com.ohmyfly.settings

internal enum class AccentPalette(
    val label: String,
    val seedArgb: Long?,
) {
    Automatic("自动", null),
    Crimson("绯红", 0xFFE03030),
    Violet("紫罗兰", 0xFF7C4DFF),
    Ocean("海洋", 0xFF2196C6),
    Sage("鼠尾草", 0xFF6B9E6B),
    Amber("琥珀", 0xFFE0A030),
    Rose("玫瑰", 0xFFE0507A),
    Miyazaki("宫崎骏", 0xFF638B58),
}
