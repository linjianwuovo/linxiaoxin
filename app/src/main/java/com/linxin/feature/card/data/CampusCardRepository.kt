package com.linxin.feature.card.data

import com.linxin.core.auth.TokenManager
import com.linxin.core.network.ApiConstants
import com.linxin.core.network.CardRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 校园卡只读数据层：换会话 + 查余额。
 *
 * 会话是进程内一次性的（`sessionReady`）；一旦某个调用发现被踢回登录，就 `resetSession()` 再换一次。
 * 三个 `_ENCRYPT` 写操作（挂失 / 改密 / 解绑）在这里没有任何入口。
 */
@Singleton
class CampusCardRepository @Inject constructor(
    private val api: CampusCardApi,
    private val tokenManager: TokenManager,
) {

    @Volatile
    private var sessionReady = false

    /**
     * 换会话必须串行：余额和流水两个请求是并行发的，两条链同时跑时后一次的 code
     * 会把前一次换来的会话顶掉，服务端就回"会话失效或前置异常"。
     */
    private val sessionMutex = Mutex()

    fun resetSession() {
        sessionReady = false
    }

    private suspend fun ensureSession() {
        if (sessionReady) return
        sessionMutex.withLock {
            if (sessionReady) return@withLock
            ensureSessionLocked()
        }
    }

    private suspend fun ensureSessionLocked() {
        val creds = tokenManager.snapshot()
        val token = creds.accessToken.orEmpty()
        val xh = creds.userCode.orEmpty()
        val name = creds.userName.orEmpty()
        val userType = creds.userType ?: "1"
        if (token.isBlank() || xh.isBlank()) error("还没登录，换不出一卡通会话")

        val hub = ApiConstants.BASE_WANXIAO_HUB
        // 第 1 步：只为拿 hub 的 cookie，返回的那段 HTML 本身不用解析
        val bootstrap = api.openBootstrap(
            "$hub/bsacs/light.action?flag=${ApiConstants.ECARD_FLAG}" +
                "&ecardFunc=index&access_token=$token" +
                "&_userCode=$xh&code=$xh&userCode=$xh" +
                "&_userName=${URLEncoder.encode(name, "UTF-8")}" +
                "&_userType=$userType&appId=${ApiConstants.APP_ID}" +
                "&returnFromIscToAppFunc=ReturnDefault",
        )
        if (!bootstrap.isSuccessful) throw Exception("一卡通引导页打不开（${bootstrap.code()}）")
        bootstrap.body()?.close()

        // 第 2 步：userData 就是抓包里那串 JSON，键的顺序也照它 H5 里写好的样子来
        val userData = JSONObject()
            .put("access_token", token)
            .put("returnFromIscToAppFunc", "ReturnDefault")
            .put("ecardFunc", "index")
            .put("flag", ApiConstants.ECARD_FLAG)
            .put("code", xh)
            .put("_userName", name)
            .put("_userType", userType)
            .put("appId", ApiConstants.APP_ID)
            .put("_userCode", xh)
            .put("userCode", xh)
            .toString()
        val redirect = api.redirect("$hub/bsacs/redirect.action", userData)
        if (redirect.error == true) {
            throw Exception(redirect.message_ ?: "一卡通账号跳转失败")
        }
        val authorizeUrl = redirect.url ?: throw Exception("一卡通没返回跳转地址")

        // 第 3 步：跟着 302 走完，ecardh5 的会话 cookie 就在这一步种下
        val authorize = api.followAuthorize(authorizeUrl)
        if (!authorize.isSuccessful) throw Exception("一卡通授权跳转失败（${authorize.code()}）")
        authorize.body()?.close()
        sessionReady = true
    }

    suspend fun balance(): Result<CardBalance> {
        return try {
            ensureSession()
            val resp = api.baseInfo()
            if (resp.result_ != true) {
                sessionReady = false
                return Result.failure(Exception(resp.message_ ?: "校园卡接口没有返回余额"))
            }
            val row = resp.data ?: return Result.failure(Exception("校园卡余额数据为空"))
            Result.success(
                CardBalance(
                    mainFare = row.main_fare ?: 0.0,
                    subsidyFare = row.subsidy_fare ?: 0.0,
                    fundFare = row.fund_fare ?: 0.0,
                    totalText = row.my_fare.orEmpty().trim(),
                    status = row.status ?: 0,
                    validUntil = row.nouseDate.orEmpty().trim(),
                ),
            )
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "校园卡加载失败", e))
        }
    }

    /**
     * 本月流水。第一次抓到的是空账本，2026-10-09 在电脑上把整条链跑通后拿到了非空返回，
     * 行字段（`accdscrp`/`amount`/`businessopdt`/`term_name`/`orderno`/`type`…）就是那次的内容。
     */
    suspend fun monthTrades(beginDate: String, endDate: String): Result<List<CardTrade>> {
        return try {
            ensureSession()
            val resp = api.trades(
                beginIndex = 0,
                pageSize = 20,
                type = "-1",
                beginDate = beginDate,
                endDate = endDate,
            )
            if (resp.result_ != true) {
                sessionReady = false
                return Result.failure(Exception(resp.message_ ?: "交易明细加载失败"))
            }
            Result.success(
                resp.data?.data.orEmpty().map { row ->
                    CardTrade(
                        time = row.businessopdt.orEmpty().trim(),
                        title = (row.accdscrp ?: row.description).orEmpty().trim(),
                        place = row.term_name.orEmpty().trim(),
                        amount = row.amount ?: 0.0,
                    )
                },
            )
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "交易明细加载失败", e))
        }
    }

    // ─── 写操作：挂失 / 改密 / 解绑 ───
    // 加密方式读它 H5 的 axios 拦截器确认：jsencrypt 的 setPublicKey/encrypt，
    // 即 RSA/ECB/PKCS1Padding + base64；公钥来自 gotowhere=userInfo 的 data.data.rsaPublicKey。
    // 拦截器只加密 password（改密是 oldpwd/newpwd），不会自动加验证码之类的字段。

    private var rsaPublicKey: String? = null

    private suspend fun publicKey(): String {
        rsaPublicKey?.let { return it }
        ensureSession()
        val resp = api.userInfo()
        val key = resp.data?.rsaPublicKey
        if (resp.result_ != true || key.isNullOrBlank()) {
            throw Exception(resp.message_ ?: "拿不到一卡通加密公钥")
        }
        rsaPublicKey = key
        return key
    }

    private fun encrypt(plain: String, pem: String): String {
        val body = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val spec = java.security.spec.X509EncodedKeySpec(
            android.util.Base64.decode(body, android.util.Base64.DEFAULT),
        )
        val key = java.security.KeyFactory.getInstance("RSA").generatePublic(spec)
        val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        return android.util.Base64.encodeToString(cipher.doFinal(plain.toByteArray()), android.util.Base64.NO_WRAP)
    }

    /** 挂失。挂失后这张卡就不能消费了，界面那边必须二次确认。 */
    suspend fun lostCard(password: String): Result<String> = write {
        api.lostCard(encrypt(password, publicKey()))
    }

    /** 改查询密码。 */
    suspend fun modifyPassword(oldPassword: String, newPassword: String): Result<String> = write {
        val pem = publicKey()
        api.modifyPassword(encrypt(oldPassword, pem), encrypt(newPassword, pem))
    }

    /** 解绑。成功判据是第二级 `data.result_code == 0`。 */
    suspend fun unbind(password: String): Result<String> = write {
        api.unbind(encrypt(password, publicKey()))
    }

    private suspend fun write(call: suspend () -> CardWriteResponse): Result<String> {
        return try {
            ensureSession()
            val resp = call()
            if (resp.code_ != 0 && resp.result_ != true) {
                return Result.failure(Exception(resp.message_ ?: "一卡通操作失败"))
            }
            val data = resp.data
            if (data?.result_code != null && data.result_code != 0) {
                return Result.failure(Exception(data.message ?: resp.message_ ?: "一卡通操作失败"))
            }
            Result.success(data?.reBindUrl.orEmpty())
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "一卡通操作失败", e))
        }
    }
}

/** 一卡通这条链跨三个子域，cookie 必须自己攒着，所以给它一个独立的 client。 */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CardCookieJar

@Module
@InstallIn(SingletonComponent::class)
object CampusCardModule {

    /**
     * 扁平存 + `Cookie.matches(url)` 判域。
     *
     * 不能按 host 分桶：一卡通在 hub 上种的会话 cookie 是 `Domain=.17wanxiao.com` 这种跨子域的，
     * 分桶存进 hub 桶后就再也发不给 ecardh5，服务端直接回"出现异常,请联系运维人员"。
     * 只有 `matches()` 才懂 domain / path / secure 那套规则。
     */
    @Provides
    @Singleton
    @CardCookieJar
    fun provideCardCookieJar(): CookieJar {
        val store = mutableListOf<Cookie>()
        return object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val now = System.currentTimeMillis()
                synchronized(store) {
                    store.removeAll { old ->
                        old.expiresAt < now ||
                            cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path }
                    }
                    store += cookies
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val now = System.currentTimeMillis()
                return synchronized(store) {
                    store.removeAll { it.expiresAt < now }
                    store.filter { it.matches(url) }
                }
            }
        }
    }

    @Provides
    @Singleton
    @CardRetrofit
    fun provideCardRetrofit(client: OkHttpClient, @CardCookieJar jar: CookieJar): Retrofit =
        Retrofit.Builder()
            .baseUrl(ApiConstants.BASE_ECARD_H5 + "/")
            .client(client.newBuilder().cookieJar(jar).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideCampusCardApi(@CardRetrofit retrofit: Retrofit): CampusCardApi =
        retrofit.create(CampusCardApi::class.java)
}
