package com.ohmyfly.ui.settings

import com.ohmyfly.ui.AppMessageKind
import com.ohmyfly.ui.AppMessageState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppMessageStateTest {
    @Test
    fun undoQueueAndStaleCallbacksExecuteEachActionOnlyOnce() {
        val messages = AppMessageState()
        var removed = 0
        var restored = 0
        val first = com.ohmyfly.ui.AppMessage(AppMessageKind.Uncertain, "一", "撤销",
            onAction = { restored++ }, onDismiss = { removed++ })
        val second = com.ohmyfly.ui.AppMessage(AppMessageKind.Uncertain, "二", "撤销",
            onAction = { restored++ }, onDismiss = { removed++ })
        messages.show(first)
        messages.show(second)
        messages.show(AppMessageKind.Success, "普通消息")
        assertSame(first, messages.current)
        messages.perform(first)
        messages.dismiss(first)
        messages.perform(first)
        assertSame(second, messages.current)
        messages.dismiss(second)
        messages.perform(second)
        assertEquals(1, restored)
        assertEquals(1, removed)
        assertEquals("普通消息", messages.current?.text)
    }

    @Test
    fun newMessageReplacesOldAndStaleDismissCannotCloseReplacement() {
        val messages = AppMessageState()
        messages.show(AppMessageKind.Success, "设置已保存")
        val first = messages.current
        messages.show(AppMessageKind.Failure, "设置保存失败，已恢复原值")
        val replacement = messages.current
        assertEquals(AppMessageKind.Failure, replacement?.kind)
        messages.dismiss(first)
        assertSame(replacement, messages.current)
        messages.dismiss(replacement)
        assertNull(messages.current)
    }

    @Test
    fun identicalMessagesRefreshLifetimeAndManualCloseClearsCurrent() {
        val messages = AppMessageState()
        messages.show(AppMessageKind.Success, "设置已保存")
        val first = messages.current
        messages.show(AppMessageKind.Success, "设置已保存")
        assertNotSame(first, messages.current)
        assertEquals(3_000L, messages.current?.durationMillis)
        messages.show(AppMessageKind.Error, "设置无效")
        assertEquals(8_000L, messages.current?.durationMillis)
        assertEquals(true, messages.current?.dismissible)
        messages.dismiss()
        assertNull(messages.current)
    }
}
