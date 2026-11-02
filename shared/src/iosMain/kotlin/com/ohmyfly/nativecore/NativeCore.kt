package com.ohmyfly.nativecore

import com.ohmyfly.nativecore.bridge.MobileParseSubscription
import com.ohmyfly.nativecore.bridge.MobileVersion
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError

class NativeCoreException(message: String) : Exception(message)

/** iOS 侧调用 Go native-core（NativeCore.xcframework）的入口。 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object NativeCore {
    fun version(): String = MobileVersion()

    @Throws(NativeCoreException::class)
    fun parseSubscription(content: String): NativeSubscription {
        val json = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val result = MobileParseSubscription(content, error.ptr)
            error.value?.let { throw NativeCoreException(it.localizedDescription) }
            result
        }
        return decodeNativeSubscription(json)
    }
}
