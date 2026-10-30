package com.ohmyfly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmyfly.ui.DriftIcons
import com.ohmyfly.ui.hapticClick
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** App icon blue; the brand tile stays fixed while the rest of the UI follows the theme. */
private val BrandBlue = Color(0xFF346DF1)
private val SponsorPink = Color(0xFFFF2D55)
private const val MOTTO = "每条线路，都值得一次起飞～"

/** Settings home brand card: name, motto, then version; the whole card opens the sponsor page. */
@Composable
internal fun SettingsBoardingPass(version: String, onSponsor: () -> Unit) {
    SettingsCard {
        SettingsRowFeedback {
            BasicComponent(
                insideMargin = PaddingValues(16.dp),
                onClick = hapticClick(onSponsor),
                onClickLabel = "打开赞助",
                role = Role.Button,
                startAction = { BrandTile() },
                endActions = { SponsorChip() },
            ) {
                Text("OhMyFly", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 6.dp))
                Text(MOTTO, fontSize = 13.sp, lineHeight = 18.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                Text(version, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp))
            }
        }
    }
}

@Composable
private fun BrandTile() {
    Box(Modifier.size(52.dp).squircleBackground(BrandBlue, 13.dp), contentAlignment = Alignment.Center) {
        Icon(DriftIcons.BrandMark, contentDescription = null, modifier = Modifier.size(30.dp), tint = Color.White)
    }
}

@Composable
private fun SponsorChip() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier.height(30.dp).background(SponsorPink.copy(alpha = 0.14f), RoundedCornerShape(15.dp))
            .padding(start = 12.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SettingsIcons.Sponsor, contentDescription = null, modifier = Modifier.size(14.dp), tint = SponsorPink)
        Text("赞助", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SponsorPink)
        Icon(MiuixIcons.Basic.ArrowRight, contentDescription = null, tint = SponsorPink,
            modifier = Modifier.size(width = 7.dp, height = 11.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f })
    }
}
