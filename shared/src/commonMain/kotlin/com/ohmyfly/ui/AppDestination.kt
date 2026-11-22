package com.ohmyfly.ui

import androidx.compose.ui.graphics.vector.ImageVector

internal enum class AppDestination(val label: String, val icon: ImageVector) {
    Overview("概览", DriftIcons.Overview),
    Subscriptions("塔台", DriftIcons.Subscriptions),
    Speed("起飞", DriftIcons.Speed),
    NodeLibrary("航班", DriftIcons.Flights),
    Settings("设置", DriftIcons.Settings),
}

/** 在恢复旧胶囊交互时继续使用本轮已选定的图标字重。 */
internal fun AppDestination.navigationIcon(selected: Boolean): ImageVector = when (this) {
    AppDestination.Overview -> if (selected) DriftIcons.OverviewSelected else DriftIcons.Overview
    AppDestination.Subscriptions -> if (selected) DriftIcons.SubscriptionsSelected else DriftIcons.Subscriptions
    AppDestination.Speed -> if (selected) DriftIcons.SpeedSelected else DriftIcons.Speed
    AppDestination.NodeLibrary -> if (selected) DriftIcons.FlightsSelected else DriftIcons.Flights
    AppDestination.Settings -> if (selected) DriftIcons.SettingsSelected else DriftIcons.Settings
}
