package com.ohmyfly.nativecore

// shortcut: Android 尚未链接 native-core AAR，接入后改为调用 Go 桥接。
internal actual fun parseSubscriptionContent(content: String): NativeSubscription =
    throw NativeCoreException("Android 尚未接入 native-core")
