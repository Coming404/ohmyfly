// 连接详情只呈现数据库保存的原始字段集合，避免再叠加节点摘要字段。
package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import top.yukonga.miuix.kmp.basic.Text

internal fun flightConfigurationFields(value: JsonObject): List<Pair<String, JsonElement>> {
    (value["fields"] as? JsonObject)?.takeIf { it.isNotEmpty() }?.let { fields ->
        return fields.entries.map { it.key to it.value }
    }
    val internalFields = setOf("fields", "rawInput", "sourceFormat", "fromBase64", "contentHash", "source", "eligible", "sources", "group")
    return value.entries.filter { it.key !in internalFields }.map { it.key to it.value }
}

@Composable
internal fun FlightConfiguration(value: JsonObject) {
    val entries = flightConfigurationFields(value)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (key, content) ->
            Text("$key: ${nodeValue(content)}", autoSize = CatalogTextSizing.Body)
        }
    }
}

private fun nodeValue(value: JsonElement): String = value.toString()
