package com.ohmyfly.catalog

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import java.io.File

private lateinit var catalogContext: Context

public fun initializeCatalog(context: Context) {
    catalogContext = context.applicationContext
    com.ohmyfly.ui.catalogClipboardContext = catalogContext
    catalogContext.registerComponentCallbacks(object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() { appCatalog.release(background = false) }
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) appCatalog.release(background = false)
        }
    })
}

internal actual fun openNativeCatalog(): String {
    com.ohmyfly.logging.logger.info { "[原生核心] Android 构建版本：${com.ohmyfly.nativecore.nativeCoreVersion()}" }
    val directory = File(catalogContext.noBackupFilesDir, "catalog")
    check(directory.isDirectory || directory.mkdirs()) { "无法建立目录数据库文件夹" }
    return mobile.Mobile.openCatalog(File(directory, "catalog.sqlite").absolutePath)
}

internal actual fun nativeCatalog(request: String): String = mobile.Mobile.catalog(request)
