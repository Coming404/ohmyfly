// SPDX-License-Identifier: Apache-2.0
// Small geometry helpers for the shared Melox-derived glass navigation.
package com.ohmyfly.ui.glass

import kotlin.math.floor

/** The four-dp inset is physical on both ends; indices always stay in logical tab order. */
internal fun glassTabWidth(width: Float, inset: Float, count: Int): Float =
    if (count > 0) ((width - 2f * inset) / count).coerceAtLeast(0f) else 0f

internal fun glassTabAt(x: Float, width: Float, inset: Float, count: Int, ltr: Boolean): Int {
    if (count <= 1) return 0
    val tabWidth = glassTabWidth(width, inset, count)
    if (tabWidth <= 0f) return 0
    val logicalX = if (ltr) x else width - x
    return floor((logicalX - inset) / tabWidth).toInt().coerceIn(0, count - 1)
}

internal fun glassTabOffset(value: Float, tabWidth: Float, ltr: Boolean): Float =
    value * tabWidth * if (ltr) 1f else -1f
