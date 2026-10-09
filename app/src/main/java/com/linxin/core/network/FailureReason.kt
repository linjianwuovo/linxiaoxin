package com.linxin.core.network

/**
 * 服务端说"失败"时，原因可能落在 msg / code / result / message / message_ 任意一个键上，
 * 而且可能是空串——直接 `a ?: b ?: 兜底` 会让空串通过，界面上就只剩一个"重试"，什么都不解释。
 * 所有取失败原因的地方都走这里。
 */
fun failureReason(vararg candidates: String?, fallback: String): String =
    candidates.firstOrNull { !it.isNullOrBlank() }?.trim() ?: fallback
