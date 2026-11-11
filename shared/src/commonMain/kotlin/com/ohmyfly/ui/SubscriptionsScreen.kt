package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.subscription.SubscriptionMirrorSettings
import com.ohmyfly.catalog.CatalogTower
import com.ohmyfly.catalog.CatalogStore
import com.ohmyfly.catalog.CatalogPager
import com.ohmyfly.subscription.validateSubscriptionUrl
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.SettingsButton
import com.ohmyfly.ui.settings.SettingsCard
import com.ohmyfly.ui.settings.SettingsHint
import com.ohmyfly.ui.settings.SettingsIcons
import com.ohmyfly.ui.settings.SettingsInputMenu
import com.ohmyfly.ui.settings.SettingsList
import com.ohmyfly.ui.settings.SettingsName
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SubscriptionsScreen(
    listState: LazyListState,
    store: CatalogStore,
    settings: AppSettings,
    importReady: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val catalog by store.state.collectAsState()
    val mirrors = SubscriptionMirrorSettings(settings.mirrorEnabled, settings.mirrors, settings.selectedMirrorId)
    SettingsList(contentPadding, listState) {
        item {
            SubscriptionAddressInput(catalog.busy, catalog.ready && importReady) { address ->
                store.add(address, settings.subscription, mirrors)
            }
        }
        item {
            CatalogPager(catalog.towerPage, catalog.summary.towers, catalog.failure,
                onPrevious = { store.page("towers", catalog.towerPage - 1) },
                onNext = { store.page("towers", catalog.towerPage + 1) },
                onRetry = { if (catalog.ready) store.page("towers", catalog.towerPage) else store.start() })
        }
        if (catalog.ready && catalog.failure == null && catalog.summary.towers == 0L) {
            item { SettingsCard { BasicComponent(title = "暂无塔台") } }
        }
        catalog.towers.forEach { tower ->
            item(key = "tower:${tower.id}") {
                SubscriptionTowerHeader(
                    tower = tower, loading = catalog.busy, ready = catalog.ready && importReady,
                    onRefresh = { store.refresh(tower.id, settings.subscription, mirrors) },
                    onRemove = { store.remove(tower.id) },
                )
            }
        }
    }
}

@Composable
private fun SubscriptionAddressInput(busy: Boolean, ready: Boolean, onAdd: (String) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val validation = if (attempted) validateSubscriptionUrl(address) else null
    val focus = LocalFocusManager.current
    val submit = {
        attempted = true
        if (ready && !busy && validateSubscriptionUrl(address) == null) {
            focus.clearFocus()
            onAdd(address.trim())
            address = ""
            attempted = false
        }
    }
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsName("订阅地址")
            SettingsInputMenu {
                TextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    insideMargin = DpSize(12.dp, 12.dp),
                    textStyle = MiuixTheme.textStyles.main.copy(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics {
                        contentDescription = "订阅地址"
                        validation?.let { error(it) }
                    },
                )
            }
            validation?.let { SettingsHint(it, isError = true) }
            SettingsButton(
                text = if (busy) "拉取中…" else "添加",
                icon = MirrorIcons.Add,
                modifier = Modifier.fillMaxWidth(),
                enabled = ready && !busy,
                onClick = submit,
            )
        }
    }
}

@Composable
private fun SubscriptionTowerHeader(
    tower: CatalogTower,
    loading: Boolean,
    ready: Boolean,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
) {
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsName(tower.name.ifBlank { "塔台 ${tower.id}" })
            SettingsHint(if (loading) "拉取中…" else "${tower.nodeCount} 个航班")
            if (tower.failure.isNotEmpty()) SettingsHint(tower.failure, isError = true)
            tower.usage?.let { usage ->
                SettingsHint("上传 ${usage.upload ?: "未知"} B · 下载 ${usage.download ?: "未知"} B · 总量 ${usage.total ?: "未知"} B")
            }
            if (!loading && tower.failure.isEmpty() && tower.nodeCount == 0L) SettingsHint("暂无航班")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsButton("刷新", SettingsIcons.Retry, Modifier.weight(1f), enabled = ready && !loading, onClick = onRefresh)
                SettingsButton("删除", MirrorIcons.Trash, Modifier.weight(1f), destructive = true, onClick = onRemove)
            }
        }
    }
}
