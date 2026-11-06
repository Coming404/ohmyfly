package com.ohmyfly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.subscription.SubscriptionTower
import com.ohmyfly.subscription.SubscriptionTowerStore
import com.ohmyfly.subscription.validateSubscriptionUrl
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.SettingsButton
import com.ohmyfly.ui.settings.SettingsCard
import com.ohmyfly.ui.settings.SettingsHint
import com.ohmyfly.ui.settings.SettingsIcons
import com.ohmyfly.ui.settings.SettingsInputMenu
import com.ohmyfly.ui.settings.SettingsList
import com.ohmyfly.ui.settings.SettingsName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SubscriptionsScreen(
    listState: LazyListState,
    store: SubscriptionTowerStore,
    settings: SubscriptionSettings,
    scope: CoroutineScope,
    loading: SnapshotStateList<String>,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val towers by store.towers.collectAsState()
    SettingsList(contentPadding, listState) {
        item {
            SubscriptionAddressInput(loading) { address ->
                launchSubscriptionRequest(address, loading, scope) { store.add(address, settings) }
            }
        }
        if (towers.isEmpty()) {
            item { SettingsCard { BasicComponent(title = "暂无塔台") } }
        }
        towers.forEach { tower ->
            item(key = "tower:${tower.address}") {
                SubscriptionTowerHeader(
                    tower = tower,
                    loading = tower.address in loading,
                    onRefresh = {
                        launchSubscriptionRequest(tower.address, loading, scope) {
                            store.refresh(tower.address, settings)
                        }
                    },
                    onRemove = { store.remove(tower.address) },
                )
            }
            itemsIndexed(tower.nodes, key = { index, _ -> "node:${tower.address}:$index" }) { _, node ->
                SettingsCard {
                    BasicComponent(title = node.name, summary = "${node.type} · ${node.server}:${node.port}")
                }
            }
        }
    }
}

@Composable
private fun SubscriptionAddressInput(loading: List<String>, onAdd: (String) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val validation = if (attempted) validateSubscriptionUrl(address) else null
    val busy = address.trim() in loading
    val focus = LocalFocusManager.current
    val submit = {
        attempted = true
        if (!busy && validateSubscriptionUrl(address) == null) {
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
                enabled = !busy,
                onClick = submit,
            )
        }
    }
}

@Composable
private fun SubscriptionTowerHeader(
    tower: SubscriptionTower,
    loading: Boolean,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
) {
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsName(tower.address)
            SettingsHint(if (loading) "拉取中…" else "${tower.nodes.size} 个节点")
            tower.failure?.let { SettingsHint(it, isError = true) }
            tower.warnings.forEach { SettingsHint(it, isError = true) }
            if (!loading && tower.failure == null && tower.nodes.isEmpty()) SettingsHint("暂无节点")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsButton("刷新", SettingsIcons.Retry, Modifier.weight(1f), enabled = !loading, onClick = onRefresh)
                SettingsButton("删除", MirrorIcons.Trash, Modifier.weight(1f), destructive = true, onClick = onRemove)
            }
        }
    }
}

private fun launchSubscriptionRequest(
    address: String,
    loading: SnapshotStateList<String>,
    scope: CoroutineScope,
    action: suspend () -> Unit,
) {
    if (address in loading) return
    loading.add(address)
    scope.launch {
        try {
            action()
        } finally {
            loading.remove(address)
        }
    }
}
