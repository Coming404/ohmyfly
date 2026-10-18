package dev.ohmydrift.mirror

internal enum class MirrorMode {
    /** Original URL appended unchanged after the prefix. */
    Prefix,
    /** One encoded {url} placeholder in path or query. */
    Template,
}
