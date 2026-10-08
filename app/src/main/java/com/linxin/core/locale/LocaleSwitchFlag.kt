package com.linxin.core.locale

import android.content.Context

/**
 * 系统 per-app 语言一改，进程就会被重启；[com.linxin.MainActivity] 的开屏动画带最短展示时长，
 * 于是切换后先看到一遍开屏再进主界面，视觉上就是"闪一下"。
 *
 * 这里用 SharedPreferences 而不是 DataStore：写必须在调用 LocaleManager 之前**同步落盘**，
 * 因为紧接着进程就没了，异步写会被丢掉。MainActivity 启动时消费一次即清除。
 */
object LocaleSwitchFlag {
    private const val FILE = "linxin_locale_switch"
    private const val KEY = "pending_restart"

    fun mark(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).commit()
    }

    /** 返回 true 表示这次启动是语言切换引起的，应当跳过开屏动画。 */
    fun consume(context: Context): Boolean {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY, false)
        if (pending) prefs.edit().remove(KEY).commit()
        return pending
    }
}
