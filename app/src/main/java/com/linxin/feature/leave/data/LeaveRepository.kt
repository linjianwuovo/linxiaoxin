package com.linxin.feature.leave.data

import com.linxin.core.auth.TokenManager
import com.linxin.core.network.failureReason
import com.linxin.core.network.netFail
import com.linxin.core.network.LeaveRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit

/** 请假（业务流转）数据层：我的申请 / 待办 / 表单定义 / 提交 / 撤回。 */
@Singleton
class LeaveRepository @Inject constructor(
    private val api: LeaveApi,
    private val tokenManager: TokenManager,
) {

    private suspend fun identity(): Quint {
        val c = tokenManager.snapshot()
        val xh = c.userCode.orEmpty()
        return Quint(
            accessToken = c.accessToken.orEmpty(),
            userCode = xh,
            userType = c.userType ?: "1",
        )
    }

    private data class Quint(
        val accessToken: String,
        val userCode: String,
        val userType: String,
    )

    suspend fun myApplies(page: Int = 1, pageSize: Int = 10): Result<LeavePage> {
        val me = identity()
        return runCatching {
            val resp = api.myApplies(
                currentPageNo = page,
                pageSize = pageSize,
                accessToken = me.accessToken,
                userCodeUnderscore = me.userCode,
                userType = me.userType,
                userCode = me.userCode,
                xh = me.userCode,
            )
            resp.toPage(page, pageSize)
        }
    }

    suspend fun todoTasks(page: Int = 1, pageSize: Int = 10): Result<LeavePage> {
        val me = identity()
        return runCatching {
            val resp = api.todoTasks(
                currentPageNo = page,
                pageSize = pageSize,
                accessToken = me.accessToken,
                userCodeUnderscore = me.userCode,
                userType = me.userType,
                userCode = me.userCode,
                xh = me.userCode,
            )
            resp.toPage(page, pageSize)
        }
    }

    /** 请假表单定义：flowSheet 是 JSON 字符串，原样交给界面用 org.json 解 */
    suspend fun flowSheet(processId: String): Result<String> {
        return try {
            val me = identity()
            val resp = api.getFlowSheet(
                processId,
                me.accessToken,
                me.userCode,
                me.userType,
                me.userCode,
                me.userCode,
            )
            if (resp.flag != true) return Result.failure(Exception(failureReason(resp.result, fallback = "请假表单没取到，服务端也没给原因")))
            val sheet = resp.data?.flowSheet
            if (sheet.isNullOrBlank()) Result.failure(Exception("请假表单是空的")) else Result.success(sheet)
        } catch (e: Exception) {
            Result.failure(netFail(e, "请假表单加载失败"))
        }
    }

    /** 提交请假。dataJson 由界面按 flowSheet 的键拼好传进来 */
    suspend fun submit(processId: String, dataJson: String): Result<String> {
        return try {
            val me = identity()
            val resp = api.submitForm(
                "",
                processId,
                dataJson,
                me.accessToken,
                me.userCode,
                me.userType,
                me.userCode,
                me.userCode,
            )
            if (resp.flag != true) return Result.failure(Exception(failureReason(resp.result, fallback = "请假提交失败，服务端没给原因")))
            Result.success(resp.data?.executionId.orEmpty())
        } catch (e: Exception) {
            Result.failure(netFail(e, "请假提交失败"))
        }
    }

    suspend fun withdraw(executionId: String): Result<Unit> {
        return try {
            val me = identity()
            val resp = api.withdraw(
                executionId,
                me.accessToken,
                me.userCode,
                me.userType,
                me.userCode,
                me.userCode,
            )
            if (resp.flag != true) Result.failure(Exception(failureReason(resp.result, fallback = "撤回失败，服务端没给原因"))) else Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(netFail(e, "撤回失败"))
        }
    }

    /**
     * 表单默认值（标题/申请人/班级/学号/手机号/流水号）。
     * H5 是先拿 flowSheet、再打这个接口做只读预填，所以界面里这些字段不让手打。
     * 服务端偶尔给数字（比如流水号），Gson 到 `Map<String, Any?>` 会变成 `Double`，
     * 这里统一转字符串并把整数值的小数尾巴去掉，免得显示成 `123.0`。
     */
    suspend fun defaults(processId: String): Result<Map<String, String>> {
        return try {
            val me = identity()
            val resp = api.findDefaults(
                processId,
                me.accessToken,
                me.userCode,
                me.userType,
                me.userCode,
                me.userCode,
            )
            if (resp.flag != true) {
                return Result.failure(Exception(failureReason(resp.result, fallback = "表单默认值没取到")))
            }
            Result.success(resp.data.orEmpty().mapValues { (_, v) -> v.asText() })
        } catch (e: Exception) {
            Result.failure(netFail(e, "表单默认值没取到"))
        }
    }

    private fun Any?.asText(): String = when (this) {
        null -> ""
        is Number -> toDouble().let { d ->
            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        }
        is Boolean -> toString()
        else -> toString().trim()
    }

    private fun LeaveListResponse.toPage(page: Int, pageSize: Int): LeavePage {
        if (flag != true) {
            throw Exception(failureReason(result, fallback = "流程列表没取到，服务端也没给原因"))
        }
        val merged = rows.orEmpty() + (data?.axxDoneList.orEmpty())
        val items = merged.mapNotNull { row ->
            val id = row.executionId ?: row.ruTaskNodeId ?: return@mapNotNull null
            LeaveItem(
                id = id,
                title = row.flowTitle.orEmpty().trim(),
                status = (row.statusTxt ?: row.status).orEmpty().trim(),
                time = (row.receiveTime ?: row.createDate).orEmpty().trim(),
                withdrawable = row.isWithdraw == "1",
            )
        }
        val totalRecords = total ?: items.size
        return LeavePage(
            items = items,
            hasMore = page * pageSize < totalRecords,
            total = total,
        )
    }
}

data class LeavePage(
    val items: List<LeaveItem>,
    val hasMore: Boolean,
    val total: Int?,
)

@Module
@InstallIn(SingletonComponent::class)
object LeaveModule {

    @Provides
    @Singleton
    fun provideLeaveApi(@LeaveRetrofit retrofit: Retrofit): LeaveApi =
        retrofit.create(LeaveApi::class.java)
}
