package com.ohmyfly.catalog

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

internal suspend fun queryFlightFilter(revision: Long, predicate: FlightPredicate): FlightFilterResult {
    val ticket = catalogCall("beginFilter") { put("revision", revision) }
    val protocols = mutableSetOf<String>()
    var anchor = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val inputs = catalogCall("filterInputs") {
            put("filterTicket", ticket); put("anchor", anchor)
        }.getValue("inputs").jsonArray
        if (inputs.isEmpty()) break
        val decisions = inputs.map { element ->
            currentCoroutineContext().ensureActive()
            val node = element.jsonObject
            val type = node.getValue("type").jsonPrimitive.content
            protocols += type
            buildJsonObject {
                put("id", node.getValue("id"))
                put("eligible", predicate.matches(node.getValue("name").jsonPrimitive.content, type))
            }
        }
        catalogCall("stageFilter") { put("filterTicket", ticket); put("decisions", JsonArray(decisions)) }
        anchor = inputs.last().jsonObject.getValue("id").jsonPrimitive.long
    }
    currentCoroutineContext().ensureActive()
    val result = catalogCall("commitFilter") { put("filterTicket", ticket) }
    return FlightFilterResult(ticket.getValue("token").jsonPrimitive.long,
        result.getValue("count").jsonPrimitive.long, protocols)
}
