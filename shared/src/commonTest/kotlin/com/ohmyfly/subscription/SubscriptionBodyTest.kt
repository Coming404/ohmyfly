package com.ohmyfly.subscription

import com.ohmyfly.nativecore.NativeSubscriptionNode
import com.ohmyfly.settings.SubscriptionSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SubscriptionBodyTest {
    @Test
    fun bodyDoesNotFetchHttpAndPreservesFilteredSourceRecords() = runTest {
        val client = subscriptionClient { error("正文导入不允许请求网络") }
        try {
            var parsedBody = ""
            val importer = SubscriptionImporter(SubscriptionFetcher(client), {
                parsedBody = it
                testSnapshot(testNode("HK"), testNode("JP"), skipped = 1)
            })
            val records = mutableListOf<NativeSubscriptionNode>()
            val result = importer.importBody("ss://body", SubscriptionSettings(includePattern = "HK")) { records += it }
            assertEquals("ss://body", parsedBody)
            assertNull(result.failure)
            assertEquals(2, result.recordCount)
            assertEquals(listOf(true, false), records.map { it.eligible })
            assertEquals(testNode("JP").contentHash, records.last().contentHash)
            assertEquals(1, result.warnings.size)
        } finally { client.close() }
    }

    @Test
    fun contentLimitsUseUtf8BytesAndLocalIdentityIsNotFilePath() {
        assertFailsWith<IllegalArgumentException> { validateSubscriptionBody(" ", 1) }
        assertFailsWith<IllegalArgumentException> { validateSubscriptionBody("中".repeat(400_000), 1) }
        validateSubscriptionBody("ss://body", 1)
        val first = newBodyAddress("file")
        assertTrue(first.startsWith("local:file:"))
        assertNotEquals(first, newBodyAddress("file"))
    }
}
