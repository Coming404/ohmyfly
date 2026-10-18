package dev.ohmydrift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun SubscriptionsScreen(listState: LazyListState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SmallTitle("订阅来源") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                BasicComponent(
                    title = "尚未接入订阅管理",
                    summary = "当前节点库仅含演示节点，没有已导入的订阅。",
                )
                BasicComponent(
                    title = "添加与导入",
                    summary = "URL、文件及文本导入将在解析与安全存储接入后开放；当前不会读取文件或发送网络请求。",
                )
            }
        }
    }
}
