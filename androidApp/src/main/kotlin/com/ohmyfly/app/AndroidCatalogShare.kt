// 仅分享应用私有缓存中的 YAML，通过临时 URI 读取授权交给系统分享页。
package com.ohmyfly.app

import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal suspend fun ComponentActivity.shareCatalogYaml(text: String) {
    val file = withContext(Dispatchers.IO) {
        val directory = File(cacheDir, "catalog-exports")
        check(directory.isDirectory || directory.mkdirs()) { "无法建立导出目录" }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            ?.forEach { it.delete() }
        File(directory, "mihomo-${UUID.randomUUID()}.yaml").apply { writeText(text) }
    }
    val uri = FileProvider.getUriForFile(this, "$packageName.catalog.exports", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/yaml"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Mihomo YAML", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "分享 Mihomo YAML"))
}
