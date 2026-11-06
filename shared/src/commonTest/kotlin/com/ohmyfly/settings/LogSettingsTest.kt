// 日志配置边界、持久化兼容和级别语义。
package com.ohmyfly.settings

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ohmyfly.logging.LogLevel
import com.ohmyfly.logging.LogPolicy
import com.ohmyfly.logging.logTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LogSettingsTest {
    @Test fun customPolicySurvivesDataStoreRoundTrip() {
        val settings = AppSettings(logging = LogPolicy(32, 4096, 90, LogLevel.Info))
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
    }

    @Test fun oldSettingsUseDefaultsWithoutWriting() {
        val values = mutablePreferencesOf()
        assertEquals(LogPolicy(), SettingsCodec.decode(values).logging)
        assertEquals(0, values.asMap().size)
    }

    @Test fun totalMustFitWholeFileAndUnitsMustNotOverflow() {
        assertNotNull(validateLogPolicy(LogPolicy(16, 8)))
        assertNotNull(validateLogPolicy(LogPolicy(0, 128)))
        assertNotNull(validateLogPolicy(LogPolicy(totalMiB = Long.MAX_VALUE)))
        assertNotNull(validateLogPolicy(LogPolicy(retentionDays = Long.MAX_VALUE)))
        assertNull(validateLogPolicy(LogPolicy(2048, 8192, 400)))
    }

    @Test fun corruptValuesRecoverToUsablePolicy() {
        val values = mutablePreferencesOf(
            longPreferencesKey("logging.fileMiB") to 16L,
            longPreferencesKey("logging.totalMiB") to 1L,
            longPreferencesKey("logging.retentionDays") to -1L,
            stringPreferencesKey("logging.level") to "Error",
        )
        assertEquals(LogPolicy(16, 16, 7, LogLevel.Detail), SettingsCodec.decode(values).logging)
    }

    @Test fun onlyTwoLevelsAndUrlSecretsAreNotLogged() {
        assertEquals(listOf(LogLevel.Info, LogLevel.Detail), LogLevel.entries)
        assertEquals("https://example.com:443", logTarget("https://u:p@example.com/private-token/path?token=x#key"))
        assertEquals("无效地址", logTarget("not-a-url"))
    }
}
