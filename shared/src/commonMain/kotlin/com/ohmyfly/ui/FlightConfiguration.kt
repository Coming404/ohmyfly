package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun FlightConfiguration(value: JsonObject) {
    val internalFields = setOf("fields", "rawInput", "sourceFormat", "fromBase64", "contentHash", "source", "eligible", "sources", "group")
    val entries = buildList {
        value.forEach { (key, item) ->
            if (key == "fields") (item as? JsonObject)?.forEach { (field, content) -> add(field to content) }
            else if (key !in internalFields) add(key to item)
        }
    }.sortedBy { it.first }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (key, content) ->
            Text("$key: ${nodeValue(content)}", autoSize = CatalogTextSizing.Body)
        }
    }
}

private fun nodeValue(value: JsonElement): String = value.toString()
