// 目录展开使用淡入与垂直尺寸过渡；减少动态效果时直接切换内容。
package com.ohmyfly.ui

import androidx.compose.animation.*
import androidx.compose.runtime.Composable

@Composable
internal fun CatalogExpansion(expanded: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    val motion = LocalAppMotionAllowed.current
    AnimatedVisibility(visible = expanded,
        enter = if (motion) fadeIn() + expandVertically() else EnterTransition.None,
        exit = if (motion) fadeOut() + shrinkVertically() else ExitTransition.None,
        content = content)
}
