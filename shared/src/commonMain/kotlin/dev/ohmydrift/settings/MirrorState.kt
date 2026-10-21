package dev.ohmydrift.settings

import dev.ohmydrift.mirror.MirrorEntry
import dev.ohmydrift.mirror.MirrorOrder

/** 镜像字段的解码结果，供 [SettingsCodec] 组装 AppSettings，避免重复解析。 */
internal data class MirrorState(
    val mirrors: List<MirrorEntry>,
    val selectedMirrorId: String?,
    val order: MirrorOrder,
    val enabled: Boolean,
)
