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

    fun resetSession() {
        sessionReady = false
    }

    private suspend fun ensureSession() {
        if (sessionReady) return
        val creds = tokenManager.snapshot()
        val token = creds.accessToken.orEmpty()
        val xh = creds.userCode.orEmpty()
        val name = creds.userName.orEmpty()
        val userType = creds.userType ?: "1"
        if (token.isBlank() || xh.isBlank()) error("还没登录，换不出一卡通会话")

        val hub = ApiConstants.BASE_WANXIAO_HUB
        // 第 1 步：只为拿 hub 的 cookie，返回的那段 HTML 本身不用解析
        api.openBootstrap(
            "$hub/bsacs/light.action?flag=${ApiConstants.ECARD_FLAG}" +
                "&ecardFunc=index&access_token=$token" +
                "&_userCode=$xh&code=$xh&userCode=$xh" +
                "&_userName=${URLEncoder.encode(name, "UTF-8")}" +
                "&_userType=$userType&appId=${ApiConstants.APP_ID}" +
                "&returnFromIscToAppFunc=ReturnDefault",
        )

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
        api.followAuthorize(authorizeUrl).use { resp ->
            if (!resp.isSuccessful) throw Exception("一卡通授权跳转失败（${resp.code}）")
        }
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
     * 本月流水条数。抓到的那次账本是空的（`{"size":0,"data":[]}`），
     * 所以**行的字段名没有出处**，这里只取有出证的 `size`，不去猜每一行长什么样。
     */
    suspend fun monthTradeCount(beginDate: String, endDate: String): Result<Int> {
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
                return Result.failure(Exception(resp.message_ ?: "交易明细加载失败"))
            }
            Result.success(resp.data?.size ?: 0)
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "交易明细加载失败", e))
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

    @Provides
    @Singleton
    @CardCookieJar
    fun provideCardCookieJar(): CookieJar = object : CookieJar {
        val cookies = mutableMapOf<String, MutableList<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies0: List<Cookie>) {
            val host = url.host
            val list = cookies.getOrPut(host) { mutableListOf() }
            list.removeAll { old -> cookies0.any { it.name == old.name && it.path == old.path } }
            list += cookies0
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return cookies[url.host].orEmpty().filter { it.expiresAt > now }
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
