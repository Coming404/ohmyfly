// 保留 Miuix 的显式圆角开关，矢量连续曲率本身不依赖运行时着色器。
package top.yukonga.miuix.kmp.squircle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSquircleEnabled = staticCompositionLocalOf { true }

@Composable
@ReadOnlyComposable
fun isSquircleEnabled(): Boolean = LocalSquircleEnabled.current
