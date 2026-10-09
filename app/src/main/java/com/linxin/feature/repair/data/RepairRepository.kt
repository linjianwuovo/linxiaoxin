package com.linxin.feature.repair.data

import com.linxin.core.auth.TokenManager
import com.linxin.core.network.RepairRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit

/**
 * 报修只读数据层。写接口（`saveOrUpdateBxdForSjd` / `updateBxdByIdForSjd` / `addLcbzForSjd`）
 * 故意不接：会动到真实工单，要他单独点头。
 */
@Singleton
class RepairRepository @Inject constructor(
    private val api: RepairApi,
    private val tokenManager: TokenManager,
) {

    private suspend fun identity(): Quad {
        val c = tokenManager.snapshot()
        return Quad(
            accessToken = c.accessToken.orEmpty(),
            xh = c.userCode.orEmpty(),
            userName = c.userName.orEmpty(),
            userType = c.userType ?: "1",
        )
    }

    private data class Quad(
        val accessToken: String,
        val xh: String,
        val userName: String,
        val userType: String,
    )

    suspend fun myRepairs(): Result<List<RepairItem>> {
        return try {
            val me = identity()
            val resp = api.getMyRepairs(me.accessToken, me.xh, me.xh, me.userName, me.userType)
            if (resp.flag != true) {
                return Result.failure(Exception(resp.msg ?: resp.code ?: "获取报修单失败"))
            }
            val rows = resp.data?.data.orEmpty()
            Result.success(
                rows.mapNotNull { row ->
                    val bxdh = row.bxdh ?: return@mapNotNull null
                    RepairItem(
                        bxdh = bxdh,
                        type = row.bxlx.orEmpty().trim(),
                        place = row.bxdd.orEmpty().trim(),
                        time = row.bxsj.orEmpty().trim(),
                        status = row.dqztMc.orEmpty().trim(),
                        statusCode = row.dqzt.orEmpty().trim(),
                        description = row.gzms.orEmpty().trim(),
                        worker = row.wxyXm.orEmpty().trim(),
                        workerPhone = row.wxySjh.orEmpty().trim(),
                    )
                },
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "获取报修单失败", e))
        }
    }

    suspend fun detail(bxdh: String): Result<RepairInfo> {
        return try {
            val me = identity()
            val resp = api.getRepairDetail(bxdh, me.accessToken, me.xh, me.xh, me.userName, me.userType)
            if (resp.flag != true) {
                return Result.failure(Exception(resp.msg ?: resp.code ?: "获取报修详情失败"))
            }
            val d = resp.data?.data
            if (d == null) {
                Result.failure(Exception("没有这张报修单"))
            } else {
                Result.success(
                    RepairInfo(
                        bxdh = d.bxdh.orEmpty(),
                        type = d.bxlx.orEmpty().trim(),
                        place = listOf(d.lymc.orEmpty(), d.qyfjh.orEmpty()).filter { it.isNotBlank() }.joinToString("，"),
                        time = d.bxsj.orEmpty().trim(),
                        status = (d.dqztmc ?: d.dqzt).orEmpty().trim(),
                        description = d.gzms.orEmpty().trim(),
                        expectFrom = d.kssj.orEmpty().trim(),
                        expectTo = d.jssj.orEmpty().trim(),
                        serviceSource = d.fwly.orEmpty().trim(),
                        evaluation = d.fwpj.orEmpty().trim(),
                        finishNote = d.wcqk.orEmpty().trim(),
                        repairNote = d.wxlcms.orEmpty().trim(),
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "获取报修详情失败", e))
        }
    }

    /**
     * 流程时间线。抓包里节点还能再嵌一层 `list`（派单给多个维修员那种），
     * 这里拍平成一个按原顺序的列表，不自己排序——服务端给的就是时序。
     */
    suspend fun flow(bxdh: String): Result<List<RepairStep>> {
        return try {
            val me = identity()
            val resp = api.getRepairFlow(bxdh, me.accessToken, me.xh, me.xh, me.userName, me.userType)
            if (resp.flag != true) {
                return Result.failure(Exception(resp.msg ?: resp.code ?: "获取处理进度失败"))
            }
            val nodes = resp.data?.data?.list.orEmpty()
            val flat = ArrayList<RepairStep>()
            fun walk(list: List<RepairFlowNode?>) {
                list.filterNotNull().forEach { node ->
                    val text = node.clms.orEmpty().trim()
                    if (text.isNotEmpty()) flat += RepairStep(time = node.sj.orEmpty().trim(), text = text)
                    node.list?.let { walk(it) }
                }
            }
            walk(nodes)
            Result.success(flat)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "获取处理进度失败", e))
        }
    }
}

/** 列表项，界面用 */
data class RepairItem(
    val bxdh: String,
    val type: String,
    val place: String,
    val time: String,
    val status: String,
    val statusCode: String,
    val description: String,
    val worker: String,
    val workerPhone: String,
)

/** 详情，界面用 */
data class RepairInfo(
    val bxdh: String,
    val type: String,
    val place: String,
    val time: String,
    val status: String,
    val description: String,
    val expectFrom: String,
    val expectTo: String,
    val serviceSource: String,
    val evaluation: String,
    val finishNote: String,
    val repairNote: String,
)

data class RepairStep(
    val time: String,
    val text: String,
)

@Module
@InstallIn(SingletonComponent::class)
object RepairModule {

    @Provides
    @Singleton
    fun provideRepairApi(@RepairRetrofit retrofit: Retrofit): RepairApi =
        retrofit.create(RepairApi::class.java)
}
