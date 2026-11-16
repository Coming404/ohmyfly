package com.ohmyfly.nativecore

// 从 AAR 中的真实 Go 桥接读取构建标识。
internal actual fun nativeCoreVersion(): String = mobile.Mobile.version()

internal actual fun parseSubscriptionContent(content: String): NativeSubscription =
    decodeNativeSubscriptionResult(mobile.Mobile.parseSubscription(content))
