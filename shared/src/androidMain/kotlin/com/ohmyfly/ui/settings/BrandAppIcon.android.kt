package com.ohmyfly.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

@Composable
internal actual fun BrandAppIcon(dark: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val iconName = if (dark) "brand_app_icon_dark" else "brand_app_icon_light"
    val iconId = remember(context, dark) {
        context.resources.getIdentifier(iconName, "drawable", context.packageName).also {
            check(it != 0) { "Missing app resource: $iconName" }
        }
    }
    Image(
        painter = painterResource(iconId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
