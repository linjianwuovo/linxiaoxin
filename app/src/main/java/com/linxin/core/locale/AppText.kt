package com.linxin.core.locale

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes

/**
 * 给非组合上下文（ViewModel / Service / 通知调度器）用的取词入口。
 *
 * 为什么不用 context.getString：应用内语言是我们自己在 [ProvideAppLanguage] 里用
 * createConfigurationContext 覆盖的，Application Context 的 Resources 跟的是**系统**
 * locale，不跟这个覆盖，用它取词会拿到错的语言。所以由 [ProvideAppLanguage] 每次
 * 生效时把当前 Resources 写进这里，两边就是同一套语言。
 *
 * 局限：已经存进 UiState 的字符串不会在切换语言后自动重译 —— 这些都是一次性的错误
 * 提示与通知文案，重新触发一次即可，因此不做失效处理。
 */
object AppText {
    @Volatile
    private var current: Resources? = null

    /** Application 启动时调一次，保证第一个 ViewModel 取词时不为空。 */
    fun init(context: Context) {
        if (current == null) current = context.resources
    }

    fun update(resources: Resources) {
        current = resources
    }

    fun str(@StringRes id: Int, vararg args: Any?): String {
        val res = current ?: return ""
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }
}
