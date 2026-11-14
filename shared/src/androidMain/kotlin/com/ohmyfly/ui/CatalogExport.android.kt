package com.ohmyfly.ui

import android.content.Context

internal lateinit var catalogClipboardContext: Context
private var catalogFileSharing: (suspend (String) -> Unit)? = null

// Activity 持有系统分享入口，销毁时解除引用。
public fun initializeCatalogFileSharing(handler: (suspend (String) -> Unit)?) { catalogFileSharing = handler }

internal actual suspend fun shareCatalogFile(text: String) {
    checkNotNull(catalogFileSharing) { "文件分享入口尚未就绪" }(text)
}

internal actual fun copyCatalogText(text: String) {
    catalogClipboardContext.getSystemService(android.content.ClipboardManager::class.java)
        .setPrimaryClip(android.content.ClipData.newPlainText("Mihomo YAML", text))
}
