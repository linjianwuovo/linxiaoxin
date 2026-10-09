package com.linxin.core.network

/**
 * 服务端说"失败"时，原因可能落在 msg / code / result / message / message_ 任意一个键上，
 * 而且可能是空串——直接 `a ?: b ?: 兜底` 会让空串通过，界面上就只剩一个"重试"，什么都不解释。
 * 所有取失败原因的地方都走这里。
 *
 * 另外几种"登录态没了"的说法（`非法访问`、`token验证失败`）是服务端原话，
 * 光贴出来人不知道下一步该干什么，所以补一句怎么办。
 * 注意这里只改**文案**：这些失败回的是 `code:"0" + flag:false`，不是 HTTP 401，
 * `TokenRefreshInterceptor` 不会介入，要不要真去做自动重登是另一件事，得单独点头。
 */
private val LOGIN_STALE_MARKS = listOf(
    "非法访问",
    "没有访问权限",
    "token验证失败",
    "token 验证失败",
    "登录已失效",
    "登录失效",
    "会话失效",
)

fun failureReason(vararg candidates: String?, fallback: String): String {
    val reason = candidates.firstOrNull { !it.isNullOrBlank() }?.trim() ?: fallback
    return if (LOGIN_STALE_MARKS.any { reason.contains(it, ignoreCase = true) }) {
        "$reason —— 登录态可能过期了（账号是单设备登录，在别的手机登录会把这个顶下线），去「我的」里重新登录一次再进来。"
    } else {
        reason
    }
}
