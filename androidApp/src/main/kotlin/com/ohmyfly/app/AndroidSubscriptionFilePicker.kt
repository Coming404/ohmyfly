package com.ohmyfly.app

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ohmyfly.subscription.SubscriptionFile
import com.ohmyfly.subscription.SubscriptionFilePicker
import com.ohmyfly.subscription.SubscriptionFileSelection
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal class AndroidSubscriptionFilePicker(private val activity: ComponentActivity) : SubscriptionFilePicker {
    private var pending: CancellableContinuation<List<Uri>>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pending?.let { if (it.isActive) it.resume(uris) }
        pending = null
    }

    override suspend fun pick(maxFiles: Int): List<SubscriptionFileSelection> {
        val uris = suspendCancellableCoroutine<List<Uri>> { continuation ->
            check(pending == null) { "文件选择器已打开" }
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            launcher.launch(arrayOf("*/*"))
        }.distinct()
        require(uris.size <= maxFiles) { "最多选择 $maxFiles 个文件" }
        return withContext(Dispatchers.IO) {
            uris.map { uri ->
                var name = "导入文件"
                activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
                }
                SubscriptionFileSelection(name) { maxBytes -> read(uri, maxBytes) }
            }
        }
    }

    // 选择时只读取名称，正文受共享来源任务并发限制。
    private suspend fun read(uri: Uri, maxBytes: Int): SubscriptionFile = withContext(Dispatchers.IO) {
            val resolver = activity.contentResolver
            var name = "导入文件"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    require(cursor.isNull(1) || cursor.getLong(1) <= maxBytes) { "文件超过大小上限" }
                }
            }
            val bytes = ByteArrayOutputStream()
            val input = requireNotNull(resolver.openInputStream(uri)) { "无法读取所选文件" }
            input.use {
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = it.read(buffer)
                    if (count < 0) break
                    require(bytes.size().toLong() + count <= maxBytes) { "文件超过大小上限" }
                    bytes.write(buffer, 0, count)
                }
            }
            SubscriptionFile(name, bytes.toByteArray().decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF"))
    }
}
