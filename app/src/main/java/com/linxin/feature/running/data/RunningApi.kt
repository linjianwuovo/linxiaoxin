package com.linxin.feature.running.data

import com.google.gson.JsonObject
import com.linxin.core.network.SportsRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ResponseBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import javax.inject.Singleton

interface RunningApi {

    @FormUrlEncoded
    @POST("mobile/sportApp/startIndex.do")
    suspend fun getStartIndex(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/sportApp/checkDsStudent.do")
    suspend fun checkDsStudent(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/index/clubInfo.do")
    suspend fun getClubInfo(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/index/extraInfo.do")
    suspend fun getExtraInfo(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/extra/extraDetailInfo.do")
    suspend fun getExtraDetailInfo(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/auto/clubInfo.do")
    suspend fun getClubDetail(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @Multipart
    @POST("mobile/qrcode/getQrcodeResult.do")
    suspend fun getQrcodeResult(
        @Part("studentCode") studentCode: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
    ): JsonObject

    /** 学年学期：`{"data":{"XN":"2026","XQ":"1"}}`，官方那一屏的标题由它拼 */
    @FormUrlEncoded
    @POST("common/currentTerm.do")
    suspend fun getCurrentTerm(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/sportApp/startRunning.do")
    suspend fun startRunning(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    /** 运动记录列表：queryType 是页签序号（0 全部 / 1 自主练习 / 2 课外锻炼 / 3 团体课），不是状态枚举 */
    @FormUrlEncoded
    @POST("mobile/check/getCheckList.do")
    suspend fun getCheckList(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    /** 列表顶上那几格汇总（累计里程、团体课里程、次数、晨跑里程） */
    @FormUrlEncoded
    @POST("mobile/check/getTotalInfo.do")
    suspend fun getTotalInfo(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    /** 单条跑步明细：判无效的「说明」那条 remark 只在这条里给，列表里不下发 */
    @FormUrlEncoded
    @POST("mobile/extra/getExtraCheck.do")
    suspend fun getExtraCheck(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    /** 考勤维护（申诉）记录：auditState 空=全部、0 处理中、1 同意、2 驳回 */
    @FormUrlEncoded
    @POST("mobile/check/getApplyRecordList.do")
    suspend fun getApplyRecordList(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @GET("mobile/time/getServerTime.do")
    suspend fun getServerTime(): ResponseBody

    @FormUrlEncoded
    @POST("mobile/extra/addExtraCheckNew.do")
    suspend fun uploadRunningRecord(
        @Field("list") list: String,
        @FieldMap params: Map<String, String>,
    ): JsonObject
}

@Module
@InstallIn(SingletonComponent::class)
object RunningModule {

    @Provides
    @Singleton
    fun provideRunningApi(@SportsRetrofit retrofit: Retrofit): RunningApi =
        retrofit.create(RunningApi::class.java)
}
