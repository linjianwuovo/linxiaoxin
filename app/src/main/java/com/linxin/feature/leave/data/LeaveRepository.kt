package com.linxin.feature.leave.data

import com.linxin.core.auth.TokenManager
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
            if (resp.flag != true) return Result.failure(Exception(resp.result ?: "请假表单加载失败"))
            val sheet = resp.data?.flowSheet
            if (sheet.isNullOrBlank()) Result.failure(Exception("请假表单是空的")) else Result.success(sheet)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "请假表单加载失败", e))
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
            if (resp.flag != true) return Result.failure(Exception(resp.result ?: "请假提交失败"))
            Result.success(resp.data?.executionId.orEmpty())
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "请假提交失败", e))
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
            if (resp.flag != true) Result.failure(Exception(resp.result ?: "撤回失败")) else Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "撤回失败", e))
        }
    }

    private fun LeaveListResponse.toPage(page: Int, pageSize: Int): LeavePage {
        if (flag != true) {
            throw Exception(result ?: "流程列表加载失败")
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
