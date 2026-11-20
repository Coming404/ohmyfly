// 使用 Miuix 状态式输入；文字变化交由目录会话防抖，重置由外部条件同步。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.ohmyfly.ui.settings.SettingsInputMenu
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.TextField

@Composable
internal fun FlightFilterInput(
    title: String, value: String, enabled: Boolean, onFocus: () -> Unit, onChange: (String) -> Unit,
) {
    val input = rememberTextFieldState(value)
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onChange)
    LaunchedEffect(value) {
        if (input.text.toString() != value) input.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(input) {
        snapshotFlow { input.text.toString() }.distinctUntilChanged().collect { text ->
            if (text != latestValue) latestChange(text)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = catalogInputStyle())
    SettingsInputMenu {
        TextField(
            state = input, enabled = enabled, textStyle = catalogInputStyle(), insideMargin = DpSize(12.dp, 12.dp),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() }
                .semantics { contentDescription = title }, cornerRadius = AppCorners.Input,
        )
    }
    }
}
