package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.ui.settings.*
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

@Composable
internal fun SettingsScreen(
    listState: LazyListState,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    backStack: NavBackStack,
    onPage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    editorBackActive: Boolean,
    onEditorBackChange: ((() -> Unit)?) -> Unit,
    session: SettingsSession,
    runner: MirrorProbeRunner,
    onMirrorAddChange: ((() -> Unit)?) -> Unit,
    version: String,
    writeError: String?,
    onDismissError: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val motionAllowed = LocalAppMotionAllowed.current
    val direction = LocalLayoutDirection.current
    val showWriteError = writeError != null && backStack.lastOrNull() != SettingsPage.Mirrors
    val pagePadding = if (!showWriteError) contentPadding else PaddingValues(
        start = contentPadding.calculateStartPadding(direction),
        end = contentPadding.calculateEndPadding(direction),
        bottom = contentPadding.calculateBottomPadding(),
    )
    val background = LocalAppSurfaceColors.current.page
    val swipeDirection = when {
        editorBackActive || !motionAllowed -> NavSwipeDirection.None
        direction == LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        else -> NavSwipeDirection.LeftToRight
    }
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        transition = if (motionAllowed) NavTransitions.MiuixDefault else NavTransitions.None,
        effects = if (motionAllowed) {
            NavDisplayEffects(enableCornerClip = false, blockInputDuringTransition = true)
        } else NavDisplayEffects.None,
    ) {
        entry<SettingsPage>(swipeDismiss = swipeDirection) { page ->
            Column(Modifier.fillMaxSize().background(background)
                .padding(top = if (showWriteError) contentPadding.calculateTopPadding() else 0.dp)) {
                if (showWriteError && writeError != null) {
                    BasicComponent(title = "设置未保存", summary = writeError)
                    TextButton(text = "关闭提示", onClick = onDismissError)
                }
                if (session.state.readFailed && page != SettingsPage.Mirrors) {
                    BasicComponent(title = "设置结果待确认", summary = "无法读取本地设置，已暂停写入。")
                    TextButton(text = "重试读取", onClick = { session.retryRead() })
                }
                when (page) {
                    SettingsPage.Home -> SettingsHome(settings, onChange, onPage, version, listState, pagePadding)
                    SettingsPage.Speed -> SpeedSettingsPage(settings, onChange, pagePadding)
                    SettingsPage.Media -> MediaSettingsPage(settings, onChange, pagePadding)
                    SettingsPage.Subscription -> SubscriptionSettingsPage(settings, onChange, pagePadding)
                    SettingsPage.Mirrors -> MirrorSettingsPage(settings, onChange, pagePadding, onEditorBackChange,
                        session, runner, onMirrorAddChange)
                    SettingsPage.Privacy -> PrivacySettingsPage(settings, onChange, pagePadding)
                    SettingsPage.About -> AboutSettingsPage(version, pagePadding)
                }
            }
        }
    }
}
