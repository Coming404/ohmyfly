package com.ohmyfly.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppMotionTest {
    @Test
    fun eitherRestrictionPreventsMotion() {
        for (reduced in listOf(false, true)) {
            for (scale in listOf(0f, 0.5f, 1f, 2f, 10f)) {
                if (reduced || scale == 0f) assertFalse(appMotionAllowed(reduced, scale))
                else assertTrue(appMotionAllowed(reduced, scale))
            }
        }
    }
}
