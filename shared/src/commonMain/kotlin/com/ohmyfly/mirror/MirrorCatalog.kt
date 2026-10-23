package com.ohmyfly.mirror

/**
 * Upstream candidates, untested and unendorsed. Fixed ids are persisted identity; adding or
 * renaming a display name must not silently rebind a user's selection.
 */
internal fun defaultMirrors(): List<MirrorEntry> = listOf(
    MirrorEntry("gh-proxy-com", "gh-proxy.com", "https://gh-proxy.com/"),
    MirrorEntry("githubdog-com", "githubdog.com", "https://githubdog.com/"),
    MirrorEntry("hk-gh-proxy-org", "hk.gh-proxy.org", "https://hk.gh-proxy.org/"),
    MirrorEntry("cdn-gh-proxy-org", "cdn.gh-proxy.org", "https://cdn.gh-proxy.org/"),
    MirrorEntry("gh-wglee-org", "gh.wglee.org", "https://gh.wglee.org/"),
)
