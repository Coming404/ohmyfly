// 固定顶栏统一标题、数量胶囊与选择操作，材质使用设置中的同一绘制入口。
package com.ohmyfly

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.ui.*
import com.ohmyfly.ui.settings.MirrorIcons
import com.ohmyfly.ui.settings.SettingsIcons
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 只有顶栏固定；塔台总控属于列表内容。 */
@Composable
internal fun AppTopBar(
    destination: AppDestination, settingsChild: Boolean, settingsBack: (() -> Unit)?,
    mirrorPage: Boolean, mirrorAdd: (() -> Unit)?, logsPage: Boolean, logClear: (() -> Unit)?,
    selectedIds: Set<String>, selectedTowerIds: Set<Long>, selectionMode: CatalogSelectionMode?,
    selectionAllLabel: String, selectingAll: Boolean, onCancelSelection: () -> Unit, onSelectAll: () -> Unit,
    backdrop: Backdrop, glassEnabled: Boolean, dark: Boolean, settings: AppSettings,
    settingsSession: SettingsSession, onCatalogSearch: () -> Unit, onTowerAdd: () -> Unit,
    onFlightFilter: () -> Unit, towerCount: Long,
) {
    DriftTopBar(
        title = if (selectionMode != null) "已选 ${if (selectionMode == CatalogSelectionMode.Nodes) selectedIds.size else selectedTowerIds.size}"
            else if (settingsChild || destination == AppDestination.Settings) "" else destination.label,
        titleCount = if (destination == AppDestination.Subscriptions && selectionMode == null) towerCount else null,
        backdrop = backdrop, glassEnabled = glassEnabled, dark = dark,
        materialMode = settings.materialMode, onBack = settingsBack,
        actions = when {
            selectionMode != null -> {
                {
                    CatalogSelectionTopToolbar(
                        modifier = Modifier.chromeMaterial(backdrop, glassEnabled, dark, settings.materialMode),
                        selecting = selectingAll, selectAllLabel = selectionAllLabel,
                        onCancel = onCancelSelection, onSelectAll = onSelectAll,
                    )
                }
            }
            destination == AppDestination.NodeLibrary -> {
                {
                    CatalogActionToolbar(Modifier.chromeMaterial(backdrop, glassEnabled, dark, settings.materialMode)) {
                        CatalogToolbarAction("搜索航班", SettingsIcons.Search, onClick = onCatalogSearch)
                        FlightFilterAction(onClick = onFlightFilter)
                    }
                }
            }
            destination == AppDestination.Subscriptions -> {
                {
                    CatalogActionToolbar(Modifier.chromeMaterial(backdrop, glassEnabled, dark, settings.materialMode)) {
                        CatalogToolbarAction("搜索塔台", SettingsIcons.Search, onClick = onCatalogSearch)
                        CatalogToolbarAction("添加塔台", MirrorIcons.Add, onClick = onTowerAdd,
                            enabled = !settingsSession.state.readFailed)
                    }
                }
            }
            mirrorPage -> {
                { HeaderAction("添加镜像", MirrorIcons.Add, onClick = { mirrorAdd?.invoke() },
                    enabled = mirrorAdd != null && settings.mirrorEnabled && !settingsSession.state.readFailed) }
            }
            logsPage -> {
                { HeaderAction("清理全部日志", MirrorIcons.Trash, onClick = { logClear?.invoke() },
                    enabled = logClear != null && !settingsSession.state.readFailed,
                    tint = MiuixTheme.colorScheme.error) }
            }
            else -> null
        },
    )
}
