package com.ohmyfly.nativecore

internal actual fun parseSubscriptionContent(content: String): NativeSubscription =
    NativeCore.parseSubscription(content)
