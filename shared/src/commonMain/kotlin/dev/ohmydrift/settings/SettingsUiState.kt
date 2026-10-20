package dev.ohmydrift.settings

/** 界面唯一可见快照：settings 可以领先磁盘，error 保留尚未确认的失败。 */
internal data class SettingsUiState(
    val settings: AppSettings? = null,
    val readFailed: Boolean = false,
    val error: String? = null,
)
