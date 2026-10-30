package com.ohmyfly.ui.settings

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SettingsFormValidationTest {
    @Test
    fun targetRequiresHttpsAndNoCredentials() {
        assertNull(validateTargetUrl("https://example.com/resource"))
        assertNotNull(validateTargetUrl("http://example.com/resource"))
        assertNotNull(validateTargetUrl("https://user:pass@example.com/resource"))
        assertNotNull(validateTargetUrl("https://example.com/#fragment"))
        assertNotNull(validateTargetUrl("https://example.com/white space"))
    }

    @Test
    fun patternsRejectInvalidAndOversizeDrafts() {
        assertNull(validatePattern(""))
        assertNull(validatePattern("香港|HK"))
        assertNotNull(validatePattern("["))
        assertNotNull(validatePattern("a".repeat(513)))
    }
}
