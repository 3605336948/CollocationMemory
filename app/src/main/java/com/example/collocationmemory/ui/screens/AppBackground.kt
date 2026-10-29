package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 App 共用的「当日壁纸背景」。
 *
 * 首页、新学页、复习页都用它，保证：
 * - 同一天在哪个页面看到的都是**同一张**壁纸（[backgroundResForToday] 按星期几取图）；
 * - 透明度一致（压暗层 alpha 相同）；
 * - 模糊程度一致（磨砂感）。
 *
 * 模糊用 `Modifier.blur`（Compose 1.6.1 已提供，位于 androidx.compose.ui.draw）。
 * 注意：只模糊**壁纸这一层**，不模糊上面的文字与卡片 ——
 * 否则卡片里的英文和选项会一起糊掉，没法看。
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    /** 模糊半径。数值越大越糊；14dp 左右是「看得出磨砂但不影响辨认」的区间 */
    blurRadius: Dp = DEFAULT_BLUR_RADIUS,
    /** 压暗层透明度：越大字越清楚、壁纸越暗。与首页保持一致 */
    scrimAlpha: Float = DEFAULT_SCRIM_ALPHA
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = backgroundResForToday()),
            contentDescription = null,
            // 放大一点点再模糊，避免边缘出现半透明的虚边
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            contentScale = ContentScale.Crop
        )
        // 压暗层：保证最亮的壁纸上白字也看得清（与首页同一个值）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
        )
    }
}

/** 默认模糊半径：够看出磨砂感，又不至于糊成一片 */
val DEFAULT_BLUR_RADIUS = 14.dp

/** 默认压暗层透明度：与首页一致 */
const val DEFAULT_SCRIM_ALPHA = 0.18f
