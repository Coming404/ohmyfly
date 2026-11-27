// 导航页签不响应输入焦点的横向滚动，页面内列表仍保留自动避让能力。
package com.ohmyfly.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
private object NavigationBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CatalogPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    pageContent: @Composable PagerScope.(Int) -> Unit,
) {
    val pageBringIntoViewSpec = LocalBringIntoViewSpec.current
    CompositionLocalProvider(LocalBringIntoViewSpec provides NavigationBringIntoViewSpec) {
        HorizontalPager(state = state, modifier = modifier, userScrollEnabled = false) { page ->
            // 只限制分页器，垂直列表继续使用原来的输入法避让策略。
            CompositionLocalProvider(LocalBringIntoViewSpec provides pageBringIntoViewSpec) {
                pageContent(page)
            }
        }
    }
}
