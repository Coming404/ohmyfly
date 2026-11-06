// 复制单条已脱敏日志，不自动访问剪贴板内容。
package com.ohmyfly.logging

import platform.UIKit.UIPasteboard

internal actual fun copyLogText(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
