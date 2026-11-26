@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ohmyfly.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentModeScaleAspectFill

@Composable
internal actual fun BrandAppIcon(dark: Boolean, modifier: Modifier) {
    val name = if (dark) "BrandAppIcon-Dark" else "BrandAppIcon-Light"
    UIKitView(
        factory = {
            UIImageView().apply {
                image = UIImage.imageNamed(name)
                contentMode = UIViewContentModeScaleAspectFill
                clipsToBounds = true
            }
        },
        modifier = modifier,
        update = { it.image = UIImage.imageNamed(name) },
    )
}
