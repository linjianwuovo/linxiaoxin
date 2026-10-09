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
