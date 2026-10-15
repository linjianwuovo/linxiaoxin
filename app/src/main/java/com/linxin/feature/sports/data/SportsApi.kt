package com.linxin.feature.sports.data

import com.google.gson.JsonObject
import com.linxin.core.network.SportsRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import javax.inject.Singleton

/**
 * 智慧体育（`sports.aiit.edu.cn:8082`）这一套接口。
 *
 * 路径、参数、返回外壳全部以 2026-10-14 那轮抓包为准（534 条 flow，LSPosed+TrustMe 出明文）。
 * 官方那页是内嵌 H5：`GET /dist/` 回一个 Vue SPA，接口就是它调的这批 mobile 开头的路径；
 * 真正的"跑步中"由原生壳接管（`window.android.goRunning`），上传走
 * `mobile/extra/addExtraCheckNew.do`。
 *
 * 注意这台机只有校内网能到，而且是 **http**（官方代码里 URL 就写着 `http://`）。
 */
interface SportsApi {

    /** 分流用：`result` 直接是字符串「大一大二学生」/「大三大四学生」，两套页互为 xxx / xxxDasan */
    @FormUrlEncoded
    @POST("mobile/sportApp/checkDsStudent.do")
    suspend fun studentGrade(@FieldMap form: Map<String, String>): JsonObject

    /** 首页汇总（今天里程、任务进度、阈值、dsFlag） */
    @FormUrlEncoded
    @POST("mobile/sportApp/startIndex.do")
    suspend fun homeSummary(@FieldMap form: Map<String, String>): JsonObject

    /** 课外跑步计划：`data.extraInfo{planMile,completeMile,completeRatio,extraId,isExtra}` */
    @FormUrlEncoded
    @POST("mobile/index/extraInfo.do")
    suspend fun plan(@FieldMap form: Map<String, String>): JsonObject

    /** 约束全在这条：`data.extraDetail{mixOnceMile,maxDayMile,minSpeed,maxSpeed,extraStartDate,extraEndDate,memberId,…}`，req 带 runningType=1 */
    @FormUrlEncoded
    @POST("mobile/extra/extraDetailInfo.do")
    suspend fun planDetail(@FieldMap form: Map<String, String>): JsonObject

    /** 俱乐部（课号、班名、会员等级、老师） */
    @FormUrlEncoded
    @POST("mobile/index/clubInfo.do")
    suspend fun club(@FieldMap form: Map<String, String>): JsonObject

    /** 学年学期：`data{XN,XQ}`，官方标题拼成 `XN-(XN+1)-XQ` */
    @FormUrlEncoded
    @POST("common/currentTerm.do")
    suspend fun currentTerm(@FieldMap form: Map<String, String>): JsonObject

    /** 运动记录顶上那几格：`data.totalInfo{totalMile,groupMile,groupCnt,extraMile,morningExtraMile}` */
    @FormUrlEncoded
    @POST("mobile/check/getTotalInfo.do")
    suspend fun totals(@FieldMap form: Map<String, String>): JsonObject

    /** 运动记录列表：`queryType` 是页签序号（0 全部/1 自主练习/2 课外锻炼/3 团体课），`queryDate` = `yyyy-MM` */
    @FormUrlEncoded
    @POST("mobile/check/getCheckList.do")
    suspend fun records(@FieldMap form: Map<String, String>): JsonObject

    /** 单条明细：判无效那句「说明」只有这条给（列表里不下发 remark） */
    @FormUrlEncoded
    @POST("mobile/extra/getExtraCheck.do")
    suspend fun recordDetail(@FieldMap form: Map<String, String>): JsonObject

    /** 考勤维护记录：`auditState` 空=全部、0 处理中、1 同意、2 驳回 */
    @FormUrlEncoded
    @POST("mobile/check/getApplyRecordList.do")
    suspend fun appeals(@FieldMap form: Map<String, String>): JsonObject

    /** 开始一场跑步，回 `rows[0]{extraId,memberId,mixOnceMile,taskFlag,taskStartTime,taskEndTime}`；只有大三大四走这条 */
    @FormUrlEncoded
    @POST("mobile/sportApp/startRunning.do")
    suspend fun startRunning(@FieldMap form: Map<String, String>): JsonObject

    /** 服务器时间（GET，空 body）：`data.serverTime` */
    @GET("mobile/time/getServerTime.do")
    suspend fun serverTime(): ResponseBody

    /** 交这一场：`list` 是数组包一个对象、10 个键名全加密、轨迹那个值是明文（见 [SportsUploadCodec]） */
    @FormUrlEncoded
    @POST("mobile/extra/addExtraCheckNew.do")
    suspend fun submitRun(
        @Field("list") list: String,
        @Field("access_token") accessToken: String,
        @Field("_userCode") userCode: String,
        @Field("userId") userId: String,
        @Field("_userName") userName: String,
        @Field("appId") appId: String,
    ): JsonObject
}

@Module
@InstallIn(SingletonComponent::class)
object SportsModule {

    @Provides
    @Singleton
    fun provideSportsApi(@SportsRetrofit retrofit: Retrofit): SportsApi =
        retrofit.create(SportsApi::class.java)
}
