package com.linxin.core.luckin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 瑞幸 capi 网关的**带登录态**客户端。
 *
 * 2026-10-12 实测：`m.lkcoffee.com/capi/` 这个网关会把瑞幸 App 的私有接口一起路由出来，
 * 而且不校验 App 那套 native 签名（`X-LK-AKV` / encryptType 都不需要）：
 *
 *   POST resource/m/sys/base/validcode   发验证码
 *   POST resource/m/user/login           手机号+验证码登录，type=1
 *   GET  resource/m/user/baseinfo/detail 我
 *   GET  resource/core/v2/order/list     订单列表
 *   GET  resource/m/user/wallet/accountBalance 余额
 *
 * 之前判死的「H5 没有手机号登录」是错的：那条只在 H5 自己的 SPA 里成立，
 * 接口本身在这个网关上是活的。业务代码在瑞幸 App 私有目录里是明文 JS bundle，
 * 参数形状是从那儿抄的（只抄字段名和流程，代码没搬）。
 *
 * 会话只存 Cookie，存在应用私有 SharedPreferences 里，不进日志、不进仓库。
 */
object LuckinAuth {

    private const val BASE = "https://m.lkcoffee.com/capi/"
    private const val PREF = "linxin_luckin_web"
    private const val K_SSID = "LK_PROD_LUCKYWAP_SSID"
    private const val K_CSRF = "LK_prod_csrfToken"

    class ApiError(override val message: String, val status: String = "ERROR") : Exception(message)

    data class Profile(val name: String, val mobile: String, val balance: String)

    private val gson = Gson()
    private val jar = PersistingJar()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cookieJar(jar)
            .build()
    }

    /** 冷启动调一次，把上次登录的 Cookie 捞回来 */
    fun init(ctx: Context) {
        jar.prefs = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        jar.prefs?.let { p ->
            p.getString(K_SSID, null)?.let { jar.put(cookieOf(K_SSID, it)) }
            p.getString(K_CSRF, null)?.let { jar.put(cookieOf(K_CSRF, it)) }
        }
    }

    /** 从私有存储里还原出来的 Cookie 没有 Expires/Domain 元信息，按网关的域补一个 */
    private fun cookieOf(name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).domain("m.lkcoffee.com").path("/").build()

    fun isLoggedIn(): Boolean = jar.has(K_SSID)

    fun logout() {
        jar.clear()
        jar.prefs?.edit()?.clear()?.apply()
    }

    // ---------------- 对外三个动作 ----------------

    /** 发验证码。返回服务端原文，界面上好把「60 秒冷却」「要滑块」这类话直接显示出来 */
    suspend fun sendCode(phone: String): String = withContext(Dispatchers.IO) {
        val j = post("resource/m/sys/base/validcode", mapOf(
            "mobile" to phone,
            "callCode" to "86",
        ))
        requireSuccess(j)
        j.str("msg") ?: "已发送"
    }

    /** 手机号+验证码登录。成功后 Cookie 落到私有存储，界面只需要知道成没成 */
    suspend fun login(phone: String, code: String): Unit = withContext(Dispatchers.IO) {
        val j = post("resource/m/user/login", mapOf(
            "type" to "1",
            "mobile" to phone,
            "validateCode" to code,
            "countryNo" to "86",
            "callCode" to "86",
            "regionId" to "CO0001",
            "deviceId" to "",
            "blackBox" to "",
            "deptId" to "",
            "regId" to "",
            "uniqueCode" to "",
            "appleId" to "",
            "loginRegion" to "{\"shopRegion\":\"\",\"appRegion\":\"CN\"}",
            "memberCanPush" to "0",
        ))
        requireSuccess(j)
        jar.persist()
    }

    suspend fun profile(): Profile = withContext(Dispatchers.IO) {
        val j = get("resource/m/user/baseinfo/detail", mapOf("type" to 1))
        requireSuccess(j)
        val c = j.obj("content") ?: throw ApiError("没有内容")
        Profile(
            name = c.str("userName") ?: c.str("name") ?: "",
            mobile = c.str("mobile") ?: "",
            balance = c.str("accountBalance") ?: "",
        )
    }

    // ---------------- 通用请求，后面下单/订单/支付都走这两个 ----------------

    suspend fun getApi(path: String, params: Map<String, Any?>): JsonObject =
        withContext(Dispatchers.IO) { get(path, params) }

    suspend fun postApi(path: String, params: Map<String, Any?>): JsonObject =
        withContext(Dispatchers.IO) { post(path, params) }

    private fun requireSuccess(j: JsonObject) {
        if ((j.str("status") ?: "") != "SUCCESS") {
            throw ApiError(j.str("msg") ?: "瑞幸没回话", j.str("status") ?: "ERROR")
        }
    }

    private fun get(path: String, params: Map<String, Any?>): JsonObject {
        // 这里只让 URLEncoder 编码一次，所以直接拼字符串，不走 HttpUrl（那个版本没有 toHttpUrl 扩展）
        val q = if (params.isEmpty()) "" else "?queryParamsStr=" + URLEncoder.encode(gson.toJson(params), "UTF-8")
        return exec(Request.Builder().url("$BASE$path$q").header("User-Agent", UA).build())
    }

    private fun post(path: String, params: Map<String, Any?>): JsonObject {
        val fb = FormBody.Builder()
        // 嵌套的列表和对象必须按 JSON 字符串递。直接 toString() 会拼成 "[{a=1}]" 这种 Java 味字符串，
        // 服务端认不出来，只会回「商品列表参数不能为空」
        params.forEach { (k, v) ->
            fb.add(k, when (v) {
                null -> ""
                is JsonElement -> v.toString()
                is List<*>, is Map<*, *> -> gson.toJson(v)
                else -> v.toString()
            })
        }
        jar.get(K_CSRF)?.let { fb.add("_csrf", it) }
        return exec(Request.Builder()
            .url(BASE + path)
            .header("User-Agent", UA)
            .header("Origin", "https://m.lkcoffee.com")
            .header("Referer", "https://m.lkcoffee.com/")
            .post(fb.build())
            .build())
    }

    private fun exec(req: Request): JsonObject {
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw ApiError("空响应")
            // 网关正常回的是 JSON。回 HTML 说明根本没到瑞幸的业务层，是被前面的 WAF 挡了
            if (body.trimStart().startsWith("<")) {
                throw ApiError("HTTP ${resp.code}：请求被网关挡在外面，没到瑞幸业务层。" +
                    "手机挂着 VPN/代理的话，让 m.lkcoffee.com 走直连再试一次。")
            }
            return runCatching { gson.fromJson(body, JsonObject::class.java) }
                .getOrElse { throw ApiError("回的不是 JSON：${body.take(60)}") }
        }
    }

    // ---------------- Cookie 落地 ----------------

    private class PersistingJar : CookieJar {
        private val map = mutableMapOf<String, Cookie>()
        var prefs: android.content.SharedPreferences? = null

        fun has(name: String) = map.containsKey(name)
        fun get(name: String): String? = map[name]?.value
        fun put(c: Cookie) { map[c.name] = c }
        fun clear() = map.clear()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { map[it.name] = it }
            persist()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = map.values.toList()

        fun persist() {
            val p = prefs ?: return
            p.edit()
                .putString(K_SSID, map[K_SSID]?.value)
                .putString(K_CSRF, map[K_CSRF]?.value)
                .apply()
        }
    }

    // ---------------- Gson 小工具，JsonNull 一律吞掉 ----------------

    private fun JsonObject.str(key: String): String? =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()

    private fun JsonObject.obj(key: String): JsonObject? =
        runCatching { get(key)?.takeIf { it.isJsonObject }?.asJsonObject }.getOrNull()

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; M2011K2C) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0 Mobile Safari/537.36 LinXin/1.0"
}
