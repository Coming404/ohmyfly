@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ohmyfly.catalog

import com.ohmyfly.nativecore.bridge.MobileCatalog
import com.ohmyfly.nativecore.bridge.MobileOpenCatalog
import platform.Foundation.*
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.darwin.NSObjectProtocol

private val catalogObservers = mutableListOf<NSObjectProtocol>()

internal actual fun openNativeCatalog(): String {
    com.ohmyfly.logging.logger.debug { "[原生核心] iOS 构建版本：${com.ohmyfly.nativecore.nativeCoreVersion()}" }
    val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .first() as String
    val path = "$base/catalog"
    val manager = NSFileManager.defaultManager
    check(manager.createDirectoryAtPath(path, true,
        mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication), null))
    check(NSURL.fileURLWithPath(path).setResourceValue(true, NSURLIsExcludedFromBackupKey, null))
    check(manager.setAttributes(mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication), path, null))
    return MobileOpenCatalog("$path/catalog.sqlite")
}

public fun initializeCatalog() {
    if (catalogObservers.isNotEmpty()) return
    val center = NSNotificationCenter.defaultCenter
    catalogObservers += center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
        appCatalog.resume()
    }
    listOf(UIApplicationDidEnterBackgroundNotification, UIApplicationDidReceiveMemoryWarningNotification).forEach { name ->
        catalogObservers += center.addObserverForName(name, null, NSOperationQueue.mainQueue) {
            appCatalog.release(background = name == UIApplicationDidEnterBackgroundNotification)
        }
    }
}

internal actual fun nativeCatalog(request: String): String = MobileCatalog(request)
