package com.ohmyfly.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDestinationTest {
    @Test
    fun fiveDestinationsFollowTheAgreedOrder() {
        assertEquals(
            listOf("概览", "塔台", "起飞", "航班", "设置"),
            AppDestination.entries.map { it.label },
        )
    }

    @Test
    fun eachDestinationHasADistinctNamedIcon() {
        val icons = AppDestination.entries.map { it.icon.name }
        assertEquals(5, icons.distinct().size)
        assertTrue(icons.all { it.isNotBlank() })
    }
}
