package com.linxin.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CshRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CheckinRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SportsRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LaborRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CreditRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class FifRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class FifOkHttpClient
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IzuoyeRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RepairRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LeaveRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CardRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenRefreshInterceptor: TokenRefreshInterceptor,
        netTraceInterceptor: NetTraceInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            // 放最外层：耗时里要把 TokenRefreshInterceptor 自己触发的重登也算进去
            .addInterceptor(netTraceInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(tokenRefreshInterceptor)
            .addInterceptor(logging)
            // OkHttp 默认每个主机只放 5 条并发，AI 课堂一屏就是六发同一个域名，
            // 排在后面的那几发光排队就要好几秒 —— 课程页「整体很慢、还容易超时」有这个成分在。
            // 放宽到 8 条，让六发真的一起走，也就不用为了排队把加载拆成两波。
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 16
                    maxRequestsPerHost = 8
                },
            )
            // 空闲连接 30 秒就丢，别拿一条服务器那边早断了的老连接去发请求：
            // 2026-10-13 真机日志里同一屏总有一发发出去完全没回音（课件那一发挂到 25 秒超时），
            // 这种"挂死"最像就是复用到了一条半开的旧连接。
            .connectionPool(ConnectionPool(8, 30, TimeUnit.SECONDS))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // readTimeout 只管"单次读"，服务器每隔 29 秒挤一个字节就能把请求挂几分钟
            // （真机实测 getWorkingCourseRecord 挂了 214 秒）。callTimeout 是整条调用的绝对上限。
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRetrofit(client: OkHttpClient, baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides @Singleton @AuthRetrofit
    fun provideAuthRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_AUTH + "/")

    @Provides @Singleton @MainRetrofit
    fun provideMainRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_SCPS + "/")

    @Provides @Singleton @CshRetrofit
    fun provideCshRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_CSH + "/")

    @Provides @Singleton @CheckinRetrofit
    fun provideCheckinRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_CHECKIN + "/")

    @Provides @Singleton @SportsRetrofit
    fun provideSportsRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_SPORTS + "/")

    @Provides @Singleton @LaborRetrofit
    fun provideLaborRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_LABOR + "/")

    // 素质学分：复用主 OkHttpClient，AuthInterceptor 默认分支会向 FormBody 注入
    // access_token/_userCode/userId 等通用字段，cqc.aiit.edu.cn 服务端忽略多余参数。
    // 接口真正需要的 studentCode 由 CreditApi 显式 @Field 声明。
    @Provides @Singleton @CreditRetrofit
    fun provideCreditRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_CREDIT + "/")

    // 报修：接口自己带 accessToken/userName/xh/userType 四个字段（照它 H5 的 userInfo 合并法），
    // 所以 AuthInterceptor 对这个域名不注入门户那套通用字段，见那边的 repair 分支。
    @Provides @Singleton @RepairRetrofit
    fun provideRepairRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_REPAIR + "/")

    // 请假（业务流转引擎）：身份五件套由 LeaveApi 自己声明，AuthInterceptor 对该域名跳过注入
    @Provides @Singleton @LeaveRetrofit
    fun provideLeaveRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_LEAVE + "/")

    // ─── FIF AI课堂 ───

    @Provides @Singleton
    fun provideFifCookieJar(): CookieJar {
        val store = ConcurrentHashMap<String, MutableList<Cookie>>()
        return object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = store.getOrPut(url.host) { mutableListOf() }
                synchronized(list) {
                    for (new in cookies) {
                        list.removeAll { it.name == new.name && it.path == new.path && it.domain == new.domain }
                        list.add(new)
                    }
                    val now = System.currentTimeMillis()
                    list.removeAll { it.expiresAt < now }
                }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val list = store[url.host] ?: return emptyList()
                val now = System.currentTimeMillis()
                return synchronized(list) {
                    list.removeAll { it.expiresAt < now }
                    list.filter { it.matches(url) }
                }
            }
        }
    }

    @Provides @Singleton @FifOkHttpClient
    fun provideFifOkHttpClient(cookieJar: CookieJar, netTraceInterceptor: NetTraceInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(netTraceInterceptor)
            .addInterceptor(logging)
            // OkHttp 默认每个主机只放 5 条并发，AI 课堂一屏就是六发同一个域名，
            // 排在后面的那几发光排队就要好几秒 —— 课程页「整体很慢、还容易超时」有这个成分在。
            // 放宽到 8 条，让六发真的一起走，也就不用为了排队把加载拆成两波。
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 16
                    maxRequestsPerHost = 8
                },
            )
            // 空闲连接 30 秒就丢，别拿一条服务器那边早断了的老连接去发请求：
            // 2026-10-13 真机日志里同一屏总有一发发出去完全没回音（课件那一发挂到 25 秒超时），
            // 这种"挂死"最像就是复用到了一条半开的旧连接。
            .connectionPool(ConnectionPool(8, 30, TimeUnit.SECONDS))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton @FifRetrofit
    fun provideFifRetrofit(@FifOkHttpClient client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_FIF + "/")

    @Provides @Singleton @IzuoyeRetrofit
    fun provideIzuoyeRetrofit(@FifOkHttpClient client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_IZUOYE + "/")
}
