package com.linxin.feature.sports.data

import android.os.Build
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.linxin.core.auth.TokenManager
import com.linxin.core.network.netFail
import com.linxin.feature.sports.domain.RecordDetail
import com.linxin.feature.sports.domain.RecordPage
import com.linxin.feature.sports.domain.RecordTotals
import com.linxin.feature.sports.domain.RunRecordDraft
import com.linxin.feature.sports.domain.RunRules
import com.linxin.feature.sports.domain.RunSession
import dagger.hilt.android.scopes.ViewModelScoped
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * 跑步这条链唯一的数据入口。
 *
 * 只有读和"交这一场"两件事；交出去之后服务器判有效还是无效，我们只能从
 * `getCheckList` 的 `exerciseState` 和明细的 `remark` 里看，别处猜不到。
 */
@ViewModelScoped
class SportsRepository @Inject constructor(
    private val api: SportsApi,
    private val tokenManager: TokenManager,
) {

    /** 年级只问一次就缓存住，官方每条查询都带 grade */
    private var gradeCache: String? = null

    private suspend fun form(): Map<String, String> {
        val auth = tokenManager.snapshot()
        val userCode = auth.userCode.orEmpty()
        val token = auth.accessToken.orEmpty()
        val userType = auth.userType?.takeIf { it.isNotBlank() } ?: "1"
        val map = linkedMapOf(
            "studentCode" to userCode,
            "accessToken" to token,
            "access_token" to token,
            "userCode" to userCode,
            "_userCode" to userCode,
            "xh" to userCode,
            "userName" to auth.userName.orEmpty(),
            "_userType[0]" to userType,
            "_userType[1]" to userType,
        )
        gradeCache?.let { map["grade"] = it }
        return map
    }

    /** 约束那一屏要的东西：年级 + 计划 + 明细 + 学期，四条并行，谁挂了谁留空 */
    suspend fun rules(): Result<RunRules> {
        val base = runCatching { form() }.getOrElse {
            return Result.failure(netFail(it, "登录信息读不出来"))
        }
        return try {
            val gradeJson = api.studentGrade(base)
            SportsParse.requireFlag(gradeJson, "识别年级失败")
            val grade = SportsParse.gradeOf(gradeJson)
            gradeCache = grade
            val withGrade = base + ("grade" to grade)

            val planJson = runCatching { api.plan(withGrade) }.getOrNull()
            val detailJson = runCatching {
                api.planDetail(withGrade + ("runningType" to "1"))
            }.getOrNull()
            val termJson = runCatching { api.currentTerm(withGrade) }.getOrNull()

            Result.success(SportsParse.rules(grade, planJson, detailJson, termJson))
        } catch (e: Exception) {
            Result.failure(netFail(e, "读跑步任务失败", campusOnlyHost = SPORTS_HOST))
        }
    }

    /**
     * 开场。大一大二**没有** `startRunning.do`：会话标识 `extraId` 来自 `extraInfo.do`，
     * `memberId` 来自 `extraDetailInfo.do`（这是抓包对出来的，不是猜的）。
     */
    suspend fun startSession(rules: RunRules): Result<RunSession> {
        val base = runCatching { form() }.getOrElse { return Result.failure(it) }
        return try {
            if (rules.isJunior || rules.extraId.isNotBlank()) {
                val extraId = rules.extraId.ifBlank {
                    SportsParse.extraIdOf(runCatching { api.plan(base) }.getOrNull())
                }
                if (extraId.isBlank()) {
                    return Result.failure(Exception("没有进行中的课外跑步任务，开不了场"))
                }
                Result.success(
                    RunSession(
                        exerciseId = extraId,
                        memberId = rules.memberId,
                        runningType = "1",
                    )
                )
            } else {
                val json = api.startRunning(base + ("extraId" to rules.extraId))
                SportsParse.requireFlag(json, "开始跑步失败")
                val exerciseId = SportsParse.startIdOf(json)
                if (exerciseId.isBlank()) {
                    return Result.failure(Exception(SportsParse.messageOf(json).ifBlank { "开始跑步失败" }))
                }
                Result.success(
                    RunSession(
                        exerciseId = exerciseId,
                        memberId = SportsParse.startMemberOf(json).ifBlank { rules.memberId },
                        runningType = "1",
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(netFail(e, "开始跑步失败", campusOnlyHost = SPORTS_HOST))
        }
    }

    /**
     * 交这一场。`endDate` 用服务器时间 —— 官方就是先 `getServerTime.do` 再拼包，
     * 拿本机时间会被判时间对不上。
     */
    suspend fun submit(draft: RunRecordDraft): Result<String> {
        val auth = tokenManager.snapshot()
        return try {
            val endText = serverTimeText()
            val list = SportsUploadCodec.buildListField(
                SportsUploadCodec.Run(
                    exerciseId = draft.session.exerciseId,
                    memberId = draft.session.memberId,
                    runningType = draft.session.runningType,
                    startText = draft.startText,
                    endText = endText.ifBlank { draft.endText },
                    mileKm = draft.mileKm,
                    timeSeconds = draft.timeSeconds,
                    speedKmh = draft.speedKmh,
                    trackWgs = draft.points.map { it.latitude to it.longitude },
                    deviceModel = Build.MODEL.orEmpty(),
                )
            )
            val json = api.submitRun(
                list = list,
                accessToken = auth.accessToken.orEmpty(),
                userCode = auth.userCode.orEmpty(),
                userId = auth.userCode.orEmpty(),
                userName = auth.userName.orEmpty(),
                appId = SportsUploadCodec.APP_ID,
            )
            val id = SportsParse.uploadedIdOf(json)
            if (!json.flagOk() || id.isBlank() || id == "0") {
                return Result.failure(
                    Exception(
                        SportsParse.messageOf(json).ifBlank {
                            "服务器收了但没落库（id=${id.ifBlank { "空" }}），这一场可能不计"
                        }
                    )
                )
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(netFail(e, "提交跑步记录失败", campusOnlyHost = SPORTS_HOST))
        }
    }

    suspend fun records(queryDate: String, queryType: Int, pageNum: Int = 1): Result<RecordPage> =
        call("获取运动记录失败") {
            SportsParse.records(
                api.records(
                    form() + linkedMapOf(
                        "pageNum" to pageNum.toString(),
                        "pageSize" to PAGE_SIZE.toString(),
                        "queryDate" to queryDate,
                        "queryType" to queryType.toString(),
                    )
                )
            )
        }

    suspend fun totals(): Result<RecordTotals> =
        call("获取运动汇总失败") { SportsParse.totals(api.totals(form())) }

    suspend fun detail(checkId: String): Result<RecordDetail> =
        call("获取运动明细失败") { SportsParse.detail(api.recordDetail(form() + ("checkId" to checkId)), checkId) }

    suspend fun appeals(
        queryDate: String,
        queryType: Int,
        auditState: String,
        pageNum: Int = 1,
    ): Result<com.linxin.feature.sports.domain.AppealPage> =
        call("获取维护记录失败") {
            SportsParse.appeals(
                api.appeals(
                    form() + linkedMapOf(
                        "pageNum" to pageNum.toString(),
                        "pageSize" to PAGE_SIZE.toString(),
                        "queryDate" to queryDate,
                        "queryType" to queryType.toString(),
                        "auditState" to auditState,
                    )
                )
            )
        }

    private suspend fun <T> call(fallback: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(netFail(e, fallback, campusOnlyHost = SPORTS_HOST))
    }

    /** 官方那套 `yyyy-MM-dd HH:mm:ss` */
    fun dateTimeText(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(FORMATTER)

    private suspend fun serverTimeText(): String = try {
        val raw = api.serverTime().string().trim().removeSurrounding("\"")
        if (raw.startsWith("{")) {
            val json = JsonParser.parseString(raw).asJsonObject
            SportsParse.textOf(json, "serverTime")
        } else {
            raw
        }.takeIf { it.isNotBlank() }.orEmpty()
    } catch (_: Exception) {
        ""
    }

    companion object {
        const val PAGE_SIZE = 20
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    }
}

/** 体育那台机只有校内网能到 */
private const val SPORTS_HOST = "sports.aiit.edu.cn:8082"

private fun JsonObject.flagOk(): Boolean {
    val flag = get("flag") ?: return true
    return when {
        flag.isJsonNull -> true
        flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean -> flag.asBoolean
        else -> flag.asString.equals("true", ignoreCase = true)
    }
}
