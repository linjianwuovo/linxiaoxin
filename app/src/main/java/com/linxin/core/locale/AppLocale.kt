package com.linxin.core.locale

import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * 语言切换完全交给系统的 per-app locale（Android 13+ 的 [android.app.LocaleManager]），
 * 不引入 appcompat：持久化和切换后的应用重启都由系统负责，所以这里只读写、不自建状态。
 * Android 12 及以下系统没有这个 API，[supported] 为 false，设置页据此隐藏该行。
 */
object AppLocale {
    const val FOLLOW_SYSTEM = 0
    const val SIMPLIFIED_CHINESE = 1
    const val ENGLISH = 2

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun manager(context: Context): android.app.LocaleManager? =
        context.getSystemService(android.app.LocaleManager::class.java)

    fun current(context: Context): Int {
        val locales = manager(context)?.applicationLocales ?: return FOLLOW_SYSTEM
        if (locales.isEmpty) return FOLLOW_SYSTEM
        val tag = locales.toLanguageTags()
        return when {
            tag.startsWith("en") -> ENGLISH
            tag.startsWith("zh") -> SIMPLIFIED_CHINESE
            else -> FOLLOW_SYSTEM
        }
    }

    fun set(context: Context, choice: Int) {
        val locales: LocaleList = when (choice) {
            SIMPLIFIED_CHINESE -> LocaleList.forLanguageTags("zh-CN")
            ENGLISH -> LocaleList.forLanguageTags("en")
            else -> LocaleList.getEmptyLocaleList()
        }
        val current = current(context)
        if (current == choice) return
        // 设完系统会重启进程，标记必须先同步落盘，否则开屏动画会重放一次（=闪屏）。
        LocaleSwitchFlag.mark(context)
        manager(context)?.applicationLocales = locales
    }
}
