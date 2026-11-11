// 验证共享域名分类与公开资源路径边界，不把域名分类当成跳转授权。
package com.ohmyfly.mirror

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubRoutingTest {
    @Test
    fun domainClassificationUsesLabelBoundariesAndNormalizesCaseAndTrailingDot() {
        for (host in listOf("github.com", "GITHUB.COM.", "gist.github.com", "a.b.github.com",
            "githubusercontent.com", "gist.githubusercontent.com", "a.b.githubusercontent.com")) {
            assertTrue(isGitHubHost(host), host)
        }
        for (host in listOf("evilgithub.com", "github.com.evil", "githubusercontent.com.evil", "example.com")) {
            assertFalse(isGitHubHost(host), host)
        }
    }

    @Test
    fun githubFamilyStillRequiresConcretePublicResourcePaths() {
        for (url in listOf("https://github.com/o/r/raw/main/a.yaml", "https://GITHUB.COM./o/r/raw/main/a.yaml",
            "https://api.github.com/o/r/raw/main/a.yaml", "https://githubusercontent.com/o/r/raw/main/a.yaml",
            "https://nested.githubusercontent.com/o/r/raw/main/a.yaml", "https://raw.githubusercontent.com/o/r/main/a.yaml",
            "https://gist.github.com/u/id/raw", "https://gist.github.com/u/id/raw/rev/a.yaml",
            "https://gist.githubusercontent.com/u/id/raw/a.yaml", "https://gist.githubusercontent.com/u/id/raw/rev/a.yaml")) {
            assertNull(githubResourceError(url), url)
        }
        for (url in listOf("https://gist.github.com/u/id", "https://api.github.com/anything",
            "https://evilgithub.com/o/r/raw/main/a", "https://github.com.evil/o/r/raw/main/a",
            "https://example.com/github.com/o/r/raw/main/a", "https://github.com/o/r/raw/a?token=secret",
            "https://user:pass@github.com/o/r/raw/a", "https://github.com:8443/o/r/raw/a",
            "http://github.com/o/r/raw/a", "https://github.com/o/r/raw/../a")) {
            assertNotNull(githubResourceError(url), url)
        }
        assertNull(resolveRedirect("https://mirror.example/file", "https://api.github.com/o/r/raw/a"))
    }
}
