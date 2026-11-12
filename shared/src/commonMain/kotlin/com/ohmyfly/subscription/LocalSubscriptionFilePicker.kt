package com.ohmyfly.subscription

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalSubscriptionFilePicker = staticCompositionLocalOf<SubscriptionFilePicker> {
    error("平台文件选择器未接入")
}
