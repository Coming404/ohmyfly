package dev.ohmydrift.ui

import androidx.compose.ui.graphics.vector.ImageVector

internal enum class AppDestination(val label: String, val icon: ImageVector) {
    Overview("概览", DriftIcons.Overview),
    Subscriptions("订阅", DriftIcons.Subscriptions),
    SpeedTest("测速", DriftIcons.Play),
    NodeLibrary("节点库", DriftIcons.Nodes),
    Settings("设置", DriftIcons.Settings),
}
