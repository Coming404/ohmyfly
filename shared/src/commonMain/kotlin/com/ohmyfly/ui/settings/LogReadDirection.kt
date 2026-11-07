// 自动加载只沿当前阅读方向前进；程序定位不会改变方向，边界处等待用户反向滚动。
package com.ohmyfly.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ohmyfly.logging.LogFileCursor

internal class LogReadDirection {
    private var forward by mutableStateOf(false)

    fun userScroll(deltaY: Float) {
        if (deltaY != 0f) forward = deltaY > 0f
    }

    fun request(atTop: Boolean, atBottom: Boolean, older: LogFileCursor?, newer: LogFileCursor?): LogFileCursor? =
        if (forward) newer?.takeIf { atTop } else older?.takeIf { atBottom }
}
