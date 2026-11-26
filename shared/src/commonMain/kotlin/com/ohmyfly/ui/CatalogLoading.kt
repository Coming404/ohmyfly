// 加载占位沿用卡片高度，减少动态效果开启时停止骨架呼吸动画。
package com.ohmyfly.ui

import com.ohmyfly.shapes.AppCorners

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CatalogLoadingIndicator(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 180.dp).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            top.yukonga.miuix.kmp.basic.CircularProgressIndicator(size = 20.dp)
            top.yukonga.miuix.kmp.basic.Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
internal fun CatalogTowerLoading() {
    val alpha = if (LocalAppMotionAllowed.current) {
        rememberInfiniteTransition(label = "towerSkeleton").animateFloat(
            initialValue = 0.4f, targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "towerSkeletonAlpha").value
    } else 0.6f
    val color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f * alpha)
    Card(Modifier.fillMaxWidth().semantics { contentDescription = "正在读取塔台" }, cornerRadius = AppCorners.Card) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(color, AppCorners.shape(AppCorners.Content)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(16.dp).background(color, AppCorners.shape(AppCorners.Skeleton)))
                    Box(Modifier.fillMaxWidth(0.38f).height(12.dp).background(color, AppCorners.shape(AppCorners.Skeleton)))
                }
                Box(Modifier.width(58.dp).height(24.dp).background(color, AppCorners.shape(AppCorners.Card)))
            }
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).background(color, AppCorners.shape(AppCorners.Skeleton)))
            Box(Modifier.fillMaxWidth().height(6.dp).background(color, AppCorners.Capsule))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Box(Modifier.weight(1f).height(44.dp).background(color, AppCorners.shape(AppCorners.Content))) }
            }
        }
    }
}

@Composable
internal fun CatalogLoading(label: String = "正在读取") {
    val alpha = if (LocalAppMotionAllowed.current) {
        rememberInfiniteTransition(label = "catalogSkeleton").animateFloat(
            initialValue = 0.4f, targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeletonAlpha").value
    } else 0.6f
    val color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f * alpha)
    Card(Modifier.fillMaxWidth().semantics { contentDescription = label }, cornerRadius = AppCorners.Card) {
        Row(Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(color, AppCorners.tile(36.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.75f).height(16.dp).background(color, AppCorners.shape(AppCorners.Skeleton)))
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).background(color, AppCorners.shape(AppCorners.Skeleton)))
            }
            Box(Modifier.width(52.dp).height(16.dp).background(color, AppCorners.shape(AppCorners.Skeleton)))
        }
    }
}
