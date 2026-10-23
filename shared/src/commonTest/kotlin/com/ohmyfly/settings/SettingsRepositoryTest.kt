package com.ohmyfly.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

class SettingsRepositoryTest {
    @Test
    fun factoryReturnsOneRepositoryPerNormalizedPath() {
        val first = createSettingsRepository("/tmp/ohmyfly-cache-test/settings.preferences_pb")
        val second = createSettingsRepository("/tmp/ohmyfly-cache-test/../ohmyfly-cache-test/settings.preferences_pb")
        val other = createSettingsRepository("/tmp/ohmyfly-cache-test/second.preferences_pb")

        // 构造 DataStore 不触发磁盘读写；相同路径（含 .. 归一化）必须复用同一实例。
        assertSame(first, second)
        assertFalse(first === other)
    }
}
