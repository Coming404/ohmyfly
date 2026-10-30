package com.ohmyfly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.LayoutDirection
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SettingsSession
import com.ohmyfly.ui.settings.*
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

@Composable
internal fun SettingsScreen(
    listState: LazyListState,
    settings: AppSettings,
    onChange: (String, (AppSettings) -> AppSettings) -> Unit,
    backStack: NavBackStack,
    onPage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    editorBackActive: Boolean,
    onEditorBackChange: ((() -> Unit)?) -> Unit,
    session: SettingsSession,
    runner: MirrorProbeRunner,
    onMirrorAddChange: ((() -> Unit)?) -> Unit,
    version: String,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val motionAllowed = LocalAppMotionAllowed.current
    val direction = LocalLayoutDirection.current
    val background = LocalAppSurfaceColors.current.page
    val swipeDirection = when {
        editorBackActive || !motionAllowed -> NavSwipeDirection.None
        direction == LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        else -> NavSwipeDirection.LeftToRight
    }
    SettingsEditHost(session) {
        NavDisplay(
            backStack = backStack,
            onBack = onBack,
            modifier = Modifier.fillMaxSize().clipToBounds(),
            transition = if (motionAllowed) AppNavTransition else NavTransitions.None,
            effects = if (motionAllowed) {
                NavDisplayEffects(
                    enableCornerClip = false,
                    dimAmount = 0f,
                    blockInputDuringTransition = true,
                    backdropColor = background,
                )
            } else NavDisplayEffects.None,
        ) {
            entry<SettingsPage>(swipeDismiss = swipeDirection) { page ->
                SettingsPageScope(page) {
                    Column(Modifier.fillMaxSize().background(background)) {
                        when (page) {
                            SettingsPage.Home -> SettingsHome(settings, onChange, onPage, version, listState, contentPadding)
                            SettingsPage.Speed -> SpeedSettingsPage(settings, onChange, contentPadding)
                            SettingsPage.Media -> MediaSettingsPage(settings, onChange, contentPadding)
                            SettingsPage.Subscription -> SubscriptionSettingsPage(contentPadding)
                            SettingsPage.Mirrors -> MirrorSettingsPage(settings, onChange, contentPadding, onEditorBackChange,
                                session, runner, onMirrorAddChange)
                            SettingsPage.Privacy -> PrivacySettingsPage(contentPadding)
                            SettingsPage.About -> AboutSettingsPage(version, contentPadding)
                            SettingsPage.Sponsor -> SponsorSettingsPage(contentPadding)
                        }
                    }
                }
            }
        }
    }
}
