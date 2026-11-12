package com.ohmyfly.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogWindowTest {
    @Test
    fun distantJumpReverseReadAndMemoryTrimKeepOnlyBoundedWindow() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = CatalogStore(StandardTestDispatcher(testScheduler), { raw ->
                val request = catalogJson.parseToJsonElement(raw).jsonObject
                when (request.getValue("action").jsonPrimitive.content) {
                    "summary" -> """{"revision":1,"towers":1200}"""
                    "beginFilter" -> """{"token":1,"revision":1}"""
                    "filterInputs" -> """{"inputs":[]}"""
                    "commitFilter" -> """{"count":0}"""
                    "page" -> {
                        val anchor = request["anchor"]?.jsonPrimitive?.long ?: 0
                        val back = request["backwards"]?.jsonPrimitive?.boolean ?: false
                        val ids = (1L..1200L).filter { if (back) it < anchor else it > anchor }
                            .let { if (back) it.takeLast(100) else it.take(100) }
                        buildJsonObject {
                            put("revision", 1)
                            put("towers", JsonArray(ids.map { id -> buildJsonObject {
                                put("id", id); put("name", "tower-$id"); put("nodeCount", 0); put("failure", "")
                            } }))
                        }.toString()
                    }
                    else -> error("unexpected request")
                }
            }, { "{}" })
            store.show("towers")
            advanceUntilIdle()
            assertEquals(1200, store.state.value.towerMatchCount)
            store.page("towers", 10)
            advanceUntilIdle()
            assertEquals(1001, store.state.value.towers.first().id)
            store.page("towers", 2)
            advanceUntilIdle()
            assertEquals(201, store.state.value.towers.first().id)
            for (page in 3..9) { store.page("towers", page); advanceUntilIdle() }
            assertTrue(store.state.value.towerWindow.size <= 5)
            store.release(background = false)
            assertEquals(setOf(9), store.state.value.towerWindow.keys)
            store.release(background = true)
            assertTrue(store.state.value.towerWindow.isEmpty())
            assertTrue(store.state.value.towers.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
