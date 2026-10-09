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
 * 报修数据层。
 *
 * 读：列表 / 详情 / 流程时间线 / 表单用的地点树、类型树、受理人。
 * 写：新建报修、取消申请、确认并评价、打开详情时静默标已读。
 * 字段全部照它 H5 的调用点抄（见 RepairApi 的注释），不猜。
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
                        id = row.id.orEmpty(),
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
                        id = d.bxdId.orEmpty(),
                        bxdh = d.bxdh.orEmpty(),
                        type = d.bxlx.orEmpty().trim(),
                        place = listOf(d.lymc.orEmpty(), d.qyfjh.orEmpty())
                            .filter { it.isNotBlank() }.joinToString("，"),
                        time = d.bxsj.orEmpty().trim(),
                        status = (d.dqztmc ?: d.dqzt).orEmpty().trim(),
                        statusCode = d.dqzt.orEmpty().trim(),
                        description = d.gzms.orEmpty().trim(),
                        expectFrom = d.kssj.orEmpty().trim(),
                        expectTo = d.jssj.orEmpty().trim(),
                        serviceSource = d.fwly.orEmpty().trim(),
                        evaluation = d.fwpj.orEmpty().trim(),
                        finishNote = d.wcqk.orEmpty().trim(),
                        repairNote = d.wxlcms.orEmpty().trim(),
                        workerLogin = d.wxry.orEmpty().trim(),
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

    // ─── 写操作 ───

    /** 新建表单的初始数据：地点树 + 类型树 + 预填的手机号/姓名 */
    suspend fun formInit(): Result<RepairFormInit> {
        return try {
            val me = identity()
            val places = api.getPlaces(me.accessToken, me.xh, me.xh, me.userName, me.userType)
            val types = api.getTypes(me.accessToken, me.xh, me.xh, me.userName, me.userType)
            val contact = api.getContact(me.accessToken, me.xh, me.xh, me.userName, me.userType)
            if (places.flag != true || types.flag != true) {
                return Result.failure(Exception(places.msg ?: types.msg ?: "报修表单数据加载失败"))
            }
            Result.success(
                RepairFormInit(
                    places = places.data?.data.orEmpty().mapNotNull { n ->
                        if (n.value == null) null else RepairNode(n.name.orEmpty(), n.parent.orEmpty(), n.value)
                    },
                    types = types.data?.data.orEmpty().mapNotNull { n ->
                        if (n.value == null) null else RepairNode(n.name.orEmpty(), n.parent.orEmpty(), n.value)
                    },
                    phone = contact.data?.sjh.orEmpty(),
                    personName = contact.data?.xm.orEmpty().ifBlank { me.userName },
                ),
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "报修表单数据加载失败", e))
        }
    }

    suspend fun submit(draft: RepairDraft): Result<Unit> {
        return try {
            val me = identity()
            val resp = api.submitRepair(
                xqdm = draft.placeLevel1,
                lyId = draft.placeLevel2,
                qyfjh = draft.placeLevel3,
                xxdz = draft.address,
                sjh = draft.phone,
                gzms = draft.description,
                xm = draft.personName,
                userCode = me.xh,
                tpId = "",
                bxlx = draft.typeValue,
                accessToken = me.accessToken,
                xh = me.xh,
                userName = me.userName,
                userType = me.userType,
            )
            if (resp.flag != true) Result.failure(Exception(resp.msg ?: resp.code ?: "报修提交失败"))
            else Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "报修提交失败", e))
        }
    }

    /** 取消申请：H5 从详情里拿 wxry 原样回传 */
    suspend fun cancel(bxdh: String, workerLogin: String): Result<Unit> {
        return try {
            val me = identity()
            val resp = api.cancelRepair(
                bxdh = bxdh,
                cz = 8,
                wxry = workerLogin,
                userCode = me.xh,
                accessToken = me.accessToken,
                xh = me.xh,
                userName = me.userName,
                userType = me.userType,
            )
            if (resp.flag != true) Result.failure(Exception(resp.msg ?: resp.code ?: "取消失败"))
            else Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "取消失败", e))
        }
    }

    /** 确认并评价：wcqk 1=已完成 0=未完成；fwpj 1..4，选未完成时 H5 也照发空串 */
    suspend fun evaluate(bxdId: String, done: Boolean, score: Int?, feedback: String): Result<Unit> {
        return try {
            val me = identity()
            val resp = api.evaluateRepair(
                id = bxdId,
                wcqk = if (done) 1 else 0,
                fwpj = if (done && score != null) score.toString() else "",
                yjfk = feedback,
                userCode = me.xh,
                accessToken = me.accessToken,
                xh = me.xh,
                userName = me.userName,
                userType = me.userType,
            )
            if (resp.flag != true) Result.failure(Exception(resp.msg ?: resp.code ?: "评价提交失败"))
            else Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "评价提交失败", e))
        }
    }

    /** 打开详情时 H5 会静默把未读标掉，这里照做；失败不影响详情显示 */
    suspend fun ackRead(id: String) {
        if (id.isBlank()) return
        runCatching {
            val me = identity()
            api.markRead(
                id = id,
                qtYqbz = 1,
                accessToken = me.accessToken,
                xh = me.xh,
                userCode = me.xh,
                userName = me.userName,
                userType = me.userType,
            )
        }
    }
}

/** 列表项，界面用 */
data class RepairItem(
    val id: String,
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
    val id: String,
    val bxdh: String,
    val type: String,
    val place: String,
    val time: String,
    val status: String,
    val statusCode: String,
    val description: String,
    val expectFrom: String,
    val expectTo: String,
    val serviceSource: String,
    val evaluation: String,
    val finishNote: String,
    val repairNote: String,
    val workerLogin: String,
)

data class RepairStep(
    val time: String,
    val text: String,
)

data class RepairNode(
    val name: String,
    val parent: String,
    val value: String,
)

data class RepairFormInit(
    val places: List<RepairNode>,
    val types: List<RepairNode>,
    val phone: String,
    val personName: String,
)

data class RepairDraft(
    val placeLevel1: String,
    val placeLevel2: String,
    val placeLevel3: String,
    val typeValue: String,
    val address: String,
    val phone: String,
    val description: String,
    val personName: String,
)

@Module
@InstallIn(SingletonComponent::class)
object RepairModule {

    @Provides
    @Singleton
    fun provideRepairApi(@RepairRetrofit retrofit: Retrofit): RepairApi =
        retrofit.create(RepairApi::class.java)
}
