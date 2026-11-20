package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text

/** 列表索引是滚动锚点；数据库只以相邻 100 条游标回读，不保存全量 ID。 */
@Composable
internal fun CatalogViewport(
    list: LazyListState,
    headerCount: Int,
    currentPage: Int,
    loaded: Set<Int>,
    count: Long,
    failed: Boolean,
    onPage: (Int) -> Unit,
) {
    LaunchedEffect(list, headerCount, currentPage, loaded, count, failed) {
        if (failed || count == 0L) return@LaunchedEffect
        snapshotFlow {
            list.layoutInfo.visibleItemsInfo.map { it.index - headerCount }
                .filter { it >= 0 && it.toLong() < count }.map { it / 100 }.distinct()
        }.distinctUntilChanged().collect { visible ->
            val missing = visible.firstOrNull { it !in loaded }
            if (missing != null) onPage(missing)
        }
    }
}
