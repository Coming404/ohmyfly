package com.ohmyfly.nativecore

/** 从实际链接的 Go 库读取构建时注入的源码提交号。 */
internal expect fun nativeCoreVersion(): String

/** 调用 Go native-core 解析订阅内容；失败抛出 NativeCoreException。 */
internal expect fun parseSubscriptionContent(content: String): NativeSubscription
