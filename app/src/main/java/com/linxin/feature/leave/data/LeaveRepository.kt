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

/** 请假（业务流转）只读数据层：我的申请 + 待办。提交/撤回是写操作，没接。 */
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
