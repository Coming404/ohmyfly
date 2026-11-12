package com.ohmyfly.settings

import kotlinx.serialization.Serializable

@Serializable
internal enum class SpeedMode {
    Latency,
    Download,
    Full,
}
