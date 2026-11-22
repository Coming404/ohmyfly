package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ohmyfly.catalog.CatalogStore
import com.ohmyfly.catalog.CatalogTower
import com.ohmyfly.subscription.validateSubscriptionUrl
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet


@Composable
internal fun TowerEditSheet(tower: CatalogTower, store: CatalogStore, onClose: () -> Unit) {
    var name by remember(tower.id) { mutableStateOf(tower.name) }
    var address by remember(tower.id) { mutableStateOf(tower.address) }
    var failure by remember(tower.id) { mutableStateOf<String?>(null) }
    var saving by remember(tower.id) { mutableStateOf(false) }
    OverlayBottomSheet(show = true, title = "编辑塔台", insideMargin = catalogSheetMargin(),
        onDismissRequest = { if (!saving) onClose() }, cornerRadius = AppCorners.Sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("塔台名称", style = catalogInputStyle())
            TextField(value = name, onValueChange = { name = it; failure = null },
                textStyle = catalogInputStyle(), singleLine = true, enabled = !saving, cornerRadius = AppCorners.Input)
            if (tower.sourceKind == "http") {
                Text("订阅地址", style = catalogInputStyle())
                TextField(value = address, onValueChange = { address = it; failure = null },
                    textStyle = catalogInputStyle(), singleLine = true, enabled = !saving, cornerRadius = AppCorners.Input)
            }
            failure?.let { CatalogNotice(it) }
            CatalogTextButton(
                text = "保存",
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
                onClick = {
                    val title = name.trim()
                    when {
                        title.isEmpty() -> failure = "请输入塔台名称"
                        title.length > 256 -> failure = "塔台名称过长"
                        tower.sourceKind == "http" && validateSubscriptionUrl(address) != null ->
                            failure = validateSubscriptionUrl(address)
                        else -> {
                            saving = true
                            store.updateTower(tower.id, if (tower.sourceKind == "http") address.trim() else tower.address, title) { ok, message ->
                                saving = false
                                if (ok) onClose() else failure = message
                            }
                        }
                    }
                },
            )
            CatalogTextButton("取消", enabled = !saving, onClick = hapticClick(onClose), modifier = Modifier.fillMaxWidth())
        }
    }
}
