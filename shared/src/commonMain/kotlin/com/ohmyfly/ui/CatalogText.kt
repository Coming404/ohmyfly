// 目录固定按钮和正文使用有界自动字号，长正文保留换行及内容高度。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 上限沿用原有文字层级，避免短文案被放大到不符合卡片比例的字号。
internal object CatalogTextSizing {
    val Button = TextAutoSize.StepBased(13.sp, 15.sp, 0.5.sp)
    val CompactButton = TextAutoSize.StepBased(12.sp, 14.sp, 0.5.sp)
    val Caption = TextAutoSize.StepBased(11.sp, 13.sp, 0.5.sp)
    val Notice = TextAutoSize.StepBased(12.sp, 14.sp, 0.5.sp)
    val Body = TextAutoSize.StepBased(13.sp, 15.sp, 0.5.sp)
}

// 仅保留 Home 指示条等系统栏安全区；键盘高度由 OverlayBottomSheet 默认 imePadding 消费一次。
@Composable
internal fun catalogSheetMargin(): DpSize = DpSize(
    16.dp, WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 12.dp,
)

@Composable
internal fun catalogInputStyle() = MiuixTheme.textStyles.main.copy(fontSize = 15.sp)

// Miuix TextButton未暴露autoSize，使用同样的Button基础和默认尺寸承载可缩放文字。
@Composable
internal fun CatalogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors,
        minWidth = 0.dp, minHeight = 44.dp,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp), cornerRadius = AppCorners.Control) {
        Text(text, style = MiuixTheme.textStyles.button, autoSize = CatalogTextSizing.Button,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
