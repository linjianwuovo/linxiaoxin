package com.linxin.core.locale

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.linxin.core.settings.AppLanguage
import java.util.Locale

/**
 * 不重启进程的语言切换。
 *
 * 系统那套 per-app locale（[android.app.LocaleManager]）一改就杀掉进程，而本工程的开屏动画带
 * 最短展示时长，切换后必然重放开屏 —— 用户看到的就是"闪一下"；而且当目标语言与系统语言等效时
 * （简体中文 vs 跟随系统），系统认为配置没变、根本不重启，分段按钮的高亮会停在原地像是点不动。
 *
 * Compose 1.12 的 `stringResource` 取的是 `LocalResources.current`（字节码里是 getLocalResources），
 * 所以只要往下提供一个按目标 locale 造出来的 Resources，切换就是纯 recomposition：即时、无重启、
 * Android 8 也能用。[AppLanguage.FOLLOW_SYSTEM] 时不覆盖，直接沿用 context 自身的资源，
 * 于是系统语言（含用户在系统设置里给本应用单独选的语言）照常生效。
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val tag = language.tag
    if (tag == null) {
        content()
        return
    }
    val context = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val override = remember(tag, context, baseConfiguration) {
        val configuration = Configuration(baseConfiguration)
        // Configuration 上是 setLocales/getLocales（不是 setLocaleList），单数 setLocale 只是旧写法。
        configuration.setLocales(LocaleList.forLanguageTags(tag))
        context.createConfigurationContext(configuration).resources
    }
    CompositionLocalProvider(
        LocalResources provides override,
        LocalConfiguration provides override.configuration,
        content = content,
    )
}
