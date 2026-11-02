package com.ohmyfly.nativecore

/** 调用 Go native-core 解析订阅内容；失败抛出 NativeCoreException。 */
internal expect fun parseSubscriptionContent(content: String): NativeSubscription
