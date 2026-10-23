package com.ohmyfly.settings

import com.ohmyfly.mirror.MirrorEntry
import com.ohmyfly.mirror.MirrorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsValidationTest {
    private val mirror = MirrorEntry(id = "a", name = "甲", address = "https://a.example", mode = MirrorMode.Prefix)

    @Test
    fun defaultsAndEmptyMirrorListAreValid() {
        assertNull(validateSettings(AppSettings(mirrors = emptyList())))
        assertNull(validateSettings(AppSettings(mirrors = listOf(mirror))))
    }

    @Test
    fun targetsRejectCredentialsFragmentAndControlCharacters() {
        listOf(
            "ftp://example.com/x",
            "http://example.com/x",
            "https://",
            "https://exa mple.com",
            " https://example.com",
            "https://:443/x",
            "https://user:pass@example.com/x",
            "https://example.com/x#frag",
            "https://example.com/x?token=abc",
            "https://example.com/x?api_key=abc",
            "https://example.com/x\u0000",
        ).forEach { url ->
            assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(latencyUrl = url))), "应拒绝：$url")
        }
    }

    @Test
    fun targetsAllowPlainQueryParameters() {
        assertNull(validateSettings(AppSettings(speed = SpeedSettings(latencyUrl = "https://example.com/204?cache=0"))))
        assertNull(validateSettings(AppSettings(speed = SpeedSettings(downloadUrl = "https://example.com/f.bin?size=10"))))
    }

    @Test
    fun speedRangesMatchUiPlan() {
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(downloadMiB = 501))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(uploadMiB = 101))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(transferTimeoutSeconds = 2))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(latencyTimeoutSeconds = 16))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(latencySamples = 11))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(connectionsPerNode = 9))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(maxFailurePercent = 101))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(stopAfter = -1))))
        assertNull(
            validateSettings(
                AppSettings(
                    speed = SpeedSettings(
                        downloadMiB = 500,
                        uploadMiB = 100,
                        transferTimeoutSeconds = 60,
                        latencyTimeoutSeconds = 15,
                        latencySamples = 10,
                        connectionsPerNode = 8,
                    ),
                ),
            ),
        )
    }

    @Test
    fun fractionalMinSpeedsAreValidButNonFiniteAndOutOfRangeAreNot() {
        assertNull(validateSettings(AppSettings(speed = SpeedSettings(minDownloadMiBps = 0.125, minUploadMiBps = 0.5))))
        assertNull(validateSettings(AppSettings(speed = SpeedSettings(minDownloadMiBps = 10_000.0))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(minDownloadMiBps = 10_000.5))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(minDownloadMiBps = -0.125))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(minUploadMiBps = Double.NaN))))
        assertNotNull(validateSettings(AppSettings(speed = SpeedSettings(minUploadMiBps = Double.POSITIVE_INFINITY))))
    }

    @Test
    fun mediaRequiresPlatformWhenEnabled() {
        assertNotNull(validateSettings(AppSettings(media = MediaSettings(enabled = true, platforms = emptySet()))))
        assertNull(validateSettings(AppSettings(media = MediaSettings(enabled = false, platforms = emptySet()))))
        assertNotNull(validateSettings(AppSettings(media = MediaSettings(concurrency = 9))))
        assertNotNull(validateSettings(AppSettings(media = MediaSettings(timeoutSeconds = 31))))
        assertNotNull(validateSettings(AppSettings(media = MediaSettings(retries = 3))))
    }

    @Test
    fun subscriptionRulesAreBounded() {
        assertNotNull(validateSettings(AppSettings(subscription = SubscriptionSettings(includePattern = "[a-"))))
        assertNotNull(validateSettings(AppSettings(subscription = SubscriptionSettings(excludePattern = "("))))
        assertNull(validateSettings(AppSettings(subscription = SubscriptionSettings(includePattern = "^HK|TW$"))))
        assertNotNull(validateSettings(AppSettings(subscription = SubscriptionSettings(userAgent = "a\nb"))))
        assertNotNull(validateSettings(AppSettings(subscription = SubscriptionSettings(userAgent = "a\u0000b"))))
        assertNotNull(validateSettings(AppSettings(subscription = SubscriptionSettings(maxMiB = 51))))
        assertNotNull(validateSettings(AppSettings(subscription = SubscriptionSettings(timeoutSeconds = 2))))
        assertNotNull(validateSettings(AppSettings(subscription = SubscriptionSettings(retries = 6))))
    }

    @Test
    fun mirrorIdsAreNormalizedUniqueAndNotDirect() {
        val duplicate = listOf(mirror, MirrorEntry(id = "A ", name = "乙", address = "https://b.example", mode = MirrorMode.Prefix))
        assertNotNull(validateSettings(AppSettings(mirrors = duplicate)))

        assertNotNull(validateSettings(AppSettings(mirrors = listOf(MirrorEntry(id = "direct", name = "直连", address = "https://x.example", mode = MirrorMode.Prefix)))))
        assertNotNull(validateSettings(AppSettings(mirrors = listOf(MirrorEntry(id = " ", name = "空", address = "https://x.example", mode = MirrorMode.Prefix)))))
        assertNotNull(validateSettings(AppSettings(mirrors = listOf(MirrorEntry(id = "a b", name = "空白", address = "https://x.example", mode = MirrorMode.Prefix)))))

        val sameAddress = listOf(mirror, MirrorEntry(id = "b", name = "乙", address = "https://a.example/", mode = MirrorMode.Prefix))
        assertEquals("镜像地址不能重复", validateSettings(AppSettings(mirrors = sameAddress)))

        val selected = AppSettings(mirrors = listOf(mirror), selectedMirrorId = "a")
        assertNull(validateSettings(selected))

        assertEquals("选中的镜像不存在", validateSettings(selected.copy(selectedMirrorId = "b")))
    }

    @Test
    fun controlCharacterDetectionDoesNotTreatLettersAsControl() {
        assertTrue('a'.isControl().not())
        assertTrue('\n'.isControl())
        assertTrue('\u007F'.isControl())
    }
}
