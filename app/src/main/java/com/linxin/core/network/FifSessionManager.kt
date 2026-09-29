package com.linxin.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linxin.core.auth.TokenManager
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.fifDataStore: DataStore<Preferences> by preferencesDataStore(name = "fif_session")

/**
 * FIF AI课堂独立会话管理。
 * 与校内 TokenManager 完全分离，管理 FIF SSO 登录态。
 */
@Singleton
class FifSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    @FifOkHttpClient private val fifClient: OkHttpClient,
    private val cookieJar: CookieJar,
) {
    companion object {
        private val KEY_FIF_TOKEN = stringPreferencesKey("fif_token")
        private val KEY_STUDENT_ID = stringPreferencesKey("student_id")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_SCHOOL_ID = stringPreferencesKey("school_id")
        private val KEY_MEMBER_USER_ID = stringPreferencesKey("member_user_id")
    }

    // 并行加载时 getCourses / getWorkingRecord 会同时调 ensureSession，
    // 会话临界失效时可能并发触发两次 SSO 互相覆盖 Cookie。用锁串行化。
    private val ssoMutex = Mutex()

    suspend fun getStudentId(): String? =
        context.fifDataStore.data.first()[KEY_STUDENT_ID]

    suspend fun getUserName(): String? =
        context.fifDataStore.data.first()[KEY_USER_NAME]

    suspend fun getFifToken(): String? =
        context.fifDataStore.data.first()[KEY_FIF_TOKEN]

    suspend fun getSchoolId(): String? =
        context.fifDataStore.data.first()[KEY_SCHOOL_ID]

    suspend fun getMemberUserId(): String? =
        context.fifDataStore.data.first()[KEY_MEMBER_USER_ID]

    suspend fun isSessionValid(): Boolean {
        // 课程列表只依赖 token + SESSION Cookie；学号缺失（映射失败降级）不应判为无效，
        // 否则每次进页面都会强制重新 SSO。
        val token = getFifToken()
        if (token.isNullOrBlank()) return false
        // 关键：token 是带 exp 的 JWT，过期后 Cookie 仍在会"看起来有效"，
        // 导致 AI课堂时好时坏。这里主动识别过期，逼出一次干净的重新登录。
        if (isFifTokenExpired(token)) {
            android.util.Log.w("FifSession", "FIF token 已过期，判定会话无效以触发重新登录")
            return false
        }
        return hasSessionCookie()
    }

    suspend fun hasSessionCookie(): Boolean = withContext(Dispatchers.IO) {
        val fifUrl = ApiConstants.BASE_FIF.toHttpUrl()
        cookieJar.loadForRequest(fifUrl).any { cookie ->
            cookie.name.equals("SESSION", ignoreCase = true) && cookie.value.isNotBlank()
        }
    }

    /**
     * 执行完整的 SSO 登录链路:
     * 1. 携带校内 token 请求 aiitpass.fifedu.com
     * 2. 从 302 Location 提取 FIF token
     * 3. 跟随重定向建立 Cookie 会话
     * 4. 调用 getAiktUserIdByMemberId 获取 FIF 用户标识
     */
    suspend fun performSso(): Result<Unit> = ssoMutex.withLock {
        withContext(Dispatchers.IO) {
        try {
            val accessToken = tokenManager.getAccessToken().orEmpty()
            val userCode = tokenManager.getUserCode().orEmpty()
            val userName = tokenManager.getUserName().orEmpty()
            val userType = tokenManager.getUserType().orEmpty()

            if (accessToken.isBlank() || userCode.isBlank()) {
                return@withContext Result.failure(Exception("校内登录信息已失效，请重新登录"))
            }

            // Step 1: SSO 跳转，提取 FIF token
            // 注意：?token=&app=axx 这种"参数在但值为空"的跳转，queryParameter 返回的是空串
            // 而不是 null，只判 null 会把空 token 当登录成功存下来，之后每个接口都回
            // "鉴权失败，Token为空"，再触发重登，形成几十次往返的慢循环。
            val fifToken = performSsoRedirect(accessToken, userCode, userName, userType)
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(
                    Exception("AI 课堂登录失败：单点登录没有返回 token，通常是校内登录已过期。请到「我的 → 退出登录」重新登录一次。")
                )

            // Step 2: 用 token 访问 FIF 首页建立 Cookie
            establishSession(fifToken)

            // Step 3: 用户映射（带退避重试；会话刚建立时服务端可能尚未就绪）
            val userInfo = fetchUserMappingWithRetry(fifToken)
            if (userInfo == null) {
                // 映射失败不再致命：课程列表只依赖 token + SESSION Cookie，
                // 学号/姓名缺失仅影响"正在上课"等附属查询，不应挡住整页。
                android.util.Log.w("FifSession", "用户映射重试后仍失败，降级继续（不阻塞课程加载）")
            }
            val memberUserId = extractMemberUserIdFromFifToken(fifToken)

            // 持久化（映射成功才写身份信息）
            context.fifDataStore.edit { prefs ->
                prefs[KEY_FIF_TOKEN] = fifToken
                if (userInfo != null) {
                    prefs[KEY_STUDENT_ID] = userInfo.studentId
                    prefs[KEY_USER_NAME] = userInfo.userName
                    prefs[KEY_SCHOOL_ID] = userInfo.schoolId
                }
                if (!memberUserId.isNullOrBlank()) {
                    prefs[KEY_MEMBER_USER_ID] = memberUserId
                } else {
                    prefs.remove(KEY_MEMBER_USER_ID)
                }
            }

            // 同步身份 Cookie 到 OkHttp jar，让 qrcodeHandler 等原生请求能从 jar 中读取。
            // 映射失败时回退用校内学号/姓名，保证学期/课程接口始终拿得到身份 Cookie。
            val cookieStudentId = userInfo?.studentId?.takeIf { it.isNotBlank() }
                ?: tokenManager.getUserCode().orEmpty()
            val cookieUserName = userInfo?.userName?.takeIf { it.isNotBlank() }
                ?: tokenManager.getUserName().orEmpty()
            saveIdentityCookies(cookieStudentId, cookieUserName)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("AI课堂登录失败: ${e.message}", e))
        }
    }
    }

    /** token 是否为带 exp 的 JWT 且已过期；无法解析出 exp 时按"未过期"处理，避免误判导致 SSO 死循环。 */
    private fun isFifTokenExpired(token: String, skewSeconds: Long = 60L): Boolean {
        val exp = extractExpFromFifToken(token) ?: return false
        val nowSeconds = System.currentTimeMillis() / 1000L
        return exp <= nowSeconds + skewSeconds
    }

    private fun performSsoRedirect(
        accessToken: String,
        userCode: String,
        userName: String,
        userType: String,
    ): String? {
        val encodedName = URLEncoder.encode(userName, "UTF-8")
        val url = buildString {
            append(ApiConstants.BASE_FIF_SSO)
            append("/iplat-pass-aiit/h5/login")
            append("?access_token=$accessToken")
            append("&_userCode=$userCode")
            append("&code=$userCode")
            append("&userCode=$userCode")
            append("&_userName=$encodedName")
            append("&_userType=$userType")
            append("&appId=${ApiConstants.APP_ID}")
            append("&returnFromIscToAppFunc=ReturnDefault")
        }

        // 禁止自动重定向以手动提取 Location
        val noRedirectClient = fifClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val request = Request.Builder().url(url).get().build()
        val response = noRedirectClient.newCall(request).execute()

        val location = response.header("Location") ?: return null
        response.close()

        // 从 Location 提取 token 参数
        // e.g. https://sttp.fifedu.com/studycenter-nh5/?token=xxx&app=axx&redirect_uri=null
        return try {
            location.toHttpUrl().queryParameter("token")
        } catch (_: Exception) {
            // URL 解析失败时用正则兜底
            Regex("[?&]token=([^&]+)").find(location)?.groupValues?.get(1)
        }
    }

    /**
     * Step 2: 访问 FIF 首页以建立 SESSION Cookie。
     */
    private fun establishSession(token: String) {
        val url = "${ApiConstants.BASE_FIF}/studycenter-nh5/?token=$token&app=axx"
        val request = Request.Builder().url(url).get().build()
        fifClient.newCall(request).execute().close()
    }

    /**
     * 将 SSO 拿到的身份信息写回 OkHttp cookie jar，
     * 让后续原生请求（如 qrcodeHandler）能从 jar 中拼出完整的 Cookie 头。
     */
    private fun saveIdentityCookies(studentId: String, userName: String) {
        val fifUrl = ApiConstants.BASE_FIF.toHttpUrl()
        val cookies = listOfNotNull(
            Cookie.Builder().name("id").value(safeCookieValue(studentId)).domain("fifedu.com").path("/").build().takeIf { studentId.isNotBlank() },
            Cookie.Builder().name("studentId").value(safeCookieValue(studentId)).domain("fifedu.com").path("/").build().takeIf { studentId.isNotBlank() },
            Cookie.Builder().name("currentUserName").value(safeCookieValue(userName)).domain("fifedu.com").path("/").build().takeIf { userName.isNotBlank() },
        )
        if (cookies.isNotEmpty()) {
            cookieJar.saveFromResponse(fifUrl, cookies)
        }
    }

    /**
     * OkHttp 的 Cookie 值只接受 ASCII；中文姓名直接塞进去会抛
     * "Unexpected char 0x... in Cookie value" 并炸掉整条登录链路。
     * 统一做 URL 编码转成 ASCII（学号是数字，编码后不变）。
     */
    private fun safeCookieValue(raw: String): String =
        URLEncoder.encode(raw, "UTF-8").replace("+", "%20")

    /**
     * 用户映射带退避重试：SESSION Cookie 刚建立时服务端可能短暂未就绪，
     * 首次失败间隔 600ms 再试，最多 3 次。
     */
    private fun fetchUserMappingWithRetry(token: String, attempts: Int = 3): FifUserInfo? {
        repeat(attempts) { index ->
            val info = fetchUserMapping(token)
            if (info != null) return info
            if (index < attempts - 1) {
                try {
                    Thread.sleep(600L * (index + 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
        return null
    }

    /**
     * Step 3: 调用用户映射接口获取 FIF 身份。
     */
    private fun fetchUserMapping(token: String): FifUserInfo? {
        val url = "${ApiConstants.BASE_FIF}/studycenter/mobile/common/getAiktUserIdByMemberId"
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.FormBody.Builder().build())
            .header("authorization", "Basic $token")
            .header("Visit-Type", "mobile")
            .build()

        val response = fifClient.newCall(request).execute()
        val body = response.body?.string()
        response.close()

        if (body == null) {
            android.util.Log.e("FifSession", "用户映射响应体为空, code=${response.code}")
            return null
        }
        if (!response.isSuccessful) {
            android.util.Log.e("FifSession", "用户映射HTTP失败, code=${response.code}, body=${body.take(200)}")
            return null
        }

        // 解析 JSON: { data: { id, userType, number, userName, schoolId } }
        return try {
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonObject("data")
            if (data == null) {
                android.util.Log.e("FifSession", "用户映射无data字段, body=${body.take(200)}")
                return null
            }
            FifUserInfo(
                studentId = data.get("id")?.asString ?: return null,
                userName = data.get("userName")?.asString.orEmpty(),
                schoolId = data.get("schoolId")?.asString.orEmpty(),
            )
        } catch (e: Exception) {
            android.util.Log.e("FifSession", "用户映射解析异常: ${e.message}, body=${body.take(200)}")
            null
        }
    }

    /**
     * 构建 FIF 业务请求所需的 authorization header 值。
     */
    suspend fun buildAuthHeader(): String {
        val fifToken = getFifToken().orEmpty()
        return "Basic $fifToken"
    }

    suspend fun appendQrLoginParams(url: String): String {
        val httpUrl = url.toHttpUrl()
        val accessToken = tokenManager.getAccessToken().orEmpty()
        val userCode = tokenManager.getUserCode().orEmpty()
        val userName = tokenManager.getUserName().orEmpty()
        val userType = tokenManager.getUserType().orEmpty()

        if (accessToken.isBlank() || userCode.isBlank()) {
            return url
        }

        return httpUrl.newBuilder().apply {
            if (httpUrl.queryParameter("access_token").isNullOrBlank()) {
                addQueryParameter("access_token", accessToken)
            }
            if (httpUrl.queryParameter("_userCode").isNullOrBlank()) {
                addQueryParameter("_userCode", userCode)
            }
            if (httpUrl.queryParameter("code").isNullOrBlank()) {
                addQueryParameter("code", userCode)
            }
            if (httpUrl.queryParameter("userCode").isNullOrBlank()) {
                addQueryParameter("userCode", userCode)
            }
            if (httpUrl.queryParameter("_userName").isNullOrBlank() && userName.isNotBlank()) {
                addQueryParameter("_userName", userName)
            }
            if (httpUrl.queryParameter("_userType").isNullOrBlank() && userType.isNotBlank()) {
                addQueryParameter("_userType", userType)
            }
            if (httpUrl.queryParameter("appId").isNullOrBlank()) {
                addQueryParameter("appId", ApiConstants.APP_ID)
            }
            if (!httpUrl.queryParameterValues("returnFromIscToAppFunc").contains("ReturnDefault")) {
                addQueryParameter("returnFromIscToAppFunc", "ReturnDefault")
            }
        }.build().toString()
    }

    suspend fun clear() {
        context.fifDataStore.edit { it.clear() }
        // 清除 FIF 相关 Cookie
        val fifUrl = ApiConstants.BASE_FIF.toHttpUrl()
        cookieJar.loadForRequest(fifUrl) // 触发读取，无法直接清除，但会话失效后不影响
    }

    private data class FifUserInfo(
        val studentId: String,
        val userName: String,
        val schoolId: String,
    )
}

internal fun extractMemberUserIdFromFifToken(token: String): String? {
    val payload = token.split('.').getOrNull(1) ?: return null
    return runCatching {
        val decoded = Base64.getUrlDecoder().decode(payload.padBase64Url())
        val payloadJson = String(decoded, StandardCharsets.UTF_8)
        val jsonObject = JsonParser.parseString(payloadJson).asJsonObject
        jsonObject.get("memberId")?.asString
            ?: jsonObject.get("memberUserId")?.asString
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** 从 FIF token(JWT) 的 payload 中取 exp（秒级 Unix 时间戳），解析失败返回 null。 */
internal fun extractExpFromFifToken(token: String): Long? {
    val payload = token.split('.').getOrNull(1) ?: return null
    return runCatching {
        val decoded = Base64.getUrlDecoder().decode(payload.padBase64Url())
        val payloadJson = String(decoded, StandardCharsets.UTF_8)
        JsonParser.parseString(payloadJson).asJsonObject.get("exp")?.asLong
    }.getOrNull()
}

private fun String.padBase64Url(): String {
    val remainder = length % 4
    return if (remainder == 0) this else this + "=".repeat(4 - remainder)
}
