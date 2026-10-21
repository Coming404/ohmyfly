package dev.ohmydrift.mirror

import kotlin.test.*

class MirrorPolicyTest {
    @Test
    fun prefixAndTemplateHaveDifferentEncodingRules() {
        val original = MirrorProbeTarget.url
        val prefix = MirrorEntry("a", "A", "https://EXAMPLE.com:443/base")
        val template = prefix.copy(address = "https://example.com/fetch?url={url}", mode = MirrorMode.Template)
        assertNull(validateMirror(prefix))
        assertEquals("https://example.com/base/" + original, buildMirrorUrl(prefix, original))
        assertTrue(buildMirrorUrl(template, original).contains("url=https%3A%2F%2F"))
        assertNotEquals(normalizedMirrorAddress(prefix), normalizedMirrorAddress(prefix.copy(address = "https://example.com/Base/")))
    }

    @Test
    fun mirrorsRejectSecretsAndInvalidPlaceholders() {
        val base = MirrorEntry("a", "A", "https://example.com/")
        listOf("ftp://example.com/", "https://user:pass@example.com/", "https://127.0.0.1/", "https://example.com/#fragment").forEach {
            assertNotNull(validateMirror(base.copy(address = it)))
        }
        assertNotNull(validateMirror(base.copy(id = "direct")))
        assertNotNull(validateMirror(base.copy(address = "https://example.com/?token=secret&url={url}", mode = MirrorMode.Template)))
        assertNotNull(validateMirror(base.copy(address = "https://example.com/{url}/{url}", mode = MirrorMode.Template)))
        assertNotNull(validateMirror(base.copy(address = "https://{url}.example.com/", mode = MirrorMode.Template)))
    }

    @Test
    fun mirrorInputsSupportHttpHttpsAndBareDomainsWithoutRemovingWww() {
        val base = MirrorEntry("m", "M", "")
        val cases = mapOf(
            "http://WWW.Example.com:80/Path" to "http://www.example.com/Path/",
            "https://Example.com:443/Path" to "https://example.com/Path/",
            "www.example.com/Path" to "https://www.example.com/Path/",
            "example.com" to "https://example.com/",
            "example.com:8080/Base" to "https://example.com:8080/Base/",
            "http://example.com:443/" to "http://example.com:443/",
        )
        cases.forEach { (input, expected) ->
            assertNull(validateMirror(base.copy(address = input)), input)
            assertEquals(expected, normalizedMirrorAddress(base.copy(address = input)))
        }
        listOf("http://127.0.0.1/", "localhost", "http://192.168.1.1/", "ftp://example.com/", "https://user:pass@example.com/")
            .forEach { assertNotNull(validateMirror(base.copy(address = it))) }
        assertNull(resolveRedirect("https://example.com/file", "http://example.com/next"))
        assertEquals("http://example.com/next", resolveRedirect("http://example.com/file", "/next"))
        assertEquals("https://example.com/next", resolveRedirect("http://example.com/file", "https://example.com/next"))
        assertNotNull(githubResourceError("http://github.com/a/b/archive/main.zip"))
    }

    @Test
    fun reservedIpv4AndIpv6CannotBeUsed() {
        listOf("127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.169.254", "192.168.1.1", "198.18.0.1").forEach {
            assertNull(parseHttpsUrl("https://$it/"))
        }
        listOf("::1", "fc00::1", "fe80::1", "2001:db8::1", "::ffff:127.0.0.1", "2002:7f00:1::1").forEach {
            assertNull(parseHttpsUrl("https://[$it]/"))
        }
        assertNotNull(parseHttpsUrl("https://[2001:4860:4860::8888]/"))
        assertNull(ipv6Bytes("2001:gggg::1"))
        assertNull(ipv6Bytes("2001:::1"))
    }

    @Test
    fun redirectsStayOnCurrentOriginOrKnownGithubCdn() {
        val current = "https://mirror.example.com/file"
        assertEquals("https://mirror.example.com/next", resolveRedirect(current, "/next"))
        assertNotNull(resolveRedirect(current, "https://release-assets.githubusercontent.com/file?sig=public"))
        assertNull(resolveRedirect(current, "https://attacker.example.com/file"))
        assertNull(resolveRedirect(current, "http://github.com/file"))
        assertNull(resolveRedirect(current, "https://127.0.0.1/file"))
    }

    @Test
    fun onlyConcretePublicResourcePathsAreEligible() {
        assertNull(githubResourceError(MirrorProbeTarget.url))
        assertNotNull(githubResourceError("https://github.com/owner/repo/issues/123"))
        assertNotNull(githubResourceError(MirrorProbeTarget.url + "?token=secret"))
        assertNotNull(githubResourceError("https://github.com/owner/repo/archive/../private"))
    }

    @Test
    fun sortingKeepsErrorsLastAndDoesNotChangeManualOrder() {
        val entries = listOf(MirrorEntry("a", "A", "https://a.example.com/"), MirrorEntry("b", "B", "https://b.example.com/"), MirrorEntry("c", "C", "https://c.example.com/"))
        val results = mapOf("a" to MirrorProbeResult(ProbeStatus.Success, 50, 3, ""), "b" to MirrorProbeResult(ProbeStatus.Success, 10, 3, ""), "c" to MirrorProbeResult(ProbeStatus.Timeout, null, 0, ""))
        assertEquals(listOf("b", "a", "c"), sortMirrors(entries, results, MirrorOrder.LatencyAscending).map { it.id })
        assertEquals(listOf("a", "b", "c"), sortMirrors(entries, results, MirrorOrder.LatencyDescending).map { it.id })
        assertEquals(listOf("a", "b", "c"), entries.map { it.id })
    }
}
