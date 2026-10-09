package com.linxin.feature.card.data

import okhttp3.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * 完美校园 / 一卡通 H5：`hub.17wanxiao.com` → `open.17wanxiao.com` → `ecardh5.17wanxiao.com`。
 *
 * 会话是三步换出来的，全部有出处（2026-10-09 抓包 + 直接读 `hub.17wanxiao.com/bsacs/light.action`
 * 返回的那段 HTML —— 它不用登录就能拿到，里面写着 `$.ajax({type:"post", url:"/bsacs/redirect.action",
 * data:'userData=…'})`，成功回调是 `window.top.location.replace(msg.url)`）：
 *
 * 1. GET `light.action`（带门户身份参数，只为拿 hub 的 cookie）；
 * 2. POST `redirect.action`，body 只有一个 `userData`，值是那段 JSON 字符串 —— **响应里的 `url` 才是
 *    authorize 地址**，里面那个 `token` 是服务端生成的，不用我们拼；
 * 3. GET 那个 `url`（302 落到 `ecardh5/ecard_t/index.html`，顺带把 ecardh5 的会话 cookie 种下）。
 *
 * 之后所有业务调用都复用一个地址：`POST ecardh5/ecardh5/bootcallback`，靠表单里的 `gotowhere` 分流，
 * 身份全在 cookie 里，body 不再带 accessToken。Content-Type 是
 * `application/x-www-form-urlencoded;charset=UTF-8`（读它 index.js 的 axios 默认头确认过）。
 *
 * 已实测的只读 gotowhere：`XYK_BASE_INFO`（余额）、`XYK_TRADE_DETAIL`（交易明细，
 * 参数 `beginIndex` / `pageSize` / `type` / `beginDate` / `endDate`）。
 * JS 里还有 `XYK_LOST_CARD_ENCRYPT` / `XYK_MODIFY_PASSWORD_ENCRYPT` / `XYK_UNBIND_ENCRYPT` 三个写操作，
 * 它们只是把 `password`/`oldpwd`/`newpwd` 用 RSA 加密（公钥来自 `gotowhere=userInfo` 响应的
 * `rsaPublicKey`）—— 都是要动钱和凭证的写操作，一个都没接。
 */
interface CampusCardApi {

    @GET
    suspend fun openBootstrap(@Url url: String): String

    @FormUrlEncoded
    @POST
    suspend fun redirect(@Url url: String, @Field("userData") userData: String): CardRedirectResponse

    @GET
    suspend fun followAuthorize(@Url url: String): Response

    @FormUrlEncoded
    @POST("ecardh5/bootcallback")
    suspend fun baseInfo(
        @Field("gotowhere") gotowhere: String = "XYK_BASE_INFO",
    ): CardBaseResponse

    @FormUrlEncoded
    @POST("ecardh5/bootcallback")
    suspend fun trades(
        @Field("beginIndex") beginIndex: Int,
        @Field("pageSize") pageSize: Int,
        @Field("type") type: String,
        @Field("beginDate") beginDate: String,
        @Field("endDate") endDate: String,
        @Field("gotowhere") gotowhere: String = "XYK_TRADE_DETAIL",
    ): CardTradeResponse
}

/** `redirect.action` 的响应。`ecard_customerid` 之类身份字段故意不接。 */
data class CardRedirectResponse(
    val url: String?,
    val error: Boolean?,
    val result_: Boolean?,
    val message_: String?,
)

/** `gotowhere=XYK_BASE_INFO`，抓包原样：金额是数字，`my_fare` 是字符串 */
data class CardBaseResponse(
    val code_: Int?,
    val result_: Boolean?,
    val message_: String?,
    val data: CardBaseRow?,
)

data class CardBaseRow(
    val main_fare: Double?,
    val subsidy_fare: Double?,
    val fund_fare: Double?,
    val my_fare: String?,
    val status: Int?,
    val nouseDate: String?,
)

/** `gotowhere=XYK_TRADE_DETAIL`。抓到的那次是空账本（`{"size":0,"data":[]}`），所以行字段没有出处。 */
data class CardTradeResponse(
    val code_: Int?,
    val result_: Boolean?,
    val message_: String?,
    val data: CardTradeShell?,
)

data class CardTradeShell(
    val size: Int?,
)

/** 界面用的余额卡 */
data class CardBalance(
    val mainFare: Double,
    val subsidyFare: Double,
    val fundFare: Double,
    val totalText: String,
    val status: Int,
    val validUntil: String,
)
