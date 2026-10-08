package com.linxin.feature.checkin.data

import com.linxin.core.auth.TokenManager
import com.linxin.core.network.CheckinRetrofit
import com.linxin.feature.checkin.domain.CheckinDay
import com.linxin.feature.checkin.domain.CheckinTask
import com.linxin.feature.checkin.domain.MonthStatics
import com.linxin.feature.checkin.domain.TaskDetail
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.gson.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckinRepository @Inject constructor(
    private val api: CheckinApi,
    private val fileApi: FileUploadApi,
    private val tokenManager: TokenManager,
) {
    suspend fun getTasks(page: Int, pageSize: Int = 10): Result<List<CheckinTask>> {
        return try {
            val body = mapOf<String, Any>(
                "pageNum" to page,
                "pageSize" to pageSize,
                "type" to "1",
                "taskMajorType" to "3",
            )
            val response = api.pageStudentSignIn(body)
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取签到任务失败"))
            }
            Result.success(response.toTasks())
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取签到任务", e), e))
        }
    }

    /** 主题签到（taskMajorType=2），与查寝并行的一套任务 */
    suspend fun getSubjectTasks(page: Int, pageSize: Int = 10): Result<List<CheckinTask>> {
        return try {
            val body = mapOf<String, Any>(
                "pageNum" to page,
                "pageSize" to pageSize,
                "type" to "1",
                "taskMajorType" to "2",
            )
            val response = api.pageSubjectSignIn(body)
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取主题签到任务失败"))
            }
            Result.success(response.toTasks())
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取主题签到", e), e))
        }
    }

    /**
     * 本月签到统计。staticDate 形如 2026-09。
     * createBy 是学号，从 TokenManager 取，绝不写死。
     */
    suspend fun getMonthStatics(staticDate: String): Result<MonthStatics> {
        return try {
            val userCode = tokenManager.getUserCode().orEmpty()
            if (userCode.isBlank()) {
                return Result.failure(Exception("学号信息缺失，请重新登录"))
            }
            val response = api.collectionStudentStatics(
                mapOf("staticDate" to staticDate, "createBy" to userCode, "taskMajorType" to "3"),
            )
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取签到统计失败"))
            }
            val d = response.data
                // data 为 null 时全渲染成 0，会把"没拿到"伪装成"这个月一次没签"，
                // 而月历明显有任务——所以这里当失败处理，让页面出重试条。
                ?: return Result.failure(Exception("签到统计接口没有返回数据"))
            Result.success(
                MonthStatics(
                    signed = d?.signinedNum ?: 0,
                    notSigned = d?.notSigninedNum ?: 0,
                    inProgress = d?.signiningNum ?: 0,
                    leave = d?.leaveNum ?: 0,
                    offCampus = d?.leaveSchoolNum ?: 0,
                    qrCode = d?.qrCodeNum ?: 0,
                ),
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取签到统计", e), e))
        }
    }

    /** 区间内每天的签到任务数，用于月历标记 */
    suspend fun getDateCheck(startTime: String, endTime: String): Result<List<CheckinDay>> {
        return try {
            val response = api.listDateCheck(mapOf("startTime" to startTime, "endTime" to endTime))
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取签到日历失败"))
            }
            Result.success(
                response.data.orEmpty().map { CheckinDay(date = it.taskDate.orEmpty(), taskNum = it.taskNum ?: 0) }
                    .filter { it.date.isNotBlank() },
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取签到日历", e), e))
        }
    }

    private fun SignInPageResponse.toTasks(): List<CheckinTask> =
        (rows ?: data?.list).orEmpty().map { row ->
            CheckinTask(
                id = row.id ?: "",
                taskName = row.title ?: row.taskName ?: "",
                taskDateId = row.taskDateId ?: row.id ?: row.userTaskId ?: row.taskId ?: "",
                isSigned = row.executionedStatus == "1" || row.signinStatus == "1",
                startTime = row.timedStartTime ?: row.startTime ?: row.collectionStartTime ?: "",
                endTime = row.timedEndTime ?: row.endTime ?: row.collectionEndTime ?: "",
                statusText = row.executionedStatusTxt.orEmpty(),
                taskDate = row.taskDate.orEmpty().trim(),
            )
        }

    // 与 dorm 一致：code 只代表"请求被处理"，业务成败看 flag
    private fun DateCheckResponse.isSuccess(): Boolean = (code == "0" || code == "200") && flag != false

    private fun StudentStaticsResponse.isSuccess(): Boolean = (code == "0" || code == "200") && flag != false

    suspend fun getTaskDetail(dateId: String): Result<TaskDetail> {
        return try {
            val body = mapOf("dateId" to dateId)
            val response = api.getTaskInfoByDateId(body)
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取任务详情失败"))
            }
            val data = response.data
                ?: return Result.failure(Exception("获取任务详情失败"))
            val firstLocation = data.signinLocations?.firstOrNull()
            val (centerLng, centerLat) = firstLocation?.lngLat
                ?.split(",")
                ?.let { parts ->
                    val lng = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                    val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    lng to lat
                } ?: (data.lng?.toDoubleOrNull() ?: 0.0) to (data.lat?.toDoubleOrNull() ?: 0.0)

            Result.success(
                TaskDetail(
                    taskName = data.title ?: data.taskName ?: "",
                    taskDateId = data.dateId ?: data.taskDateId ?: dateId,
                    startTime = data.timedStartTime ?: data.startTime ?: data.collectionStartTime ?: "",
                    endTime = data.timedEndTime ?: data.endTime ?: data.collectionEndTime ?: "",
                    needPhoto = data.photoRequire == "1" || data.signinPhoto == "1" || data.needPhoto == "1",
                    isSigned = data.signStatus == "1" || data.signinStatus == "1",
                    signinPlace = firstLocation?.signinLocation ?: data.signinPlace ?: "",
                    locationRange = firstLocation?.content?.toString()?.toDoubleOrNull()
                        ?: data.locationRange?.toDoubleOrNull()
                        ?: 0.0,
                    centerLng = centerLng,
                    centerLat = centerLat,
                    address = firstLocation?.signinLocation ?: data.address ?: "",
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取任务详情", e), e))
        }
    }

    suspend fun uploadPhoto(file: File): Result<String> {
        return try {
            val requestBody = file.asRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            val response = fileApi.uploadFile(part)

            val url = response.data.extractUploadUrl()
            if (url.isNullOrBlank()) {
                Result.failure(Exception(response.msg ?: "照片上传失败"))
            } else {
                Result.success(url)
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("上传照片", e), e))
        }
    }

    suspend fun submitSignIn(
        taskDateId: String,
        photoUrl: String,
        place: String,
        lngLatString: String,   // "经度,纬度" BD-09格式
    ): Result<Unit> {
        return try {
            val body = mapOf(
                "taskDateId" to taskDateId,
                "signinPhoto" to photoUrl,
                "signinPlace" to place,
                "latLng" to lngLatString,
                "outSignin" to "0",
                "outSigninDesc" to "",
            )
            val response = api.signIn(body)
            if ((response.code == "200" || response.code == "0") && response.flag != false) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.msg ?: "签到失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("提交签到", e), e))
        }
    }

    private fun mapError(action: String, error: Exception): String {
        return when (error) {
            is HttpException -> when {
                error.code() == 401 -> "登录已失效，请重新登录"
                error.code() >= 500 -> "${action}接口暂时异常，请稍后重试"
                else -> "${action}失败（HTTP ${error.code()}）"
            }
            is IOException -> "网络异常，请检查连接后重试"
            else -> error.message ?: "${action}失败"
        }
    }

    // code=="0" 只代表"请求被处理"，业务成败看 flag。鉴权失败回的是
    // code:"0" + flag:false + msg:"非法访问"，不读 flag 就会把失败渲染成空列表、
    // 甚至把提交失败报成"签到成功"。
    private fun SignInPageResponse.isSuccess(): Boolean = (code == "0" || code == "200") && flag != false

    private fun TaskInfoResponse.isSuccess(): Boolean = (code == "0" || code == "200") && flag != false

    private fun JsonElement?.extractUploadUrl(): String? {
        val element = this ?: return null
        if (element.isJsonNull) return null
        if (element.isJsonPrimitive) return element.asString
        if (!element.isJsonObject) return null

        val obj = element.asJsonObject
        val commonKeys = listOf("url", "fileUrl", "fullPath", "path", "filePath")
        commonKeys.forEach { key ->
            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }

        obj.entrySet().forEach { (_, value) ->
            if (value.isJsonPrimitive) {
                val text = value.asString
                if (text.startsWith("http://") || text.startsWith("https://") || text.contains("/group")) {
                    return text
                }
            }
        }

        return null
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CheckinModule {

    @Provides
    @Singleton
    fun provideCheckinApi(@CheckinRetrofit retrofit: Retrofit): CheckinApi =
        retrofit.create(CheckinApi::class.java)

    @Provides
    @Singleton
    fun provideFileUploadApi(@CheckinRetrofit retrofit: Retrofit): FileUploadApi =
        retrofit.create(FileUploadApi::class.java)
}
