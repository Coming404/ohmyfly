package com.ohmyfly.nativecore

internal actual fun parseSubscriptionContent(content: String): NativeSubscription =
    decodeNativeSubscriptionResult(mobile.Mobile.parseSubscription(content))
