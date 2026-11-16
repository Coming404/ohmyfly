package com.ohmyfly.nativecore

// 从 XCFramework 中的真实 Go 桥接读取构建标识。
internal actual fun nativeCoreVersion(): String = NativeCore.version()

internal actual fun parseSubscriptionContent(content: String): NativeSubscription =
    NativeCore.parseSubscription(content)
