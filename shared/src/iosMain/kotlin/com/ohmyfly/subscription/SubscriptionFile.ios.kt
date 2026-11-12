package com.ohmyfly.subscription

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileHandle
import platform.Foundation.NSObject
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.posix.memcpy
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
internal class IosSubscriptionFilePicker(private val presenter: () -> UIViewController) : SubscriptionFilePicker {
    private var pending: CancellableContinuation<List<NSURL>>? = null
    private val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            finish(didPickDocumentsAtURLs.map { it as NSURL })
        }
        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { finish(emptyList()) }
    }

    private fun finish(urls: List<NSURL>) {
        pending?.let { if (it.isActive) it.resume(urls) }
        pending = null
    }

    override suspend fun pick(maxFiles: Int): List<SubscriptionFileSelection> {
        val urls = suspendCancellableCoroutine<List<NSURL>> { continuation ->
            check(pending == null) { "文件选择器已打开" }
            val picker = UIDocumentPickerViewController(listOf("public.data"), UIDocumentPickerMode.UIDocumentPickerModeImport)
            pending = continuation
            picker.delegate = delegate
            picker.allowsMultipleSelection = maxFiles > 1
            continuation.invokeOnCancellation { pending = null }
            presenter().presentViewController(picker, animated = true, completion = null)
        }.distinctBy { it.absoluteString }
        require(urls.size <= maxFiles) { "最多选择 $maxFiles 个文件" }
        return urls.map { url -> SubscriptionFileSelection(url.lastPathComponent ?: "导入文件") { maxBytes -> read(url, maxBytes) } }
    }

    // 安全访问范围只覆盖实际读取，不随抽屉关闭提前释放任务所需的访问。
    private suspend fun read(url: NSURL, maxBytes: Int): SubscriptionFile = withContext(Dispatchers.Default) {
            val access = url.startAccessingSecurityScopedResource()
            try {
                val handle = requireNotNull(NSFileHandle.fileHandleForReadingAtPath(requireNotNull(url.path))) { "无法读取所选文件" }
                try {
                    val chunks = mutableListOf<ByteArray>()
                    var total = 0
                    while (true) {
                        ensureActive()
                        val data = requireNotNull(handle.readDataUpToLength(8192uL, error = null)) { "文件读取失败" }
                        if (data.length == 0uL) break
                        require(total.toLong() + data.length.toLong() <= maxBytes) { "文件超过大小上限" }
                        val chunk = ByteArray(data.length.toInt())
                        chunk.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                        chunks += chunk
                        total += chunk.size
                    }
                    val bytes = ByteArray(total)
                    var offset = 0
                    chunks.forEach { it.copyInto(bytes, offset); offset += it.size }
                    SubscriptionFile(url.lastPathComponent ?: "导入文件", bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF"))
                } finally { handle.closeFile() }
            } finally { if (access) url.stopAccessingSecurityScopedResource() }
    }
}
