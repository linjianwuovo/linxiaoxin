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
}
