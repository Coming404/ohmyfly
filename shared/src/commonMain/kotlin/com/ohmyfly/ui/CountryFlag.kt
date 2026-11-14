// 国旗按应用地区代码查找；CloudFlare 使用品牌图标，未知代码不替换为其他国家。
package com.ohmyfly.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.carlsen.flagkit.FlagKit

@Composable
internal fun CountryFlag(region: String, modifier: Modifier = Modifier) {
    if (region.equals("cloudflare", ignoreCase = true)) {
        Image(imageVector = cloudFlareIcon, contentDescription = "CloudFlare", modifier = modifier)
        return
    }
    val code = region.trim().uppercase()
    val flag = remember(code) { FlagKit.getFlag(code) }
    flag?.let { Image(imageVector = it, contentDescription = "地区 $code", modifier = modifier) }
}
