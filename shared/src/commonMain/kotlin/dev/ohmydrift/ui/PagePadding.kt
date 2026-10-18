package dev.ohmydrift.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Keep the viewport full-screen; reserve floating controls only inside the scroll content. */
internal fun PaddingValues.withPageSpacing(
    direction: LayoutDirection,
    horizontal: Dp = 0.dp,
    vertical: Dp = 12.dp,
): PaddingValues = PaddingValues(
    start = calculateStartPadding(direction) + horizontal,
    top = calculateTopPadding() + vertical,
    end = calculateEndPadding(direction) + horizontal,
    bottom = calculateBottomPadding() + vertical,
)
