package com.linxin.core.network

import android.os.SystemClock
import com.linxin.core.settings.DeveloperPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

/**
 * 最近网络请求的环形缓冲，只在"我的→关于→启用调试功能"打开时记录。
 * 响应体里含 token 与个人信息，所以默认关死，且内容只进内存不落盘。
 */
@Singleton
class NetTrace @Inject constructor(
    developerPrefs: DeveloperPrefs,
) {
    data class Entry(
        val timeMs: Long,
        val method: String,
        val url: String,
        val code: Int,
        val ms: Long,
        val request: String?,
        val response: String?,
        val failure: String?,
        val requestHeaders: List<String> = emptyList(),
        val responseHeaders: List<String> = emptyList(),
    )

    @Volatile
    var enabled: Boolean = false
        private set

    private val entries = ArrayDeque<Entry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            developerPrefs.isAdvancedEnabled.collectLatest { enabled = it }
        }
    }

    fun record(entry: Entry) {
        if (!enabled) return
        synchronized(entries) {
            if (entries.size >= MAX) entries.removeFirst()
            entries.addLast(entry)
        }
    }

    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList() }

    fun count(): Int = synchronized(entries) { entries.size }

    fun clear() = synchronized(entries) { entries.clear() }

    fun asText(): String {
        val list = snapshot()
        if (list.isEmpty()) return "（没有记录：调试开关打开后，先去做一次要排查的操作再回来复制）"
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        return buildString {
            appendLine("林小信 网络记录 ${list.size} 条")
            list.forEachIndexed { i, e ->
                appendLine("#${i + 1} ${fmt.format(Date(e.timeMs))} ${e.method} ${e.url}")
                appendLine("    ${e.code} · ${e.ms}ms")
                e.failure?.let { appendLine("    失败: $it") }
                if (e.requestHeaders.isNotEmpty()) appendLine("    请求头: ${e.requestHeaders.joinToString("; ")}")
                e.request?.let { appendLine("    请求: ${it.take(REQ_MAX)}") }
                if (e.responseHeaders.isNotEmpty()) appendLine("    响应头: ${e.responseHeaders.joinToString("; ")}")
                e.response?.let { appendLine("    响应: ${it.take(RES_MAX)}") }
            }
        }.trimEnd()
    }

    private companion object {
        const val MAX = 40
        const val REQ_MAX = 1000
        const val RES_MAX = 1500
    }
}

@Singleton
class NetTraceInterceptor @Inject constructor(
    private val trace: NetTrace,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!trace.enabled) return chain.proceed(request)

        val start = SystemClock.elapsedRealtime()
        val reqHeaders = request.headers.names().sorted().map { "$it: ${brief(it, request.header(it))}" }
        val reqBody = requestBody(request)
        val response = runCatching { chain.proceed(request) }
        val ms = SystemClock.elapsedRealtime() - start

        response.exceptionOrNull()?.let { err ->
            trace.record(
                NetTrace.Entry(
                    timeMs = System.currentTimeMillis(),
                    method = request.method,
                    url = request.url.toString(),
                    code = -1,
                    ms = ms,
                    request = reqBody,
                    response = null,
                    failure = "${err.javaClass.simpleName}: ${err.message}",
                    requestHeaders = reqHeaders,
                ),
            )
            throw err
        }

        val resp = response.getOrThrow()
        val isHtml = resp.header("Content-Type").orEmpty().contains("html", ignoreCase = true)
        trace.record(
            NetTrace.Entry(
                timeMs = System.currentTimeMillis(),
                method = request.method,
                url = request.url.toString(),
                code = resp.code,
                ms = ms,
                request = reqBody,
                response = if (isHtml) "<html 已省略>"
                    else runCatching { resp.peekBody(RES_PEEK).string() }.getOrNull(),
                failure = null,
                requestHeaders = reqHeaders,
                responseHeaders = resp.headers.names().sorted()
                    .map { "$it: ${brief(it, resp.header(it))}" },
            ),
        )
        return resp
    }

    /** 长值只留头尾，够比对两次请求的 token 是否同一个；空值原样显示成空，方便看出"没带上"。 */
    private fun brief(name: String, value: String?): String {
        val v = value.orEmpty()
        return if (v.length > 36) "${v.take(18)}…${v.takeLast(8)}" else v
    }

    private fun requestBody(request: okhttp3.Request): String? = runCatching {
        val body = request.body ?: return null
        val buffer = Buffer()
        body.writeTo(buffer)
        buffer.readUtf8()
    }.getOrNull()

    private companion object {
        const val RES_PEEK = 4096L
    }
}
