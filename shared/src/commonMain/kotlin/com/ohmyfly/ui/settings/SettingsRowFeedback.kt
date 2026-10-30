package com.ohmyfly.ui.settings

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsRowFeedback(content: @Composable () -> Unit) {
    val color = MiuixTheme.colorScheme.onBackground
    val indication = remember(color) { SettingsRowIndication(color) }
    CompositionLocalProvider(LocalIndication provides indication, content = content)
}

private data class SettingsRowIndication(val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        SettingsRowIndicationNode(interactionSource, color)
}

private class SettingsRowIndicationNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private var alpha by mutableFloatStateOf(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressed = false
            var hovered = false
            var focused = false
            var held = false
            var fade: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                    is HoverInteraction.Enter -> hovered = true
                    is HoverInteraction.Exit -> hovered = false
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    is HoldDownInteraction.HoldDown -> held = true
                    is HoldDownInteraction.Release -> held = false
                    else -> return@collect
                }
                val target = (if (pressed || held) 0.10f else 0f) +
                    (if (hovered) 0.06f else 0f) + (if (focused) 0.08f else 0f)
                fade?.cancel()
                if (interaction is PressInteraction.Cancel || target >= alpha) {
                    alpha = target
                } else {
                    // A quick tap can emit Press/Release in one frame; keep its release briefly visible.
                    fade = launch {
                        animate(alpha, target, animationSpec = tween(80)) { value, _ -> alpha = value }
                    }
                }
            }
        }
    }

    override fun onDetach() {
        alpha = 0f
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (alpha > 0f) drawRect(color = color, alpha = alpha)
    }
}
