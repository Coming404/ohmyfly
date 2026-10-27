package com.ohmyfly.ui

import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.HapticLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppHapticsTest {
    @Test
    fun defaultSettingsClickUsesMediumFeedback() {
        val settings = AppSettings()
        val performed = mutableListOf<HapticLevel>()
        val feedback = AppHapticFeedback({ settings.hapticEnabled }, { settings.hapticLevel }, { performed.add(it) })

        feedback.click()

        assertEquals(listOf(HapticLevel.Medium), performed)
    }

    @Test
    fun clicksReadLatestEnabledStateAndLevelFromTheSameFeedbackInstance() {
        var settings = AppSettings()
        val performed = mutableListOf<HapticLevel>()
        val feedback = AppHapticFeedback({ settings.hapticEnabled }, { settings.hapticLevel }, { performed.add(it) })

        feedback.click()
        settings = settings.copy(hapticLevel = HapticLevel.Light)
        feedback.click()
        settings = settings.copy(hapticEnabled = false, hapticLevel = HapticLevel.Heavy)
        feedback.click()
        settings = settings.copy(hapticEnabled = true)
        feedback.click()

        assertEquals(listOf(HapticLevel.Medium, HapticLevel.Light, HapticLevel.Heavy), performed)
    }

    @Test
    fun disabledClicksAndPreviewsStaySilentForEveryLevel() {
        var settings = AppSettings(hapticEnabled = false)
        val performed = mutableListOf<HapticLevel>()
        val feedback = AppHapticFeedback({ settings.hapticEnabled }, { settings.hapticLevel }, { performed.add(it) })

        for (level in HapticLevel.entries) {
            settings = settings.copy(hapticLevel = level)
            feedback.click()
            feedback.preview(level)
        }

        assertTrue(performed.isEmpty())
    }

    @Test
    fun previewsUseAllThreeRequestedLevelsWithoutChangingSelectedLevel() {
        val settings = AppSettings(hapticLevel = HapticLevel.Light)
        val performed = mutableListOf<HapticLevel>()
        val feedback = AppHapticFeedback({ settings.hapticEnabled }, { settings.hapticLevel }, { performed.add(it) })

        for (level in HapticLevel.entries) {
            feedback.preview(level)
            assertEquals(HapticLevel.Light, settings.hapticLevel)
        }
        feedback.click()

        assertEquals(listOf(HapticLevel.Light, HapticLevel.Medium, HapticLevel.Heavy, HapticLevel.Light), performed)
    }

    @Test
    fun previewsReadLatestEnabledState() {
        var enabled = false
        val performed = mutableListOf<HapticLevel>()
        val feedback = AppHapticFeedback({ enabled }, { HapticLevel.Medium }, { performed.add(it) })

        feedback.preview(HapticLevel.Light)
        enabled = true
        feedback.preview(HapticLevel.Heavy)
        enabled = false
        feedback.preview(HapticLevel.Medium)
        enabled = true
        feedback.preview(HapticLevel.Light)

        assertEquals(listOf(HapticLevel.Heavy, HapticLevel.Light), performed)
    }
}
