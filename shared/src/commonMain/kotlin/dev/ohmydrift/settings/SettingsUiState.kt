package dev.ohmydrift.settings

/**
 * 界面唯一可见快照：settings 可以领先磁盘，error 保留尚未确认的失败。
 * confirmedSettings 只由磁盘读取结果更新，因此不能与乐观 settings 混用；
 * pendingWrites 是队列中尚未确认的操作数，归零才算界面与存储一致。
 */
internal data class SettingsUiState(
    val settings: AppSettings? = null,
    val confirmedSettings: AppSettings? = null,
    val pendingWrites: Int = 0,
    val readFailed: Boolean = false,
    val error: String? = null,
)
