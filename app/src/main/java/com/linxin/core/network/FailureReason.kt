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

fun failureReason(vararg candidates: String?, fallback: String): String =
    loginStaleNotice(candidates.firstOrNull { !it.isNullOrBlank() }?.trim() ?: fallback)

/**
 * 单独拆出来是因为公告那套有自己的映射（`NewsRepository.mapError`），不经过 `failureReason` ——
 * 2026-10-09 17:45 真机上公告页就只剩一句光秃秃的「没有访问权限」。
 * 谁拿到服务端原话都过这一道。
 */
fun loginStaleNotice(reason: String): String =
    if (LOGIN_STALE_MARKS.any { reason.contains(it, ignoreCase = true) }) {
        "$reason —— 登录态可能过期了（账号是单设备登录，在别的手机登录会把这个顶下线），去「我的」里重新登录一次再进来。"
    } else {
        reason
    }

/**
 * 把网络层的异常翻成人能照着做的一句话。
 *
 * 之前各仓库都是 `Exception(e.message ?: "XX失败")`，于是 retrofit 的 HttpException 会把
 * `HTTP 502 Bad Gateway` 这种英文原话直接甩到界面上（2026-10-09 17:30 学校 zhxy-new-scps 那台
 * 后端 502，消息页上显示的就是这句英文）。这里统一：5xx 说「服务器没响应、过会儿再试」，
 * 连不上说「检查网络、可以关掉 VPN」，401 说「去重新登录」。
 * 我们自己抛的那些中文异常（带原因的）原样透出，不覆盖。
 */
fun netFail(t: Throwable, fallback: String): Exception = Exception(netFailMessage(t, fallback), t)

fun netFailMessage(t: Throwable, fallback: String): String {
    (t as? retrofit2.HttpException)?.let { e ->
        return when {
            e.code() == 401 -> "登录已失效（401），去「我的」里重新登录一次"
            e.code() in 500..599 ->
                "学校服务器这会儿没响应（HTTP ${e.code()}），过一会儿再试；挂着 VPN 的话可以关掉再试一次"
            else -> "服务器返回 HTTP ${e.code()}"
        }
    }
    if (t is java.io.IOException) {
        val detail = t.message?.takeIf { it.isNotBlank() }
        return "连不上学校服务器" + (detail?.let { "（$it）" } ?: "") + "，检查下网络，挂着 VPN 的话可以关掉再试"
    }
    return t.message?.takeIf { it.isNotBlank() } ?: fallback
}
