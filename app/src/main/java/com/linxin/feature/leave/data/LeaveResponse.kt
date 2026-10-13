package com.linxin.feature.leave.data

/**
 * 流程引擎响应外层：`{data, flag, result, rows, total}`，列表在顶层 `rows`。
 * 失败时 `flag=false`，`result` 就是给人看的中文原因（探测过：`"非法访问"`）。
 */
data class LeaveListResponse(
    val flag: Boolean?,
    val result: String?,
    val total: Int?,
    val rows: List<LeaveRow>?,
    val data: LeaveData?,
)

/** `getHiTaskByUserCode.do` 会把「安小信自己的已办」塞在 `data.axxDoneList` 里，另外两个接口给的是 `{}` */
data class LeaveData(
    val axxDoneList: List<LeaveRow>?,
)

/**
 * 一行流程。这里只声明**有出处**的字段：
 * `flowTitle` / `status` / `statusTxt` / `createDate` / `receiveTime` / `executionId` / `ruTaskNodeId` /
 * `userName` / `userCode` / `msgType` 都是它页面模板里直接读的属性。
 * 没在 JS 里出现过的键一律不猜（Gson 会自动忽略，不影响解析）。
 * `userName` / `userCode` 是身份字段，接进来但不显示，见仓库红线。
 */
data class LeaveRow(
    val executionId: String?,
    val ruTaskNodeId: String?,
    val flowTitle: String?,
    val status: String?,
    val statusTxt: String?,
    val createDate: String?,
    val receiveTime: String?,
    val msgType: String?,
    /** "1" 时列表行才显示撤回按钮（H5 的判据） */
    val isWithdraw: String?,
    val userName: String?,
    val userCode: String?,
)

/** 界面用的一行请假 */
data class LeaveItem(
    val id: String,
    val title: String,
    val status: String,
    val time: String,
    val withdrawable: Boolean = false,
    /** 详情页那一发 getForm.do 要这个节点号，列表行里本来就带着，别丢 */
    val ruTaskNodeId: String = "",
    /** 学校 H5 从「我的申请」进详情传 type=3、从「待办」进传 type=1，我们照抄 */
    val formType: String = DETAIL_TYPE_APPLY,
) {
    companion object {
        const val DETAIL_TYPE_APPLY = "3"
        const val DETAIL_TYPE_TODO = "1"
    }
}

/**
 * `process/getForm.do` 的外层。`data` 里是 `formJson`（表单结构，JSON 字符串套 JSON）
 * 和 `dataJson`（填过的值，同样套一层字符串），服务端不想要这条时会给 `{}` 或 null，
 * 所以这里收 JsonElement 现场解，不声明成具体类 —— 声明死了 Gson 会先抛英文那句。
 */
data class LeaveFormResponse(
    val flag: Boolean?,
    val result: String?,
    val data: com.google.gson.JsonElement?,
)

/** `flowRuTask/getApprovedInfoByExeId.do`：审批进度，一条一步，字段名是从它 H5 模板里读出来的 */
data class LeaveFlowResponse(
    val flag: Boolean?,
    val result: String?,
    val rows: List<com.google.gson.JsonObject>?,
)

/**
 * `flowRuTask/getYwdtCount.do` 的外层。H5 读的是 `data.redDot`，里面每个键是某一栏的未读数，
 * 键名一个样本都没读到过（那台机上四个数全是 0），所以收 JsonElement 再按候选键取，
 * 取不到就不显示数字 —— 不猜一个键名当它一定在。
 */
data class LeaveCountResponse(
    val flag: Boolean?,
    val result: String?,
    val data: com.google.gson.JsonElement?,
)

/** getFlowSheet.do 的外层。flowSheet 是一段 JSON 字符串，用 org.json 现场解，不建死 DTO */
data class LeaveFlowSheetResponse(
    val flag: Boolean?,
    val result: String?,
    val data: LeaveFlowSheetData?,
)

data class LeaveFlowSheetData(
    val flowSheet: String?,
    val specialType: String?,
)

/** submitForm.do 的响应：data.executionId 要存下来（驳回重提时用） */
data class LeaveSubmitResponse(
    val flag: Boolean?,
    val result: String?,
    val data: LeaveSubmitData?,
)

data class LeaveSubmitData(
    val executionId: String?,
)

/**
 * findDefinitionBase.do：表单默认值，键就是 flowSheet 里的 model。
 * 值不一定是字符串（流水号那类服务端给的是数字），所以这里收 `Any?`，
 * 由 `LeaveRepository.defaults()` 统一转文本 —— 直接声明 Map<String,String> 会被 Gson 顶回来。
 */
data class LeaveDefaultsResponse(
    val flag: Boolean?,
    val result: String?,
    val data: Map<String, Any?>?,
)
