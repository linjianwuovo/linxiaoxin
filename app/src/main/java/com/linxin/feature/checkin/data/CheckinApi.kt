package com.linxin.feature.checkin.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * 查寝签到接口 (fdygl.aiit.edu.cn)
 * 所有接口使用 JSON body，认证通过 AuthInterceptor 注入 Bearer token header。
 */
interface CheckinApi {

    /** 查询签到任务列表 */
    @POST("app/dorm/pageStudentSignIn")
    suspend fun pageStudentSignIn(
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): SignInPageResponse

    /** 获取任务详情 */
    @POST("app/dorm/getTaskInfoByDateId")
    suspend fun getTaskInfoByDateId(
        @Body body: Map<String, String>,
    ): TaskInfoResponse

    /** 提交签到 */
    @POST("app/dorm/signIn")
    suspend fun signIn(
        @Body body: Map<String, String>,
    ): SignInSubmitResponse

    /** 区间内每天的签到任务数（月历用） */
    @POST("app/dorm/listDateCheck")
    suspend fun listDateCheck(
        @Body body: Map<String, String>,
    ): DateCheckResponse

    /** 本月签到统计（已签/未签/进行中/请假/离校/扫码） */
    @POST("app/dorm/collectionStudentStatics")
    suspend fun collectionStudentStatics(
        @Body body: Map<String, String>,
    ): StudentStaticsResponse

    /**
     * 主题签到任务列表。与 dorm 那套并行，响应结构完全相同，
     * 区别只在 taskMajorType：3=查寝、2=主题签到。
     */
    @POST("app/signinSubject/pageStudentSignIn")
    suspend fun pageSubjectSignIn(
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): SignInPageResponse
}

/**
 * 文件上传接口（独立接口，使用 Multipart）
 */
interface FileUploadApi {

    @Multipart
    @POST("app/file/uploadFileToFastdfs")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
    ): FileUploadResponse
}
