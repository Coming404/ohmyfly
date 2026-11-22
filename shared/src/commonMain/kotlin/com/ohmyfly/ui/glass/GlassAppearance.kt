// Miuix 玻璃的可用性和跨平台光照策略。
package com.ohmyfly.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.ohmyfly.ui.LocalAppMotionAllowed
import com.ohmyfly.ui.glass.sensor.rememberGlassGravity
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/** 系统或应用要求降低透明度时，始终使用不透明表面。 */
internal fun glassEffectsEnabled(
    reduceTransparency: Boolean,
    shaderSupported: Boolean,
    systemReduceTransparency: Boolean,
): Boolean = !reduceTransparency && !systemReduceTransparency && shaderSupported

/** 使用材质库提供的双端能力判断，不保留旧引擎探测。 */
internal fun glassShaderSupported(): Boolean = isRuntimeShaderSupported()

/** Miuix 默认 Style 仅提供 Bloom 的方向光，Clear face 与模糊由 Clear pipeline 处理。 */
@Composable
internal fun rememberGlassStyle(enabled: Boolean, dark: Boolean): State<GlassStyle> {
    val gravity = rememberGlassGravity(enabled && LocalAppMotionAllowed.current)
    val base = GlassDefaults.style(dark)
    return remember(base, gravity) {
        derivedStateOf {
            val tilt = gravity.value
            base.copy(light = base.light.copy(
                directionX = base.light.directionX + tilt.x,
                directionY = base.light.directionY - tilt.y,
            ))
        }
    }
}
