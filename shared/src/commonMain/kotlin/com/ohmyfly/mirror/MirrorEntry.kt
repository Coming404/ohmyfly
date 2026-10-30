package com.ohmyfly.mirror

/** A user-configured GitHub mirror. [id] is the stable persisted identity, never the name. */
internal data class MirrorEntry(
    val id: String,
    val name: String,
    val address: String,
)
