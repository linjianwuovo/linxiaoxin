package com.linxin.feature.leave.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
            resp.toPage(page, pageSize, LeaveItem.DETAIL_TYPE_APPLY)
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
            resp.toPage(page, pageSize, LeaveItem.DETAIL_TYPE_TODO)
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

    private fun LeaveListResponse.toPage(page: Int, pageSize: Int, formType: String): LeavePage {
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
                ruTaskNodeId = row.ruTaskNodeId.orEmpty().trim(),
                formType = formType,
            )
        }
        val totalRecords = total ?: items.size
        return LeavePage(
            items = items,
            hasMore = page * pageSize < totalRecords,
            total = total,
        )
    }

    /**
     * 一条请假的详情：表单填了什么 + 审批走到哪一步。两条口一起发、互不牵连，
     * 一条挂了另一条照样显示，两条都没回才算失败。
     *
     * 值是 `dataJson`（键 = flowSheet 里各节点的 model），标题和选项文案在 `formJson` 的节点树上，
     * 所以要先按树的顺序把 model→{label, options} 收下来，再把值翻译成给人看的文字
     * （radio/select 存的是 value，不翻就显示个 "1"）。树里没出现的键原样当标签，不猜。
     */
    suspend fun formDetail(executionId: String, type: String, ruTaskNodeId: String): Result<LeaveDetail> {
        return try {
            val me = identity()
            val (formResult, flowResult) = coroutineScope {
                val form = async {
                    api.getForm(
                        executionId,
                        type,
                        ruTaskNodeId,
                        me.accessToken,
                        me.userCode,
                        me.userType,
                        me.userCode,
                        me.userCode,
                    )
                }
                val flow = async {
                    api.approvedInfo(
                        executionId,
                        System.currentTimeMillis(),
                        me.accessToken,
                        me.userCode,
                        me.userType,
                        me.userCode,
                        me.userCode,
                    )
                }
                form.await() to flow.await()
            }

            val data = formResult.data.asObjectOrNull()
            val fields = leaveDetailFields(
                formJson = data?.optStringOrNull("formJson"),
                dataJson = data?.optStringOrNull("dataJson"),
            )
            val steps = flowResult.rows.orEmpty().map { row ->
                LeaveFlowStep(
                    who = row.optStringOrEmpty("principalName").ifBlank { row.optStringOrEmpty("userName") },
                    action = row.optStringOrEmpty("approvedTypeMsg").ifBlank { row.optStringOrEmpty("operType") },
                    status = row.optStringOrEmpty("statusMsg").ifBlank { row.optStringOrEmpty("status") },
                    time = row.optStringOrEmpty("complateTime").ifBlank { row.optStringOrEmpty("receiveTime") },
                    remark = row.optStringOrEmpty("remark"),
                )
            }

            if (fields.isEmpty() && steps.isEmpty() && formResult.flag != true) {
                return Result.failure(
                    Exception(failureReason(formResult.result, fallback = "这条请假的详情没取到，服务端也没给原因")),
                )
            }
            Result.success(
                LeaveDetail(
                    fields = fields,
                    steps = steps,
                    // 结构那一发挂了不代表没填东西，界面据此说"没读到"而不是"没填"
                    fieldsUnavailable = formResult.flag != true,
                    stepsUnavailable = flowResult.flag != true,
                )
            )
        } catch (e: Exception) {
            Result.failure(netFail(e, "这条请假的详情没取到"))
        }
    }
}

/** 表单结构里的一个可填项：显示名 + 选项值到文案的映射（radio/select 存的是值） */
private data class LeaveFieldMeta(val label: String, val options: Map<String, String>)

/** 一条请假详情：填了什么 + 审批走到哪 */
data class LeaveDetail(
    val fields: List<LeaveField>,
    val steps: List<LeaveFlowStep>,
    val fieldsUnavailable: Boolean,
    val stepsUnavailable: Boolean,
)

data class LeaveField(val label: String, val value: String)

/** 审批进度的一步。字段名是它 H5 模板里直接读的那几个 */
data class LeaveFlowStep(
    val who: String,
    val action: String,
    val status: String,
    val time: String,
    val remark: String,
)

private fun JsonElement?.asObjectOrNull(): JsonObject? {
    val node = this ?: return null
    return if (node.isJsonObject) node.asJsonObject else null
}

private const val LEAVE_FORM_LABEL_LIMIT = 60

/**
 * formJson / dataJson 都是"字符串套 JSON"，解不开就回空表，不抛。
 * 值在 dataJson 里按 model 存，radio/select 存的是选项 value，所以要用结构里的选项表翻成文案；
 * 顺序按表单树的顺序出，树里没有的键再补在后面（键当标签，不猜它是什么）。
 * 这里刻意用 Gson 不用 org.json：后者在单元测试里是 android.jar 的桩，一调就 RuntimeException("Stub!")。
 */
internal fun leaveDetailFields(formJson: String?, dataJson: String?): List<LeaveField> {
    val value = leaveParseObject(dataJson) ?: return emptyList()
    val meta = LinkedHashMap<String, LeaveFieldMeta>()
    leaveParseObject(formJson)?.let { leaveWalkFormTree(it, meta) }

    val ordered = mutableListOf<LeaveField>()
    meta.forEach { (model, info) ->
        if (!value.has(model)) return@forEach
        val text = leaveRenderValue(value.get(model), info.options)
        if (text.isNotBlank()) ordered += LeaveField(info.label.ifBlank { model }, text)
    }
    value.entrySet().forEach { (key, element) ->
        if (meta.containsKey(key) || key.startsWith("_")) return@forEach
        val text = leaveRenderValue(element, null)
        if (text.isNotBlank()) ordered += LeaveField(key, text)
    }
    return ordered
}

private fun leaveParseObject(raw: String?): JsonObject? = raw?.takeIf { it.isNotBlank() }
    ?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
    ?.takeIf { it.isJsonObject }
    ?.asJsonObject

private fun leaveWalkFormTree(node: JsonObject, out: LinkedHashMap<String, LeaveFieldMeta>) {
    val model = node.optStringOrEmpty("model")
    if (model.isNotBlank() && !out.containsKey(model)) {
        val options = LinkedHashMap<String, String>()
        node.get("properties").asObjectOrNull()
            ?.get("options").asArrayOrNull()
            ?.forEach { option ->
                val obj = option.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val value = obj.optStringOrEmpty("value")
                if (value.isNotBlank()) options[value] = obj.optStringOrEmpty("label")
            }
        out[model] = LeaveFieldMeta(node.optStringOrEmpty("label").ifBlank { model }, options)
    }
    node.get("list").asArrayOrNull()?.forEach { child ->
        child.takeIf { it.isJsonObject }?.let { leaveWalkFormTree(it.asJsonObject, out) }
    }
    node.get("columns").asArrayOrNull()?.forEach { child ->
        child.takeIf { it.isJsonObject }?.let { leaveWalkFormTree(it.asJsonObject, out) }
    }
}

private fun leaveRenderValue(node: JsonElement?, options: Map<String, String>?): String = when {
    node == null || node.isJsonNull -> ""
    node.isJsonArray -> node.asJsonArray
        .map { leaveRenderValue(it, options) }
        .filter { it.isNotBlank() }
        .joinToString(" ~ ")
    node.isJsonObject -> node.toString().take(LEAVE_FORM_LABEL_LIMIT)
    else -> {
        val primitive = node.asJsonPrimitive
        val text = if (primitive.isNumber) {
            runCatching {
                val d = primitive.asDouble
                if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
            }.getOrDefault(runCatching { primitive.asString }.getOrDefault(""))
        } else {
            runCatching { primitive.asString }.getOrDefault("")
        }.trim()
        options?.get(text)?.takeIf { it.isNotBlank() } ?: text
    }
}

private fun JsonElement?.asArrayOrNull(): JsonArray? =
    this?.takeIf { it.isJsonArray }?.asJsonArray

private fun com.google.gson.JsonObject.optStringOrEmpty(key: String): String =
    get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }.orEmpty().trim()

private fun com.google.gson.JsonObject?.optStringOrNull(key: String): String? =
    this?.get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }

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
