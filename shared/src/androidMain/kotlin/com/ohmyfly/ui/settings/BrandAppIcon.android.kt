package com.ohmyfly.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ohmyfly.shared.R

@Composable
internal actual fun BrandAppIcon(dark: Boolean, modifier: Modifier) {
    Image(
        painter = painterResource(if (dark) R.drawable.brand_app_icon_dark else R.drawable.brand_app_icon_light),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
