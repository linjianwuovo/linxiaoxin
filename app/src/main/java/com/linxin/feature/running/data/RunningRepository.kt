package com.linxin.feature.running.data

import com.linxin.core.network.netFail
import android.os.Build
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.linxin.core.auth.TokenManager
import com.linxin.core.location.CoordinateConverter
import com.linxin.feature.running.domain.ClubSummary
import com.linxin.feature.running.domain.MaintainPage
import com.linxin.feature.running.domain.RunDetail
import com.linxin.feature.running.domain.RunRecordPage
import com.linxin.feature.running.domain.RunTotals
import com.linxin.feature.running.domain.RunningDashboard
import com.linxin.feature.running.domain.RunningResult
import com.linxin.feature.running.domain.RunningSnapshot
import com.linxin.feature.running.domain.RunningStartInfo
import com.linxin.feature.running.domain.TrackPoint
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject

@ViewModelScoped
class RunningRepository @Inject constructor(
    private val api: RunningApi,
    private val tokenManager: TokenManager,
    private val encryption: RunningEncryption,
) {

    /** 官方每条查询都带 grade（"大一大二学生"这种），首页那条 checkDsStudent 拿一次就够，缓存住别每条都问 */
    private var cachedGrade: String? = null

    suspend fun getDashboard(): Result<RunningDashboard> {
        return try {
            val params = sportsAuthParams()
            coroutineScope {
                val dashboardDeferred = async { api.getStartIndex(params) }
                val typeDeferred = async { api.checkDsStudent(params) }

                val dashboardJson = dashboardDeferred.await()
                val studentTypeJson = typeDeferred.await()
                dashboardJson.requireSportsSuccess("获取跑步首页失败")
                studentTypeJson.requireSportsSuccess("获取学生类型失败")

                val studentTypeLabel = studentTypeJson.lookupString("result", "msg")
                    .ifBlank { "学生类型未识别" }
                cachedGrade = studentTypeJson.lookupString("result", "msg").ifBlank { null }

                // 大一大二并行拉课外跑步进度 + 俱乐部摘要（按接口独立降级，失败不互相牵连）
                val isJunior = SportsGradeMapper.isJuniorGrade(studentTypeLabel)
                val juniorParams = if (isJunior) params + ("grade" to studentTypeLabel) else params
                val extraDeferred = if (isJunior) async { runCatching { api.getExtraInfo(juniorParams) }.getOrNull() } else null
                val clubDeferred = if (isJunior) async { runCatching { api.getClubInfo(juniorParams) }.getOrNull() } else null
                val detailDeferred = if (isJunior) {
                    async { runCatching { api.getExtraDetailInfo(juniorParams + ("runningType" to "1")) }.getOrNull() }
                } else {
                    null
                }
                val termDeferred = async { runCatching { api.getCurrentTerm(params) }.getOrNull() }
                val extraJson = extraDeferred?.await()
                val extraDetailJson = detailDeferred?.await()
                val extraDetail = SportsGradeMapper.parseExtraDetail(extraDetailJson)
                val clubSummary = SportsGradeMapper.parseClubSummary(clubDeferred?.await())
                // 学期标题：官方那屏顶上「2026-2027-1 学期」是 currentTerm.do 拼出来的
                val termJson = termDeferred.await()
                val termYear = termJson?.let { it.lookupString("XN") }
                    .orEmpty()
                val termNo = termJson?.let { it.lookupString("XQ") }.orEmpty()
                val termTitle = if (termYear.isNotBlank()) {
                    val nextYear = (termYear.toIntOrNull() ?: 0) + 1
                    "$termYear-$nextYear-${termNo.ifBlank { "1" }}"
                } else {
                    ""
                }

                val startIndexCompletedKm = dashboardJson.lookupDouble("completedKM", "dsTaskMile")
                val startIndexTargetKm = dashboardJson.lookupDouble("dsTaskThreshold", "totalTaskThreshold")
                val startIndexLeftKm = dashboardJson.lookupDouble("leftKM")

                // 大二目标值改取服务端下发的课外跑步计划（extraInfo）；大三保持 startIndex 字段。不本地按性别/年级计算。
                val (extraCompletedKm, extraPlanKm) = SportsGradeMapper.parseExtraProgress(extraJson)
                val completedKm = when {
                    isJunior && extraPlanKm > 0.0 -> extraCompletedKm
                    isJunior && extraDetail.completedMileKm > 0.0 -> extraDetail.completedMileKm
                    else -> startIndexCompletedKm
                }
                val taskTargetKm = when {
                    isJunior && extraPlanKm > 0.0 -> extraPlanKm
                    isJunior && (extraDetail.completedMileKm > 0.0 || extraDetail.surplusMileKm > 0.0) ->
                        extraDetail.completedMileKm + extraDetail.surplusMileKm
                    startIndexTargetKm > 0.0 -> startIndexTargetKm
                    completedKm > 0.0 || startIndexLeftKm > 0.0 -> completedKm + startIndexLeftKm
                    else -> 0.0
                }
                val leftKm = when {
                    isJunior && extraDetail.surplusMileKm > 0.0 -> extraDetail.surplusMileKm
                    startIndexLeftKm > 0.0 -> startIndexLeftKm
                    else -> (taskTargetKm - completedKm).coerceAtLeast(0.0)
                }
                val startIndexTodayKm = dashboardJson.lookupDouble("todayKM", "todayMile")
                val todayKm = if (isJunior) {
                    extraDetail.todayMileKm.takeIf { it > 0.0 } ?: startIndexTodayKm
                } else {
                    startIndexTodayKm
                }
                val startIndexSingleRunKm = dashboardJson.lookupDouble("validSettingLJ", "mixOnceMile")
                val singleRunTargetKm = if (isJunior) {
                    extraDetail.mixOnceMileKm.takeIf { extraDetailJson != null && it > 0.0 } ?: startIndexSingleRunKm
                } else {
                    startIndexSingleRunKm
                }
                val startIndexMaxKm = dashboardJson.lookupDouble("maxKM")
                val maxKm = if (isJunior) extraDetail.maxMileKm.takeIf { it > 0.0 } ?: startIndexMaxKm else startIndexMaxKm
                val maxKmDate = if (isJunior) {
                    extraDetail.maxMileDate.ifBlank { dashboardJson.lookupString("maxKmDate") }
                } else {
                    dashboardJson.lookupString("maxKmDate")
                }

                Result.success(
                    RunningDashboard(
                        todayKm = todayKm,
                        completedKm = completedKm,
                        leftKm = leftKm,
                        taskTargetKm = taskTargetKm,
                        singleRunTargetKm = singleRunTargetKm,
                        maxKm = maxKm,
                        maxKmDate = maxKmDate,
                        dsFlag = dashboardJson.lookupBoolean("dsFlag", "taskFlag"),
                        studentTypeLabel = studentTypeLabel,
                        clubSummary = clubSummary,
                        maxDayKm = extraDetail.maxDayMileKm,
                        minSpeedKmh = extraDetail.minSpeedKmh,
                        maxSpeedKmh = extraDetail.maxSpeedKmh,
                        termStart = extraDetail.startDate,
                        termEnd = extraDetail.endDate,
                        todayDate = extraDetail.todayDate,
                        termTitle = termTitle,
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(netFail(e, "获取跑步首页失败", campusOnlyHost = SPORTS_HOST))
        }
    }

    suspend fun getClubDetail(): Result<List<com.linxin.feature.running.exercise.domain.ClubTask>> {
        return try {
            val params = sportsAuthParams()
            val grade = api.checkDsStudent(params).lookupString("result", "msg")
            val clubJson = api.getClubDetail(params + ("grade" to grade))
            clubJson.requireSportsSuccess("获取俱乐部详情失败")
            Result.success(ClubDetailMapper.parseTasks(clubJson))
        } catch (e: Exception) {
            Result.failure(netFail(e, "获取俱乐部详情失败", campusOnlyHost = SPORTS_HOST))
        }
    }

    suspend fun currentStudentCode(): String = tokenManager.getUserCode().orEmpty()

    /** 轮询锻炼考勤打卡结果。返回 true 表示打卡成功（hasResult=YES）。 */
    suspend fun pollQrcodeResult(timestamp: Long): Result<Boolean> {
        return try {
            val studentCode = tokenManager.getUserCode().orEmpty()
            val json = api.getQrcodeResult(
                studentCode = studentCode.toPlainBody(),
                timestamp = timestamp.toString().toPlainBody(),
            )
            val hasResult = json.get("data")
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("hasResult")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            Result.success(hasResult.equals("YES", ignoreCase = true))
        } catch (e: Exception) {
            Result.failure(netFail(e, "查询打卡结果失败", campusOnlyHost = SPORTS_HOST))
        }
    }

    private fun String.toPlainBody(): okhttp3.RequestBody =
        okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), this)

    suspend fun startRunning(extraId: String? = null): Result<RunningStartInfo> {
        return try {
            val params = sportsAuthParams()
            val grade = api.checkDsStudent(params).lookupString("result", "msg")
            // 大一大二无 startRunning.do：会话标识来自 extraInfo.do(extraId) + extraDetailInfo.do(memberId/mixOnceMile)
            if (SportsGradeMapper.isJuniorGrade(grade)) {
                return startJuniorRunning(params + ("grade" to grade))
            }
            val startParams = params.toMutableMap().apply {
                if (!extraId.isNullOrBlank()) {
                    put("extraId", extraId)
                }
            }
            val response = api.startRunning(startParams)
            response.requireSportsSuccess("开始跑步失败")
            val exerciseId = response.lookupString("extraId")
            if (exerciseId.isBlank()) {
                return Result.failure(
                    Exception(
                        response.lookupString("result", "msg")
                            .ifBlank { "开始跑步失败" }
                    )
                )
            }

            Result.success(
                RunningStartInfo(
                    exerciseId = exerciseId,
                    memberId = response.lookupString("memberId"),
                    mixOnceMileKm = response.lookupDouble("mixOnceMile").takeIf { it > 0.0 } ?: 1.0,
                    validSettingLj = response.lookupString("validSettingLJ"),
                    taskFlag = response.lookupString("taskFlag"),
                )
            )
        } catch (e: Exception) {
            Result.failure(netFail(e, "开始跑步失败", campusOnlyHost = SPORTS_HOST))
        }
    }

    /** 大一大二课外跑步会话：extraId 取自 extraInfo.do，memberId/mixOnceMile 取自 extraDetailInfo.do。 */
    private suspend fun startJuniorRunning(juniorParams: Map<String, String>): Result<RunningStartInfo> {
        val extraJson = api.getExtraInfo(juniorParams)
        val exerciseId = SportsGradeMapper.parseExtraId(extraJson)
        if (exerciseId.isBlank()) {
            return Result.failure(Exception("未获取到课外跑步任务，无法开始跑步"))
        }
        val extraDetail = SportsGradeMapper.parseExtraDetail(
            api.getExtraDetailInfo(juniorParams + ("runningType" to "1"))
        )
        return Result.success(
            RunningStartInfo(
                exerciseId = exerciseId,
                memberId = extraDetail.memberId,
                mixOnceMileKm = extraDetail.mixOnceMileKm,
            )
        )
    }

    suspend fun uploadTrackedRun(snapshot: RunningSnapshot): Result<RunningResult> =
        uploadSnapshot(snapshot)

    private suspend fun uploadSnapshot(snapshot: RunningSnapshot): Result<RunningResult> {
        return try {
            val serverTime = parseServerTime(api.getServerTime().string())
            val distanceKm = snapshot.distanceMeters / 1000.0
            val durationSeconds = snapshot.durationSeconds.coerceAtLeast(1L)
            val speedKmh = distanceKm / durationSeconds * 3600.0
            val route = if (snapshot.pointsAreBd09) {
                snapshot.points
            } else {
                snapshot.points.map { point ->
                    val bdPoint = CoordinateConverter.wgs84ToBd09(point.latitude, point.longitude)
                    TrackPoint(
                        latitude = bdPoint.latitude,
                        longitude = bdPoint.longitude,
                        timestampMillis = point.timestampMillis,
                    )
                }
            }

            val payload = RunningUploadPayload(
                exerciseId = snapshot.startInfo.exerciseId,
                memberId = snapshot.startInfo.memberId,
                runningType = snapshot.startInfo.runningType,
                startDate = formatDateTime(snapshot.startTimeMillis),
                endDate = serverTime,
                mileKm = distanceKm,
                timeSeconds = durationSeconds,
                speedKmh = speedKmh,
                runningRoute = route,
                sourceInfo = Build.MODEL ?: "Android",
            )

            val encryptedList = encryption.encryptUploadPayload(payload)
            val response = api.uploadRunningRecord(encryptedList, uploadAuthParams())
            val resultCode = response.lookupString("result")
            val uploadId = response.lookupString("id")
            val success = resultCode.equals("OK", ignoreCase = true) && uploadId.isValidUploadId()
            val message = when {
                success -> "上传成功"
                resultCode.equals("OK", ignoreCase = true) ->
                    "服务器未生成有效跑步记录（id=${uploadId.ifBlank { "空" }}），本次里程可能未入账"
                else -> response.lookupString("msg", "result").ifBlank { "上传失败" }
            }

            if (!success) {
                return Result.failure(Exception(message))
            }

            Result.success(
                RunningResult(
                    success = true,
                    message = message,
                    uploadId = uploadId,
                    startDate = payload.startDate,
                    endDate = payload.endDate,
                    distanceKm = distanceKm,
                    durationSeconds = durationSeconds,
                    speedKmh = speedKmh,
                    pointCount = route.size,
                )
            )
        } catch (e: Exception) {
            Result.failure(netFail(e, "上传跑步记录失败", campusOnlyHost = SPORTS_HOST))
        }
    }

    /**
     * 运动记录列表。`queryType` 是**页签序号**不是状态枚举：0 全部 / 1 自主练习 / 2 课外锻炼 / 3 团体课，
     * 官方那一屏连着发 0~3 四次就是这个原因。`queryDate` 形如 `2026-10`。
     */
    suspend fun runRecords(
        queryDate: String,
        queryType: Int,
        pageNum: Int = 1,
        pageSize: Int = 20,
    ): Result<RunRecordPage> = try {
        val json = api.getCheckList(
            gradeParams() + linkedMapOf(
                "pageNum" to pageNum.toString(),
                "pageSize" to pageSize.toString(),
                "queryDate" to queryDate,
                "queryType" to queryType.toString(),
            )
        )
        json.requireSportsSuccess("获取运动记录失败")
        Result.success(RunningRecordMapper.records(json))
    } catch (e: Exception) {
        Result.failure(netFail(e, "获取运动记录失败", campusOnlyHost = SPORTS_HOST))
    }

    /** 列表顶上那几格汇总 */
    suspend fun runTotals(): Result<RunTotals> = try {
        val json = api.getTotalInfo(gradeParams())
        json.requireSportsSuccess("获取运动汇总失败")
        Result.success(RunningRecordMapper.totals(json))
    } catch (e: Exception) {
        Result.failure(netFail(e, "获取运动汇总失败", campusOnlyHost = SPORTS_HOST))
    }

    /** 单条明细：判无效的那句「说明」只有这条给 */
    suspend fun runDetail(checkId: String): Result<RunDetail> = try {
        val json = api.getExtraCheck(gradeParams() + ("checkId" to checkId))
        json.requireSportsSuccess("获取运动明细失败")
        Result.success(RunningRecordMapper.detail(json, checkId))
    } catch (e: Exception) {
        Result.failure(netFail(e, "获取运动明细失败", campusOnlyHost = SPORTS_HOST))
    }

    /** 考勤维护记录：`auditState` 空串=全部、0 处理中、1 同意、2 驳回 */
    suspend fun maintainRecords(
        queryDate: String,
        queryType: Int,
        auditState: String,
        pageNum: Int = 1,
        pageSize: Int = 20,
    ): Result<MaintainPage> = try {
        val json = api.getApplyRecordList(
            gradeParams() + linkedMapOf(
                "pageNum" to pageNum.toString(),
                "pageSize" to pageSize.toString(),
                "queryDate" to queryDate,
                "queryType" to queryType.toString(),
                "auditState" to auditState,
            )
        )
        json.requireSportsSuccess("获取维护记录失败")
        Result.success(RunningRecordMapper.maintain(json, queryType))
    } catch (e: Exception) {
        Result.failure(netFail(e, "获取维护记录失败", campusOnlyHost = SPORTS_HOST))
    }

    /**
     * 官方原生上传那条带的五个表单字段（`upLoadSport` 里逐条 add 出来的）。
     * appId 是它自己写死的一串 1，不是设备号也不是密钥，照抄。
     */
    private suspend fun uploadAuthParams(): Map<String, String> {
        val accessToken = tokenManager.getAccessToken().orEmpty()
        val userCode = tokenManager.getUserCode().orEmpty()
        val userName = tokenManager.getUserName().orEmpty()
        return linkedMapOf(
            "access_token" to accessToken,
            "_userCode" to userCode,
            "userId" to userCode,
            "_userName" to userName,
            "appId" to SPORTS_APP_ID,
        )
    }

    /** 查询类接口：公共参数 + 年级（官方每条都带 grade，我们缓存一下首页那条的返回值） */
    private suspend fun gradeParams(): Map<String, String> {
        val base = sportsAuthParams()
        val grade = cachedGrade ?: runCatching {
            api.checkDsStudent(base).lookupString("result", "msg")
                .takeIf { it.isNotBlank() }
                .also { cachedGrade = it }
        }.getOrNull()
        return if (grade.isNullOrBlank()) base else base + ("grade" to grade)
    }

    private suspend fun sportsAuthParams(): Map<String, String> {
        val accessToken = tokenManager.getAccessToken().orEmpty()
        val userCode = tokenManager.getUserCode().orEmpty()
        val userName = tokenManager.getUserName().orEmpty()
        val userType = tokenManager.getUserType().orEmpty().ifBlank { "1" }

        if (userCode.isBlank() || accessToken.isBlank()) {
            throw IllegalStateException("登录信息已失效，请重新登录")
        }

        return linkedMapOf(
            "studentCode" to userCode,
            "accessToken" to accessToken,
            "access_token" to accessToken,
            "userCode" to userCode,
            "_userCode" to userCode,
            "xh" to userCode,
            "userName" to userName,
            "_userType[0]" to userType,
            "_userType[1]" to userType,
        )
    }

    private fun parseServerTime(raw: String): String {
        val text = raw.trim().removeSurrounding("\"")
        if (text.isBlank()) return formatDateTime(System.currentTimeMillis())
        if (!text.startsWith("{")) return text.takeIf(::isValidDateTimeText) ?: formatDateTime(System.currentTimeMillis())

        val json = com.google.gson.JsonParser.parseString(text).asJsonObject
        val candidate = json.lookupString("serverTime", "data", "result")
        return candidate.takeIf(::isValidDateTimeText) ?: formatDateTime(System.currentTimeMillis())
    }

    private fun formatDateTime(timeMillis: Long): String {
        val instant = Instant.ofEpochMilli(timeMillis)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return localDateTime.format(FORMATTER)
    }

    private fun JsonObject.lookupString(vararg keys: String): String {
        keys.forEach { key ->
            val value = getAsPlainString(lookupElement(key))
            if (value.isNotBlank()) {
                return value
            }
        }
        return ""
    }

    private fun JsonObject.requireSportsSuccess(fallback: String) {
        val flagElement = get("flag")
        if (flagElement != null && !flagElement.isJsonNull) {
            val success = when {
                flagElement.isJsonPrimitive && flagElement.asJsonPrimitive.isBoolean -> flagElement.asBoolean
                else -> flagElement.asString.equals("true", ignoreCase = true)
            }
            if (!success) {
                throw IllegalStateException(
                    lookupString("result", "msg")
                        .ifBlank { fallback }
                )
            }
        }
    }

    private fun JsonObject.lookupBoolean(vararg keys: String): Boolean {
        keys.forEach { key ->
            val element = lookupElement(key)
            if (element != null && !element.isJsonNull) {
                return when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isBoolean -> element.asBoolean
                    else -> element.asString.equals("true", ignoreCase = true)
                }
            }
        }
        return false
    }

    private fun JsonObject.lookupDouble(vararg keys: String): Double =
        lookupString(*keys).toDoubleOrNull() ?: 0.0

    private fun JsonObject.lookupElement(key: String): JsonElement? {
        get(key)?.takeUnless { it.isJsonNull }?.let { return it }
        get("data")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get(key)
            ?.takeUnless { it.isJsonNull }
            ?.let { return it }
        firstRowObject()
            ?.get(key)
            ?.takeUnless { it.isJsonNull }
            ?.let { return it }
        return null
    }

    private fun JsonObject.firstRowObject(): JsonObject? {
        val rows = get("rows") ?: return null
        if (!rows.isJsonArray || rows.asJsonArray.size() == 0) {
            return null
        }
        val first = rows.asJsonArray[0]
        return if (first.isJsonObject) first.asJsonObject else null
    }

    private fun getAsPlainString(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        return when {
            element.isJsonPrimitive -> element.asString.orEmpty()
            element.isJsonObject -> element.asJsonObject.lookupString("id")
            else -> element.toString()
        }
    }

    private fun isValidDateTimeText(value: String): Boolean {
        return try {
            LocalDateTime.parse(value, FORMATTER)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    private fun String.isValidUploadId(): Boolean =
        isNotBlank() && this != "0" && !equals("null", ignoreCase = true)

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    }
}

/** 体育那台机只有校内网能到，报错时要把它点名 */
private const val SPORTS_HOST = "sports.aiit.edu.cn:8082"

/** 官方原生上传里写死的那个 appId（`upLoadSport` 里就是 32 个 1，不是设备号） */
private const val SPORTS_APP_ID = "11111111111111111111111111111111"
