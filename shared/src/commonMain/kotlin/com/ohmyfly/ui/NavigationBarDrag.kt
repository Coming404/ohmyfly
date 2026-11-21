// 主导航拖动沿布局方向预览目的地，松手后提交有效选择。
package com.ohmyfly.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** 初始事件阶段只接管横向越过 touch slop 的拖动，普通点击仍交给单项 selectable。 */
internal fun Modifier.navigationBarDrag(
    itemCount: Int,
    horizontalInsetPx: Float,
    layoutDirection: LayoutDirection,
    dragging: MutableState<Boolean>,
    dragIndex: MutableFloatState,
    selectedIndex: State<Int>,
    onSelect: State<(Int) -> Unit>,
): Modifier = pointerInput(itemCount, horizontalInsetPx, layoutDirection) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val start = down.position
        var active = false
        try {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (active) {
                        val index = navigationPosition(
                            change.position.x, size.width.toFloat(), horizontalInsetPx, itemCount, layoutDirection,
                        ).roundToInt().coerceIn(0, itemCount - 1)
                        dragging.value = false
                        if (index != selectedIndex.value) onSelect.value(index)
                        change.consume()
                    }
                    break
                }
                if (!active) {
                    val dx = change.position.x - start.x
                    val dy = change.position.y - start.y
                    if (max(abs(dx), abs(dy)) > viewConfiguration.touchSlop) {
                        if (abs(dx) <= abs(dy)) break
                        active = true
                        dragging.value = true
                    }
                }
                if (active) {
                    dragIndex.floatValue = navigationPosition(
                        change.position.x, size.width.toFloat(), horizontalInsetPx, itemCount, layoutDirection,
                    )
                    change.consume()
                }
            }
        } finally {
            dragging.value = false
        }
    }
}

/** 用槽位中心作为零点并换算 RTL，保证胶囊连续跟手、释放时可就近吸附。 */
private fun navigationPosition(
    x: Float,
    width: Float,
    horizontalInset: Float,
    itemCount: Int,
    layoutDirection: LayoutDirection,
): Float {
    val logicalX = if (layoutDirection == LayoutDirection.Ltr) x else width - x
    val slotWidth = ((width - horizontalInset * 2f) / itemCount).coerceAtLeast(1f)
    return ((logicalX - horizontalInset) / slotWidth - 0.5f).coerceIn(0f, (itemCount - 1).toFloat())
}
