// iOS 文件导出使用临时文件与系统分享页，完成后删除导出副本。
package com.ohmyfly.ui

import platform.Foundation.*
import platform.UIKit.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual fun copyCatalogText(text: String) { UIPasteboard.generalPasteboard.string = text }

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun shareCatalogFile(text: String) {
    val url = withContext(Dispatchers.Default) {
        val path = NSTemporaryDirectory() + "mihomo-" + kotlin.random.Random.nextLong().toString() + ".yaml"
        val bytes = text.encodeToByteArray()
        bytes.usePinned {
            check(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())
                .writeToFile(path, atomically = true)) { "无法写入 YAML 文件" }
        }
        NSURL.fileURLWithPath(path)
    }
    val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
    var controller = (scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() })?.rootViewController
        ?: error("系统分享入口尚未就绪")
    while (controller.presentedViewController != null) controller = controller.presentedViewController!!
    val activity = UIActivityViewController(listOf(url), null)
    activity.popoverPresentationController?.apply {
        sourceView = controller.view
        sourceRect = controller.view.bounds
    }
    activity.completionWithItemsHandler = { _, _, _, _ ->
        NSFileManager.defaultManager.removeItemAtURL(url, null)
    }
    controller.presentViewController(activity, true, null)
}
