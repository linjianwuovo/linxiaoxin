package com.linxin.feature.messages.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * 门户消息中心，路径前缀 `zhxy-new-scps/msgCenter/`。
 *
 * 参数与字段是 2026-10-09 抓安小信「消息」页拿到的原样：
 * `mesDetailListNew.do` 业务参数只有 `currentPage` / `pageSize` / `sourceType`（外加它自己那套
 * `accessToken` / `xh`，通用鉴权字段由 AuthInterceptor 补），响应外层 `flag/code/msg/data`，
 * `data` 是分页壳，列表在 `data.data`。抓包里 `sourceType=2` 拿到 16 条、`dealFlag=0` 那种拿 0 条，
 * 所以默认视图就是 `sourceType=2`。
 *
 * 两个「标记已读」的接口不在抓包里，是 2026-10-09 直接读安小信那套 H5 的 bundle
 * （消息页在 `chunk-2a2e09ea`，`Last-Modified: 2026-02-04`）拿到的原样：
 * ```js
 * const c = t => post("/msgCenter/batchReadPushMessage.do", t),
 *       d = t => post("/msgCenter/readPushMessage.do", t)
 * onReadPromiss() { confirm(批量阅读).then(() => { c().then(t => { t.flag && this.onRefresh() }) }) }
 * ```
 * 注意 `c()` 是**不带任何业务参数**调用的——批量已读只认身份字段；
 * 而它那个处理函数在厂商代码里真就叫 `onReadPromiss`（Promiss 是拼错的，照记）。
 * 成功只看外层 `flag`，别去猜 `code`。
 *
 * `batchReadPushMessage.do` 最后没接：真机上按它发过一回，回的是 `flag:true`，
 * 可列表里那 8 条 `readFlag="0"` 的日程提醒一条都没变（它弹的文案是「所有待阅流程」，
 * 管的是流程待阅，不是这批推送消息）。要拿它当「一键已读」就是骗人，所以只做单条那个。
 */
interface MessagesApi {

    /** `sourceType` 默认 "2" = 抓包里安小信打开「消息」页时用的来源值。 */
    @FormUrlEncoded
    @POST("msgCenter/mesDetailListNew.do")
    suspend fun getMessages(
        @Field("currentPage") currentPage: Int,
        @Field("pageSize") pageSize: Int,
        @Field("sourceType") sourceType: String = "2",
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        /** 抓包原样：这个接口必须有 userCode，缺了服务端直接回 flag=false msg=系统异常 */
        @Field("userCode") userCode: String,
    ): MessageListResponse

    @FormUrlEncoded
    @POST("msgCenter/notReadCount.do")
    suspend fun getUnreadCount(
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userCode") userCode: String,
    ): UnreadCountResponse

    /**
     * 单条标记已读，参数名就是 `id`（列表行里那串 32 位十六进制）。
     *
     * 真机验过它是真的写服务端：2026-10-09 列表里 8 条 `readFlag="0"`，点开一条发完这个请求，
     * 杀掉进程重进还剩 7 条。所以「一键已读」就是把它按未读的 id 挨个发一遍。
     */
    @FormUrlEncoded
    @POST("msgCenter/readPushMessage.do")
    suspend fun markRead(
        @Field("id") id: String,
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userCode") userCode: String,
    ): MessageFlagResponse
}
