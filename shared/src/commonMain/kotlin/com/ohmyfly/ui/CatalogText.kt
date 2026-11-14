// 目录固定按钮和正文使用有界自动字号，长正文保留换行及内容高度。
package com.ohmyfly.ui

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 上限沿用原有文字层级，避免短文案被放大到不符合卡片比例的字号。
internal object CatalogTextSizing {
    val Button = TextAutoSize.StepBased(14.sp, 17.sp, 0.5.sp)
    val CompactButton = TextAutoSize.StepBased(12.sp, 14.sp, 0.5.sp)
    val Caption = TextAutoSize.StepBased(11.sp, 13.sp, 0.5.sp)
    val Notice = TextAutoSize.StepBased(12.sp, 14.sp, 0.5.sp)
    val Body = TextAutoSize.StepBased(14.sp, 17.sp, 0.5.sp)
}

// Miuix TextButton未暴露autoSize，使用同样的Button基础和默认尺寸承载可缩放文字。
@Composable
internal fun CatalogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors) {
        Text(text, style = MiuixTheme.textStyles.button, autoSize = CatalogTextSizing.Button,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
