package com.ohmyfly.subscription

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public data class SubscriptionFile(val name: String, val content: String)

// 队列只持有本次选择的引用；正文在取得来源任务名额后才读取。
public class SubscriptionFileSelection(public val name: String, public val read: suspend (Int) -> SubscriptionFile)

public interface SubscriptionFilePicker {
    public suspend fun pick(maxFiles: Int): List<SubscriptionFileSelection>
}

internal fun subscriptionAddresses(text: String, limit: Int): List<String> {
    val addresses = text.lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
    require(addresses.isNotEmpty() && addresses.size <= limit) { "每批需为 1–$limit 个地址" }
    addresses.forEachIndexed { index, address ->
        require(address.length <= 8192 && validateSubscriptionUrl(address) == null) { "第 ${index + 1} 行订阅地址无效" }
    }
    return addresses
}

internal data class SubscriptionBody(val name: String, val content: String, val kind: String)

@OptIn(ExperimentalUuidApi::class)
internal fun newBodyAddress(kind: String): String = "local:$kind:${Uuid.random()}"

internal fun validateSubscriptionBody(content: String, maxMiB: Int) {
    require(content.isNotBlank()) { "文件或正文为空" }
    require(content.length <= maxMiB * 1024 * 1024 && content.encodeToByteArray().size <= maxMiB * 1024 * 1024) {
        "文件或正文超过 $maxMiB MiB 上限"
    }
}
