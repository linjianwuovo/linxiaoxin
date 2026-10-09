package com.linxin.feature.repair.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * 报修平台 `repair.aiit.edu.cn/bxjlSjd/`，与安小信「报修申请」那个 H5 同源。
 *
 * 协议不是猜的，两条证据：① 2026-10-09 抓包（见 BX-PROTOCOL / BX-PROTOCOL-DEEP 脱敏结论）；
 * ② 直接读它 H5 的 JS（`https://repair.aiit.edu.cn/dist-app/static/js/…`）——
 * 所有 `*ForSjd.do` 都走同一个 `post()` 助手：`qs.stringify(merge(业务参数, 全局 userInfo))`，
 * 也就是**表单编码**，不是 JSON（它另外有个 `postJson` 才带 `application/json`，报修接口一个都没用）。
 * 全局 userInfo 就是 `accessToken` / `userName` / `xh` / `userType` 这四个，每个请求都并进 body，
 * 所以这里四个字段在每个方法上都显式声明，不靠 AuthInterceptor 注入（那个分支已在
 * `AuthInterceptor` 里对 repair 域名跳过，免得把门户那套 `access_token`/`_userCode`/`appId` 多塞进去）。
 *
 * 抓包里 `getWdbxListForSjd.do` 除身份四件套外没有任何业务参数，一次回全部报修单，所以没有分页字段。
 * 另外 JS 里还有三个抓包没出现的接口，等做申请时再用：`getBxxzForSjd.do`（报修须知）、
 * `getXqByXhForSjd.do`（按学号取信息）、`saveOrUpdateBxdForSjd.do`（**写**，提交报修）。
 */
interface RepairApi {

    /** 我的报修单列表 */
    @FormUrlEncoded
    @POST("bxjlSjd/getWdbxListForSjd.do")
    suspend fun getMyRepairs(
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairListResponse

    /** 报修单详情，键是报修单号 bxdh */
    @FormUrlEncoded
    @POST("bxjlSjd/getBxdXqForSjd.do")
    suspend fun getRepairDetail(
        @Field("bxdh") bxdh: String,
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairDetailResponse

    /** 报修单的流程时间线 */
    @FormUrlEncoded
    @POST("bxjlSjd/getLctByBxdhForSjd.do")
    suspend fun getRepairFlow(
        @Field("bxdh") bxdh: String,
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairFlowResponse
}
