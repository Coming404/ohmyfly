// 验证用量头的缺失、合法零值和数值边界。
package com.ohmyfly.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionUsageTest {
    @Test
    fun parsesUnorderedFieldsAndWhitespaceWithExpiryInSeconds() {
        assertEquals(
            SubscriptionUsage(upload = 12, download = 34, total = 1024, expireEpochSeconds = 1_800_000_000),
            parseSubscriptionUserInfo(" expire = 1800000000 ; total=1024; download = 34 ; upload=12 "),
        )
    }

    @Test
    fun preservesAllExplicitZeroValues() {
        assertEquals(
            SubscriptionUsage(0, 0, 0, 0),
            parseSubscriptionUserInfo("upload=0;download=0;total=0;expire=0"),
        )
    }

    @Test
    fun missingFieldsStayNull() {
        assertEquals(SubscriptionUsage(download = 42), parseSubscriptionUserInfo("download=42"))
        assertEquals(SubscriptionUsage(expireEpochSeconds = 0), parseSubscriptionUserInfo("expire=0"))
    }

    @Test
    fun laterValidDuplicateWinsButInvalidDuplicateDoesNotEraseIt() {
        assertEquals(
            SubscriptionUsage(upload = 9, total = 0),
            parseSubscriptionUserInfo("upload=1;upload=9;upload=bad;total=3;total=0;total=-1"),
        )
    }

    @Test
    fun rejectsNegativeOverflowAndMalformedValuesWithoutLosingValidFields() {
        assertEquals(
            SubscriptionUsage(download = 7),
            parseSubscriptionUserInfo("upload=-1;total=9223372036854775808;expire=1.2;download=7"),
        )
        assertNull(parseSubscriptionUserInfo("upload=;download=1=2;total=0x10;expire=1e9"))
    }

    @Test
    fun ignoresUnknownAndCaseMismatchedKeys() {
        assertNull(parseSubscriptionUserInfo("unknown=123;UPLOAD=4;Download=5;Total=6;Expire=7"))
        assertEquals(SubscriptionUsage(upload = 8), parseSubscriptionUserInfo("unknown=12;upload=8"))
    }

    @Test
    fun returnsNullWhenNoRecognizedValidValueExists() {
        for (header in listOf(null, "", " ; ; ", "upload", "=9", "upload=-9223372036854775809")) {
            assertNull(parseSubscriptionUserInfo(header))
        }
    }

    @Test
    fun acceptsTheLongBoundaryAndSignedDecimalWithoutDerivedArithmetic() {
        assertEquals(
            SubscriptionUsage(upload = Long.MAX_VALUE, download = Long.MAX_VALUE, total = 7),
            parseSubscriptionUserInfo("upload=9223372036854775807;download=9223372036854775807;total=+7"),
        )
    }
}
