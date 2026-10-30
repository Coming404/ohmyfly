package com.ohmyfly.ui

import androidx.compose.ui.graphics.vector.ImageVector

internal enum class AppDestination(val label: String, val icon: ImageVector) {
    Overview("概览", DriftIcons.Overview),
    Subscriptions("塔台", DriftIcons.Subscriptions),
    Speed("起飞", DriftIcons.Speed),
    NodeLibrary("航班", DriftIcons.Nodes),
    Settings("设置", DriftIcons.Settings),
}
