package com.ohmyfly.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class FlightConfigurationTest {
    @Test
    fun rawFieldsKeepDatabaseOrderAndNeverRepeatNodeSummaryKeys() {
        val value = Json.parseToJsonElement(
            """{"type":"http","server":"edge.example","port":443,"contentHash":"hash","fields":{"name":"edge","type":"http","server":"edge.example","port":443,"tls":true}}""",
        ).jsonObject

        val fields = flightConfigurationFields(value)

        assertEquals(listOf("name", "type", "server", "port", "tls"), fields.map { it.first })
    }
}
