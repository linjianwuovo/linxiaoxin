package com.linxin.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// ─────────────────────────────────────────────────────────────
// 原始字面量色板（不跟随主题，供 Theme.kt 构造 ColorScheme 使用）
// 外层 `val Lx*` 才是跟随 LocalLxIsDark 的 @Composable token
//
// 命名沿用历史品牌名（Terra/Sage/Plum…），但取值已换成
// “干净现代·白底蓝主色”调色板：冷调近白背景 + 蓝色主色 + 青色辅色。
// 名字不改是为了避免 36 个界面文件的连锁改动。
// ─────────────────────────────────────────────────────────────

// ── Light 基底 ──
internal val LxParchmentRaw = Color(0xFFF4F6F9)
internal val LxCreamRaw = Color(0xFFFAFBFD)
internal val LxCardRaw = Color(0xFFFFFFFF)
internal val LxSandRaw = Color(0xFFEDF0F5)
internal val LxSandDeepRaw = Color(0xFFDCE1E9)
internal val LxCardBorderRaw = Color(0x0F000000)

internal val LxInkRaw = Color(0xFF191C22)
internal val LxInkSoftRaw = Color(0xFF3B4149)
internal val LxInkMutedRaw = Color(0xFF6A7280)
internal val LxInkFaintRaw = Color(0xFF99A1AE)
internal val LxInkGhostRaw = Color(0xFFC5CBD5)

internal val LxTerraRaw = Color(0xFF2563EB)
internal val LxTerraSoftRaw = Color(0x1F2563EB)
internal val LxTerraGlowRaw = Color(0x142563EB)
internal val LxSageRaw = Color(0xFF0D9488)
internal val LxSageSoftRaw = Color(0x1A0D9488)
internal val LxAmberRaw = Color(0xFFF59E0B)
internal val LxAmberSoftRaw = Color(0x1AF59E0B)
internal val LxPlumRaw = Color(0xFF8B5CF6)
internal val LxPlumSoftRaw = Color(0x1A8B5CF6)
internal val LxSlateRaw = Color(0xFF64748B)
internal val LxSlateSoftRaw = Color(0x1A64748B)
internal val LxRoseRaw = Color(0xFFE5484D)

internal val LxCategoryColorsLight: List<Color> = listOf(
    Color(0xFF2563EB), // 蓝（高数）
    Color(0xFF0D9488), // 青（英语）
    Color(0xFF8B5CF6), // 紫（线代）
    Color(0xFFEC4899), // 粉（数据结构）
    Color(0xFFF59E0B), // 琥珀（思政）
    Color(0xFF64748B), // 石板灰蓝
    Color(0xFFE5484D), // 红（体育）
    Color(0xFF16A34A), // 绿
)

// ── Dark 基底 ──
internal val LxDarkBackgroundRaw = Color(0xFF0E1116)
internal val LxDarkSurfaceRaw = Color(0xFF161A21)
internal val LxDarkSurfaceVariantRaw = Color(0xFF21262F)
internal val LxDarkCreamRaw = Color(0xFF11151B)
internal val LxDarkSandRaw = Color(0xFF21262F)
internal val LxDarkSandDeepRaw = Color(0xFF2C323C)
internal val LxDarkCardBorderRaw = Color(0x14FFFFFF)

internal val LxDarkOnBackgroundRaw = Color(0xFFE7EAF0)
internal val LxDarkOnSurfaceRaw = Color(0xFFE7EAF0)
internal val LxDarkOnSurfaceVariantRaw = Color(0xFF98A1AF)
internal val LxDarkInkSoftRaw = Color(0xFFC3C9D4)
internal val LxDarkInkFaintRaw = Color(0xFF6A7280)
internal val LxDarkInkGhostRaw = Color(0xFF444B57)

internal val LxDarkPrimaryRaw = Color(0xFF7AA2FF)
internal val LxDarkPrimaryContainerRaw = Color(0xFF2B3F6E)
internal val LxDarkTerraSoftRaw = Color(0x337AA2FF)
internal val LxDarkTerraGlowRaw = Color(0x1F7AA2FF)
internal val LxDarkSecondaryRaw = Color(0xFF4FD1C5)
internal val LxDarkSecondaryContainerRaw = Color(0xFF234A4A)
internal val LxDarkSageSoftRaw = Color(0x334FD1C5)
internal val LxDarkAmberRaw = Color(0xFFFBBF24)
internal val LxDarkAmberSoftRaw = Color(0x33FBBF24)
internal val LxDarkPlumRaw = Color(0xFFB79CFF)
internal val LxDarkPlumSoftRaw = Color(0x33B79CFF)
internal val LxDarkSlateRaw = Color(0xFF94A3B8)
internal val LxDarkSlateSoftRaw = Color(0x3394A3B8)
internal val LxDarkRoseRaw = Color(0xFFFF8080)
internal val LxDarkOutlineRaw = Color(0xFF2C323C)

internal val LxCategoryColorsDark: List<Color> = listOf(
    Color(0xFF7AA2FF), // 蓝 提亮
    Color(0xFF4FD1C5), // 青 提亮
    Color(0xFFB79CFF), // 紫 提亮
    Color(0xFFF472B6), // 粉 提亮
    Color(0xFFFBBF24), // 琥珀 提亮
    Color(0xFF94A3B8), // 石板 提亮
    Color(0xFFFF8080), // 红 提亮
    Color(0xFF4ADE80), // 绿 提亮
)

// ─────────────────────────────────────────────────────────────
// 当前是否深色：由 LinXinTheme 依据用户的主题外观设置写入，
// 让全部 Lx* token 跟随设置（而非只跟随系统），修复强制浅色/深色
// 时主色板不重绘的问题。默认 false（未包裹时为浅色）。
// ─────────────────────────────────────────────────────────────
val LocalLxIsDark: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

// ─────────────────────────────────────────────────────────────
// 用户自选主题色（ARGB → Color），由 LinXinTheme 写入；默认品牌蓝。
// LxTerra / LxTerraSoft / LxTerraGlow 全部从它派生，取色器一改，
// 全站主色调 + 首页底部氛围光等遮罩一起跟随。
// ─────────────────────────────────────────────────────────────
val LocalLxAccent: ProvidableCompositionLocal<Color> = staticCompositionLocalOf { LxTerraRaw }

// 深色模式下把主题色向白提亮的比例，保证在深底上有足够对比（与旧 #7AA2FF 观感一致）
internal const val LX_ACCENT_DARK_LIFT = 0.28f

// 当前生效的主题基色：深色下自动提亮，浅色下原样
internal fun resolvedAccent(accent: Color, isDark: Boolean): Color =
    if (isDark) lerp(accent, Color.White, LX_ACCENT_DARK_LIFT) else accent

// ─────────────────────────────────────────────────────────────
// 对外暴露的语义 token —— 随 LocalLxIsDark 自动切换
// 所有 UI 层 import 仍走这些名字，调用点位于 Composable 作用域即可
// ─────────────────────────────────────────────────────────────

// ── 冷调近白背景 ──
val LxParchment: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkBackgroundRaw else LxParchmentRaw
val LxCream: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkCreamRaw else LxCreamRaw
val LxCard: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkSurfaceRaw else LxCardRaw
val LxSand: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkSandRaw else LxSandRaw
val LxSandDeep: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkSandDeepRaw else LxSandDeepRaw
val LxCardBorder: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkCardBorderRaw else LxCardBorderRaw

// ── 文本四级阶梯 ──
val LxInk: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkOnBackgroundRaw else LxInkRaw
val LxInkSoft: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkInkSoftRaw else LxInkSoftRaw
val LxInkMuted: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkOnSurfaceVariantRaw else LxInkMutedRaw
val LxInkFaint: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkInkFaintRaw else LxInkFaintRaw
val LxInkGhost: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkInkGhostRaw else LxInkGhostRaw

// ── 品牌语义色（跟随用户自选主题色 LocalLxAccent）──
val LxTerra: Color @Composable @ReadOnlyComposable get() =
    resolvedAccent(LocalLxAccent.current, LocalLxIsDark.current)
val LxTerraSoft: Color @Composable @ReadOnlyComposable get() =
    resolvedAccent(LocalLxAccent.current, LocalLxIsDark.current)
        .copy(alpha = if (LocalLxIsDark.current) 0.20f else 0.12f)
val LxTerraGlow: Color @Composable @ReadOnlyComposable get() =
    resolvedAccent(LocalLxAccent.current, LocalLxIsDark.current)
        .copy(alpha = if (LocalLxIsDark.current) 0.12f else 0.08f)

val LxSage: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkSecondaryRaw else LxSageRaw
val LxSageSoft: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkSageSoftRaw else LxSageSoftRaw

val LxAmber: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkAmberRaw else LxAmberRaw
val LxAmberSoft: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkAmberSoftRaw else LxAmberSoftRaw

val LxPlum: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkPlumRaw else LxPlumRaw
val LxPlumSoft: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkPlumSoftRaw else LxPlumSoftRaw

val LxSlate: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkSlateRaw else LxSlateRaw
val LxSlateSoft: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkSlateSoftRaw else LxSlateSoftRaw

val LxRose: Color @Composable @ReadOnlyComposable get() =
    if (LocalLxIsDark.current) LxDarkRoseRaw else LxRoseRaw

// ── 历史别名 ──
val LxSuccess: Color @Composable @ReadOnlyComposable get() = LxSage
val LxWarning: Color @Composable @ReadOnlyComposable get() = LxAmber
val LxError: Color @Composable @ReadOnlyComposable get() = LxRose

// ── 课表 / 劳动图表 / 课程小圆点的八色分类色板 ──
val LxCategoryColors: List<Color>
    @Composable @ReadOnlyComposable
    get() = if (LocalLxIsDark.current) LxCategoryColorsDark else LxCategoryColorsLight
