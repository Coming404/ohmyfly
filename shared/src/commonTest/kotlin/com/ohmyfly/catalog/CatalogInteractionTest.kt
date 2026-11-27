// 验证保存确认不依赖后续读取成功，以及隐藏航班页不会占用筛选写入资源。
package com.ohmyfly.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogInteractionTest {
    @Test
    fun savedEditsFinishBeforeIndependentRefreshFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var saved = false
            var acknowledged = false
            var callbacks = 0
            val store = CatalogStore(StandardTestDispatcher(testScheduler), { raw ->
                val action = catalogJson.parseToJsonElement(raw).jsonObject.getValue("action").jsonPrimitive.content
                when (action) {
                    "summary" -> if (saved) {
                        assertTrue(acknowledged, "保存回调不能等待后续读取")
                        """{"error":"read failed"}"""
                    } else """{"revision":1,"towers":0}"""
                    "groups" -> """{"revision":1,"groups":[],"counts":{"all":0}}"""
                    "page" -> """{"revision":1,"towers":[]}"""
                    "recordTrend" -> "{}"
                    "groupCreate", "nodeChange" -> { saved = true; "{}" }
                    else -> error("隐藏航班页不应创建筛选：$action")
                }
            }, { "{}" })
            store.show("towers")
            advanceUntilIdle()
            assertNull(store.state.value.failure)
            assertEquals(0L, store.state.value.filterToken)
            val result: (Boolean, String) -> Unit = { success, _ ->
                assertTrue(success)
                acknowledged = true
                callbacks++
            }
            store.changeGroup("groupCreate", name = "新分组", onResult = result)
            advanceUntilIdle()
            assertEquals(1, callbacks)
            assertNotNull(store.state.value.failure)
            saved = false
            store.refreshView()
            acknowledged = false
            store.changeNodes(setOf(1), "disable", result)
            advanceUntilIdle()
            assertEquals(2, callbacks)
            assertNotNull(store.state.value.failure)
        } finally { Dispatchers.resetMain() }
    }
}
