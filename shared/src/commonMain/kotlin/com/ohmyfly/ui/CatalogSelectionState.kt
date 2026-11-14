
// Long集合按节点ID切换，保持选中集合可跨越有界分页窗口。
package com.ohmyfly.ui

internal fun toggleLongSelection(current: Set<Long>, id: Long): Set<Long> =
    if (id in current) current - id else current + id
