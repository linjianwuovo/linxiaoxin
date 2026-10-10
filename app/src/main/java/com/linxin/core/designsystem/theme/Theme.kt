package com.linxin.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 全站唯一主题入口：纯 Miuix + 安大信色板。
 * 不用 Monet 动态取色（跟系统壁纸走会把品牌色冲掉、显脏），
 * 主色调改由用户在「主题外观 → 主题色」里自选，通过 accent 注入。
 */
@Composable
fun LinXinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = LxTerraRaw,
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme, accent) { lxMiuixColors(darkTheme, accent) }
    CompositionLocalProvider(
        LocalLxIsDark provides darkTheme,
        LocalLxAccent provides accent,
    ) {
        // textStyles 全量注入 MiSans：Miuix 的 Text 默认取 LocalTextStyles.current.main
        // （basic/Text.kt:108），14 个 style 都换上就等于全站生效。
        MiuixTheme(colors = colors, textStyles = LxTextStyles) {
            content()
        }
    }
}

private fun lxMiuixColors(dark: Boolean, accent: Color): Colors {
    return if (dark) {
        val darkAccent = resolvedAccent(accent, true)
        darkColorScheme(
            primary = darkAccent,
            onPrimary = LxDarkSurfaceRaw,
            primaryContainer = lerp(LxDarkSurfaceRaw, darkAccent, 0.30f),
            onPrimaryContainer = darkAccent,
            secondary = LxDarkSecondaryRaw,
            onSecondary = LxDarkSurfaceRaw,
            secondaryContainer = LxDarkSecondaryContainerRaw,
            onSecondaryContainer = LxDarkSecondaryRaw,
            background = LxDarkBackgroundRaw,
            onBackground = LxDarkOnBackgroundRaw,
            surface = LxDarkSurfaceRaw,
            onSurface = LxDarkOnSurfaceRaw,
            surfaceVariant = LxDarkSurfaceVariantRaw,
            onSurfaceVariantSummary = LxDarkOnSurfaceVariantRaw,
            onSurfaceVariantActions = LxDarkOnSurfaceVariantRaw,
            onSurfaceSecondary = LxDarkOnSurfaceVariantRaw,
            outline = LxDarkOutlineRaw,
            dividerLine = LxDarkSurfaceVariantRaw,
            error = LxDarkRoseRaw,
            onError = LxDarkSurfaceRaw,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(Color.White, accent, 0.12f),
            onPrimaryContainer = accent,
            secondary = LxSageRaw,
            onSecondary = Color.White,
            secondaryContainer = LxSageSoftRaw,
            onSecondaryContainer = LxSageRaw,
            background = LxParchmentRaw,
            onBackground = LxInkRaw,
            surface = LxCardRaw,
            onSurface = LxInkRaw,
            surfaceVariant = LxSandRaw,
            onSurfaceVariantSummary = LxInkMutedRaw,
            onSurfaceVariantActions = LxInkMutedRaw,
            onSurfaceSecondary = LxInkMutedRaw,
            outline = LxSandDeepRaw,
            dividerLine = LxSandRaw,
            error = LxRoseRaw,
            onError = Color.White,
        )
    }
}
