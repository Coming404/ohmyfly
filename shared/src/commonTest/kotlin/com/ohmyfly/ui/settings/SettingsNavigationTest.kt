package com.ohmyfly.ui.settings

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsNavigationTest {
    @Test
    fun eachBusinessPageOpensFromHome() {
        SettingsPage.entries.filter { it != SettingsPage.Home }.forEach { page ->
            val stack = navBackStackOf(SettingsPage.Home)
            stack.openSettingsPage(page)
            assertEquals(listOf(SettingsPage.Home, page), stack.toList())
        }
    }

    @Test
    fun repeatedOrStaleHomeClicksDoNotPushDuplicateChildren() {
        val stack = navBackStackOf(SettingsPage.Home)
        stack.openSettingsPage(SettingsPage.Mirrors)
        stack.openSettingsPage(SettingsPage.Mirrors)
        stack.openSettingsPage(SettingsPage.Speed)
        assertEquals(listOf(SettingsPage.Home, SettingsPage.Mirrors), stack.toList())
    }

    @Test
    fun homeDoesNotPushAnotherRoot() {
        val stack = navBackStackOf(SettingsPage.Home)
        stack.openSettingsPage(SettingsPage.Home)
        assertEquals(listOf(SettingsPage.Home), stack.toList())
    }

    @Test
    fun popReturnsToHomeAndCannotRemoveRoot() {
        val stack = navBackStackOf(SettingsPage.Home)
        stack.openSettingsPage(SettingsPage.Media)
        stack.popSettingsPage()
        stack.popSettingsPage()
        assertEquals(listOf(SettingsPage.Home), stack.toList())
    }

    @Test
    fun allRouteKeysHaveAStableSerializationRoundTrip() {
        val pages = SettingsPage.entries.toList()
        assertEquals(pages, Json.decodeFromString<List<SettingsPage>>(Json.encodeToString(pages)))
        assertEquals(pages.size, pages.map { it.toString() }.distinct().size)
    }

    @Test
    fun restoredRouteValuesRemainUsableAsBackStackKeys() {
        val encoded = Json.encodeToString(listOf(SettingsPage.Home, SettingsPage.Privacy))
        val decoded = Json.decodeFromString<List<SettingsPage>>(encoded)
        val stack = navBackStackOf(*decoded.toTypedArray())
        assertEquals(SettingsPage.Privacy, stack.last())
        stack.popSettingsPage()
        stack.openSettingsPage(SettingsPage.About)
        assertEquals(listOf(SettingsPage.Home, SettingsPage.About), stack.toList())
    }
}
