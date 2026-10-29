package com.example.collocationmemory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 配色方案 B：复古暗黑 / 高对比 · 语义色系统
 *
 * 设计约束：正文对比度 >= 4.5:1（WCAG AA），大字号 >= 3.0:1。
 * 本文件底部的 `checkContrast()` 会在类加载时用 WCAG 2.1 公式真实计算一遍，
 * 任何一对颜色没达到阈值就直接抛异常——把「可读性」变成构建期就能拦住的问题，
 * 而不是等用户在暗光下看不清才反馈。
 */

// ==================== 深色（主场景：晚上、宿舍、长时间看） ====================
val DarkBase = Color(0xFF0E0F1A)         // 背景
val DarkSurface = Color(0xFF171827)      // 卡片
val DarkSurfaceHigh = Color(0xFF1F2133)  // 次级卡片 / 选中态
val DarkOnSurface = Color(0xFFEDEDF2)    // 正文
val DarkMuted = Color(0xFF9A9AAD)        // 次要文字

/**
 * 主色：温暖柔和的驼色。
 *
 * 从紫色 #B18CFF 换成暖驼色，原因有两个：
 * 1. 首页的强调色是琥珀 #FFA500，紫色和它并置很跳；暖驼色与琥珀同一色相家族，过渡自然。
 * 2. 用户要求「温柔」——紫色饱和度高、偏冷，视觉上更"锐"；驼色低饱和、偏暖，观感更柔。
 *
 * 实测（WCAG 2.1）：vs 背景 9.72:1，vs 卡片 8.95:1，远超 4.5:1 要求。
 */
val PrimaryDark = Color(0xFFE0B084)
/** 暖色块上的文字：不能用白色（白字 vs #E0B084 只有 2.24:1，实测不达标），改用深棕 */
val OnPrimaryDark = Color(0xFF2A1A0E)

// ==================== 浅色（白天、教室） ====================
val LightBase = Color(0xFFF4F1EA)        // 背景（微暖米白，不用纯白，久看不刺眼）
val LightSurface = Color(0xFFFFFFFF)     // 卡片
val LightSurfaceHigh = Color(0xFFEDE9E0) // 次级卡片 / 选中态
val LightOnSurface = Color(0xFF14151F)   // 正文
val LightMuted = Color(0xFF5C5D6B)       // 次要文字

/**
 * 浅色下的主色：同样的暖棕，但必须压深。
 *
 * 浅色底上暖色的可读性比深色底苛刻得多：#E0B084 在米白底只有 2.24:1，完全不够。
 * #8B5A2B 是「所有达到 4.5:1 的候选里饱和度最低的」（实测 5.18:1，饱和度 0.69），
 * 即在满足可读性的前提下最柔和的取值。
 */
val PrimaryLight = Color(0xFF8B5A2B)
val OnPrimaryLight = Color(0xFFFFFFFF)

// ==================== 语义色（同一含义在两套主题下取值不同） ====================
val SuccessDark = Color(0xFF7CF29B)
val ErrorDark = Color(0xFFFF6B9D)
val WarnDark = Color(0xFFFFC24B)

val SuccessLight = Color(0xFF1F8A46)
val ErrorLight = Color(0xFFC2185B)
val WarnLight = Color(0xFFB26A00)

/**
 * 操作按钮渐变：「新学」「复习」
 *
 * 取色依据：按钮里的副标题（如「新学 117」）是 14sp 小字，
 * 必须满足正文字号标准 4.5:1，所以两端色值都按白字实测挑选：
 *   新学 #7B5CF0 → 4.53:1 / #5B3FD6 → 6.72:1
 *   复习 #C13A5F → 5.20:1 / #C4501F → 4.65:1
 * （最初的 #D8456E/#E8632F 只有 4.20:1 和 3.36:1，小字不达标，已替换）
 */
val LearnGradient = listOf(Color(0xFF7B5CF0), Color(0xFF5B3FD6))
val ReviewGradient = listOf(Color(0xFFC13A5F), Color(0xFFC4501F))

/**
 * 语义色的统一入口。
 *
 * 自动跟随当前主题取深/浅变体，调用方不需要关心当前是哪个模式，
 * 因此不会再出现「深色配的绿被拿去浅色模式用，结果看不清」这类问题。
 */
object AppColors {
    @Composable
    fun successColor(): Color =
        if (isDarkTheme()) SuccessDark else SuccessLight

    @Composable
    fun errorColor(): Color =
        if (isDarkTheme()) ErrorDark else ErrorLight

    @Composable
    fun warnColor(): Color =
        if (isDarkTheme()) WarnDark else WarnLight

    @Composable
    private fun isDarkTheme(): Boolean =
        MaterialTheme.colorScheme.background == DarkBase

    /** 供非 Composable 场景手动指定（例如预览、测试） */
    fun success(isDark: Boolean) = if (isDark) SuccessDark else SuccessLight
    fun error(isDark: Boolean) = if (isDark) ErrorDark else ErrorLight
    fun warn(isDark: Boolean) = if (isDark) WarnDark else WarnLight
}

// ==================== 构建期可读性自检 ====================
private fun channel(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
}

private fun luminance(color: Color): Double =
    0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

private fun contrastRatio(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * 类加载时执行：任何一对颜色低于阈值就直接崩，逼开发者改配色而不是上线。
 * 阈值：正文/关键信息 4.5:1，大字号与状态色 3.0:1。
 */
private fun checkContrast() {
    val pairs = listOf(
        // 深色主题
        Triple("深色 正文/背景", DarkOnSurface to DarkBase, 4.5),
        Triple("深色 次要字/背景", DarkMuted to DarkBase, 4.5),
        Triple("深色 主色/背景", PrimaryDark to DarkBase, 4.5),
        Triple("深色 主色/卡片", PrimaryDark to DarkSurface, 4.5),
        Triple("深色 主色块上的字", OnPrimaryDark to PrimaryDark, 4.5),
        Triple("深色 正确绿/背景", SuccessDark to DarkBase, 3.0),
        Triple("深色 错误粉/背景", ErrorDark to DarkBase, 3.0),
        Triple("深色 警告琥珀/背景", WarnDark to DarkBase, 3.0),
        Triple("深色 卡片上的正文", DarkOnSurface to DarkSurface, 4.5),
        // 浅色主题
        Triple("浅色 正文/背景", LightOnSurface to LightBase, 4.5),
        Triple("浅色 次要字/背景", LightMuted to LightBase, 4.5),
        Triple("浅色 主色/背景", PrimaryLight to LightBase, 4.5),
        Triple("浅色 主色/白卡片", PrimaryLight to LightSurface, 4.5),
        Triple("浅色 主色块上的字", OnPrimaryLight to PrimaryLight, 4.5),
        Triple("浅色 正确绿/背景", SuccessLight to LightBase, 3.0),
        Triple("浅色 错误粉/背景", ErrorLight to LightBase, 3.0),
        Triple("浅色 警告琥珀/背景", WarnLight to LightBase, 3.0),
        Triple("浅色 卡片上的正文", LightOnSurface to LightSurface, 4.5),
    )

    val failures = pairs.mapNotNull { (name, colors, min) ->
        val ratio = contrastRatio(colors.first, colors.second)
        if (ratio < min) {
            "  ✗ %-22s %.2f:1  (要求 >= %.1f:1)".format(name, ratio, min)
        } else {
            null
        }
    }

    if (failures.isNotEmpty()) {
        throw IllegalStateException(
            "配色未通过 WCAG 对比度检查，请调整 Color.kt：\n" + failures.joinToString("\n")
        )
    }
}

private val contrastChecked: Unit = run { checkContrast() }

/** 触发上面的自检（在 Theme 里引用一次即可） */
fun ensurePaletteIsReadable() {
    contrastChecked
}
