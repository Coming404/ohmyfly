// 用户点击时同步发布公共纯文本，不读取剪贴板，也不设置过期时间。
package com.ohmyfly.logging

import platform.UIKit.UIPasteboard

internal actual fun copyLogText(text: String) {
    UIPasteboard.generalPasteboard.setItems(
        listOf(mapOf("public.utf8-plain-text" to text)),
        options = emptyMap<Any?, Any>(),
    )
}
