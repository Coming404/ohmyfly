// 仅在用户点击复制时调用系统剪贴板。
package com.ohmyfly.logging

internal expect fun copyLogText(text: String)
