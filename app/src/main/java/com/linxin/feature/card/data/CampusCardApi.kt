package com.linxin.feature.card.data

import okhttp3.ResponseBody
import retrofit2.Response
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

    /**
     * 只为拿 hub 的 cookie，返回的是那段引导 HTML。
     * 这里必须用 `Response<ResponseBody>` 而不是 `String`：Retrofit 只挂了 Gson 转换器，
     * 声明成 String 会拿 HTML 去当 JSON 解析，直接 "malformed JSON at line 1 column 1"。
     */
    @GET
    suspend fun openBootstrap(@Url url: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST
    suspend fun redirect(@Url url: String, @Field("userData") userData: String): CardRedirectResponse

    @GET
    suspend fun followAuthorize(@Url url: String): Response<ResponseBody>

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

    /**
     * `gotowhere=userInfo`：只为拿 `data.data.rsaPublicKey`（写操作加密用）。
     * 响应里还有 virtualCardWeakPwd 之类的敏感配置，**一个都不接、不显示**。
     */
    @FormUrlEncoded
    @POST("ecardh5/bootcallback")
    suspend fun userInfo(
        @Field("gotowhere") gotowhere: String = "userInfo",
    ): CardUserInfoResponse

    /**
     * 充值：可用的第三方渠道。H5 的调用是 `card_thirdWays({})`，body 只有 gotowhere。
     * 渠道在 `data.gateways`，是个数组（它自己 `o.gateways.forEach` 遍历），
     * 每项读 gateway_id / gateway_type / gateway_name / gateway_icon / gateway_info（chunk 40）。
     */
    @FormUrlEncoded
    @POST("ecardh5/bootcallback")
    suspend fun payChannels(
        @Field("gotowhere") gotowhere: String = "gateway",
    ): CardChannelsResponse

    /**
     * 充值下单。字段照它 chunk 17/40/57 的调用点原样：`opfare` 是**以元为单位的字符串**
     * （整份 JS 里没有 *100 / toFixed / 除 100，值本身就是 `parseFloat` 出来的）；
     * `return_url` 在非微信环境是 `isWeixin() && openid` 求值出的布尔 false，原样发成 "false"；
     * 它还有个 `recharge_wallet`（充补贴钱包那一档）是路由参数带过来的，没有就不发这个键 ——
     * 我们只充主钱包，所以不发。
     */
    @FormUrlEncoded
    @POST("ecardh5/bootcallback")
    suspend fun recharge(
        @Field("opfare") opfare: String,
        @Field("gateway_id") gatewayId: String,
        @Field("gateway_type") gatewayType: String,
        @Field("return_url") returnUrl: String,
        @Field("gotowhere") gotowhere: String = "pay",
    ): CardPayResponse

    /**
     * 查支付结果：H5 只在结果页调一次、不轮询，body {orderNo}；
     * 读 `data.payflag`，只有 "2" 算到账，其余（包括缺省）它自己兜成 "3" 显示失败页。
     * 真正的轮询在厂商收银台里（`wapnew.17wanxiao.com/WapCashDesk/queryOrder`），付完会跳回带
     * `?partnerjourno=` 的地址 —— 那个数就是这里要的 orderNo。
     */
    @FormUrlEncoded
    @POST("ecardh5/bootcallback")
    suspend fun payStatus(
        @Field("orderNo") orderNo: String,
        @Field("gotowhere") gotowhere: String = "payStatus",
    ): CardPayStatusResponse

    /**
     * 挂失。读它 H5 的 i18n 确认：整页只有一个 6 位查询密码输入框，
     * body 里除 `gotowhere` 外只有 `password`（RSA 加密后）。
     * `gotowhere` 按 JS 的习惯放最后一个键。
     */
    @FormUrlEncoded
    @POST("ecardh5/bootcallback")
    suspend fun lostCard(
        @Field("password") password: String,
        @Field("gotowhere") gotowhere: String = "XYK_LOST_CARD_ENCRYPT",
    ): CardWriteResponse

    /** 改查询密码：加密的是 `oldpwd` 和 `newpwd` 两个字段（拦截器里写死的分支） */
    @FormUrlEncoded
    @POST("ecardh5/bootcallback")
    suspend fun modifyPassword(
        @Field("oldpwd") oldpwd: String,
        @Field("newpwd") newpwd: String,
        @Field("gotowhere") gotowhere: String = "XYK_MODIFY_PASSWORD_ENCRYPT",
    ): CardWriteResponse

    /** 解绑：home 页的调用就是 `{password}` 一个业务字段，身份全在 cookie */
    @FormUrlEncoded
    @POST("ecardh5/bootcallback")
    suspend fun unbind(
        @Field("password") password: String,
        @Field("gotowhere") gotowhere: String = "XYK_UNBIND_ENCRYPT",
    ): CardWriteResponse
}

/** `gotowhere=userInfo` 里只取公钥，其余键一律不声明 */
data class CardUserInfoResponse(
    val code_: Int?,
    val result_: Boolean?,
    val message_: String?,
    val message: String?,
    /** 它请求层的成功判据是 `code_==0 || success || result_`，三个键都可能缺席，所以都可空 */
    val success: Boolean?,
    val data: CardUserInfoRow?,
)

data class CardUserInfoRow(
    val rsaPublicKey: String?,
    /**
     * 只接充值页要用的限额/预设/开关；同一份响应里的 virtualCardWeakPwd 之类一律不声明、不显示。
     *
     * 全部收成 String 是有意的：抓包里 `quota` 是 JSON 数字（`500.00`）、`rechargeAlertNum` 是字符串 `"20"`，
     * 它 JS 里 `quota` 走 `Number(...)`、`moneyData` 走 `JSON.parse(...)`、`limitLowMoneySwitch` 当布尔用 ——
     * Gson 读 `String?` 时数字、布尔、字符串都吃得下，反过来声明成 Double/Boolean 一旦服务端改了类型
     * 就是整份响应解析失败，页面直接白屏。
     */
    val quota: String?,
    /** JSON 字符串，形如 `[{"money":"10","selected":true},…]`，只用里面的 `money` */
    val moneyData: String?,
    val limitLowMoney: String?,
    val limitLowMoneySwitch: String?,
    /** `showPayWay=false` 时它不走渠道列表，直接用这份默认渠道配置（也是 JSON 字符串）下单 */
    val showPayWay: String?,
    val defaultPayWayCfg: String?,
    /** 为真时它 H5 要先走短信验证页（`ecardPayQueryMobile`/`sendEcardPaySms`/`ecardPayMobileVerify`）才让下单 */
    val ecardPayVerifyMobile: String?,
)

/**
 * 写操作的响应：外层成功看 `code_==0 || result_`，
 * 业务层还有第二级状态 `data.result_code`（0 才算成），解绑成功时可能带 `reBindUrl`。
 */
data class CardWriteResponse(
    val code_: Int?,
    val result_: Boolean?,
    val message_: String?,
    val message: String?,
    /** 它请求层的成功判据是 `code_==0 || success || result_`，三个键都可能缺席，所以都可空 */
    val success: Boolean?,
    val data: CardWriteData?,
)

data class CardWriteData(
    val result_code: Int?,
    val reBindUrl: String?,
    val message: String?,
)

/** `redirect.action` 的响应。`ecard_customerid` 之类身份字段故意不接。 */
data class CardRedirectResponse(
    val url: String?,
    val error: Boolean?,
    val result_: Boolean?,
    val message_: String?,
    /** 服务端出异常时这个键给原文（如 `javax.net.ssl.SSLException: Connection reset`），`message_` 给"失败" */
    val message: String?,
)

/** `gotowhere=XYK_BASE_INFO`，抓包原样：金额是数字，`my_fare` 是字符串 */
data class CardBaseResponse(
    val code_: Int?,
    val result_: Boolean?,
    val message_: String?,
    val message: String?,
    /** 它请求层的成功判据是 `code_==0 || success || result_`，三个键都可能缺席，所以都可空 */
    val success: Boolean?,
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
    val message: String?,
    /** 它请求层的成功判据是 `code_==0 || success || result_`，三个键都可能缺席，所以都可空 */
    val success: Boolean?,
    val data: CardTradeShell?,
)

data class CardTradeShell(
    val size: Int?,
    /** 2026-10-09 在电脑上跑通整条链后拿到的真实行，字段名照它原样 */
    val data: List<CardTradeRow>?,
)

data class CardTradeRow(
    /** 摘要，如「餐费支出-支付宝4」 */
    val accdscrp: String?,
    val amount: Double?,
    /** 业务时间，已经是「2026-10-09 08:26:30」这种可读格式 */
    val businessopdt: String?,
    val description: String?,
    /** 终端名，如「人脸1号」 */
    val term_name: String?,
    val orderno: String?,
    val type: String?,
    val flag: String?,
    val isdelay: String?,
)

/** 界面用的一行流水 */
data class CardTrade(
    val time: String,
    val title: String,
    val place: String,
    /** 正数是入账，负数是消费；服务端给的就是带符号的金额 */
    val amount: Double,
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

/** `gotowhere=gateway`：第三方渠道列表 */
data class CardChannelsResponse(
    val code_: Int?,
    val result_: Boolean?,
    val message_: String?,
    val message: String?,
    /** 它请求层的成功判据是 `code_==0 || success || result_`，三个键都可能缺席，所以都可空 */
    val success: Boolean?,
    val data: CardChannelsShell?,
)

data class CardChannelsShell(
    val gateways: List<CardChannel>?,
)

data class CardChannel(
    val gateway_id: String?,
    val gateway_type: String?,
    val gateway_name: String?,
    val gateway_info: String?,
)

/** `gotowhere=pay`：下单结果。ecardh5type 在信封外层（H5 取的是回调第三个参数=整个 body） */
data class CardPayResponse(
    val code_: Int?,
    val result_: Boolean?,
    val message_: String?,
    val message: String?,
    /** 它请求层的成功判据是 `code_==0 || success || result_`，三个键都可能缺席，所以都可空 */
    val success: Boolean?,
    val ecardh5type: Int?,
    val data: CardPayData?,
)

data class CardPayData(
    /** 有它就是付款地址：H5 干的是 self.location.href = request_content */
    val request_content: String?,
    /** 查支付结果要用的单号 */
    val jourorderno: String?,
    /**
     * `ecardh5type==2` 时它改调 `epaySdk.callPay`，喂给 SDK 的就是下面这几个键。
     * 全部声明成 `JsonElement`：那份 SDK 里 `orderInfo` 是「字符串就 JSON.parse、否则当对象」两种都收，
     * 服务端给的是哪一种我们说了不算，声明成 String 或对象都有一种会当场解析炸掉。
     */
    val orderInfo: com.google.gson.JsonElement?,
    val callPaywayid: com.google.gson.JsonElement?,
    val callAccountid: com.google.gson.JsonElement?,
    val projectPaywayList: com.google.gson.JsonElement?,
    val extend: com.google.gson.JsonElement?,
)

data class CardPayStatusResponse(
    val code_: Int?,
    val result_: Boolean?,
    val message_: String?,
    val message: String?,
    /** 它请求层的成功判据是 `code_==0 || success || result_`，三个键都可能缺席，所以都可空 */
    val success: Boolean?,
    val data: CardPayStatus?,
)

data class CardPayStatus(
    val payflag: String?,
)
