package com.ohmyfly.nativecore

import com.ohmyfly.nativecore.bridge.MobileParseSubscription
import com.ohmyfly.nativecore.bridge.MobileVersion
import kotlinx.cinterop.ExperimentalForeignApi

/** iOS 侧调用 Go native-core（NativeCore.xcframework）的入口。 */
@OptIn(ExperimentalForeignApi::class)
object NativeCore {
    fun version(): String = MobileVersion()

    @Throws(NativeCoreException::class)
    fun parseSubscription(content: String): NativeSubscription =
        decodeNativeSubscriptionResult(MobileParseSubscription(content))
}
