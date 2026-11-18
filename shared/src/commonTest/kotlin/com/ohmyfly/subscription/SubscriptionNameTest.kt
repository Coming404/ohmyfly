// 验证订阅名称头优先级、引号转义和严格编码回退。
package com.ohmyfly.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionNameTest {
    @Test
    fun plainProfileTitleWinsAndOnlyOuterWhitespaceIsRemoved() {
        assertEquals("我的  订阅.yaml", parseSubscriptionName("  我的  订阅.yaml  ", "attachment; filename=other.yaml"))
    }

    @Test
    fun decodesBase64ProfileTitleAsUtf8() {
        assertEquals("中文", parseSubscriptionName("base64:5Lit5paH", "attachment; filename=other"))
    }

    @Test
    fun invalidBase64OrUtf8FallsBackToDisposition() {
        for (title in listOf("base64:!!!", "base64:/w==", "base64:", "base64:IA==", "\nbase64:5Lit5paH")) {
            assertEquals("fallback.yaml", parseSubscriptionName(title, "attachment; filename=fallback.yaml"))
        }
    }

    @Test
    fun utf8ExtendedFilenameWithLanguageWinsOverPlainFilename() {
        for (language in listOf("", "zh-CN", "en")) {
            assertEquals("中文.yaml", parseSubscriptionName(null,
                "attachment; filename=fallback.yaml; filename*=UTF-8'$language'%E4%B8%AD%E6%96%87.yaml"))
        }
    }

    @Test
    fun unsupportedCharsetBadEscapesAndInvalidUtf8FallBackToFilename() {
        for (extended in listOf("ISO-8859-1''caf%E9", "UTF-8''%ZZ", "UTF-8''%A", "UTF-8''%FF", "UTF-8''%C0%AF", "UTF-8'broken", "UTF-8''")) {
            assertEquals("fallback", parseSubscriptionName(null, "attachment; filename*=\"$extended\"; filename=fallback"))
        }
    }

    @Test
    fun quotedFilenameSupportsSemicolonsEscapedQuotesAndBackslashes() {
        for (kind in listOf("inline", "attachment")) {
            assertEquals("a;\"b\\c.yaml", parseSubscriptionName(null, "$kind; filename=\"a;\\\"b\\\\c.yaml\""))
        }
    }

    @Test
    fun plainFilenameIsNotUrlDecodedAndKeepsItsExtension() {
        assertEquals("a+b%20.yaml", parseSubscriptionName(" ", "inline; filename=a+b%20.yaml"))
        assertEquals("a+b.yaml", parseSubscriptionName(null, "inline; filename*=utf-8''a+b.yaml"))
    }

    @Test
    fun base64SubscriptionRemarksSuppliesNameWhenHeadersAreMissing() {
        assertEquals("GlaDOS", parseSubscriptionName(null, null,
            "UkVNQVJLUz1HbGFET1MKc3M6Ly9leGFtcGxl"))
        assertEquals("GlaDOS", parseSubscriptionName(null, null, "REMARKS=GlaDOS\nss://example"))
        assertEquals("GlaDOS", parseSubscriptionName(null, null, "REMARKS=GlaDOS\r\nss://example"))
        assertEquals("Header", parseSubscriptionName("Header", null, "REMARKS=Body"))
    }

    @Test
    fun nodeRemarksDoNotBecomeSubscriptionName() {
        assertNull(parseSubscriptionName(null, null, "ss://YWVzOnBhc3M@example.com:443#GlaDOS"))
        assertNull(parseSubscriptionName(null, null, "proxies:\n  - name: GlaDOS\n    type: ss"))
    }

    @Test
    fun controlCharactersAndReplacementCharactersAreRejected() {
        for (title in listOf("bad\nname", "bad\u0000name", "bad\u007Fname", "bad\u0085name", "bad\uFFFDname")) {
            assertNull(parseSubscriptionName(title, null))
        }
        assertNull(parseSubscriptionName("base64:Cg==", null))
        assertNull(parseSubscriptionName(null, "inline; filename*=UTF-8''bad%00name"))
        assertNull(parseSubscriptionName(null, "inline; filename=\"bad\rname\""))
    }

    @Test
    fun emptyAndMalformedHeadersDoNotSupplyAName() {
        for (header in listOf(null, "", " ", "attachment", "attachment; filename=", "attachment; filename=\"  \"", "attachment; filename=\"unclosed")) {
            assertNull(parseSubscriptionName("  ", header))
        }
    }

    @Test
    fun rejectsUnpairedSurrogatesButKeepsValidSupplementaryCharacters() {
        assertNull(parseSubscriptionName("\uD800", null))
        assertNull(parseSubscriptionName(null, "inline; filename=\uDC00"))
        assertEquals("订阅\uD83D\uDE80", parseSubscriptionName("订阅\uD83D\uDE80", null))
    }
}
