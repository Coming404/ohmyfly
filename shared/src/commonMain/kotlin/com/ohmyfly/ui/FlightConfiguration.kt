package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun FlightConfiguration(value: JsonObject) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("连接信息")
        listOf("name" to "名称", "type" to "协议", "server" to "服务器", "port" to "端口",
            "sourceFormat" to "解析格式").forEach { (key, label) ->
            (value[key] as? JsonPrimitive)?.contentOrNull?.let { Text("$label：$it") }
        }
        val fields = value["fields"] as? JsonObject
        listOf("network" to "传输", "tls" to "TLS", "cipher" to "加密方式", "udp" to "UDP").forEach { (key, label) ->
            (fields?.get(key) as? JsonPrimitive)?.contentOrNull?.let { Text("$label：$it") }
        }
        Text("来源")
        val sources = value["sources"] as? JsonArray
        if (sources.isNullOrEmpty()) Text("暂无来源")
        sources?.forEach { item ->
            val source = item.jsonObject
            val metadata = source["metadata"]?.jsonObject
            Text("塔台 ${source["towerId"]?.jsonPrimitive?.content} · ${metadata?.get("name")?.jsonPrimitive?.content.orEmpty()}")
            metadata?.get("format")?.jsonPrimitive?.content?.let { Text("来源格式：$it") }
        }
    }
}
