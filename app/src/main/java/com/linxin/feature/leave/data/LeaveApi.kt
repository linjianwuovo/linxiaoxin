package com.linxin.feature.leave.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * 业务流转引擎（请假）`ywlz.aiit.edu.cn/zhxy-bfc/mobile/flowRuTask/`。
 *
 * 协议出处是它 H5 自己的 JS（`https://ywlz.aiit.edu.cn/mobile/`，app.js + 32 个懒加载 chunk 全扒了一遍）：
 * - 请求层的 `post()` 会把五个身份字段并进**每一个**请求 body：
 *   `accessToken` / `_userCode` / `_userType` / `userCode` / `xh`（后四个值都是学号，原样照抄）；
 * - axios 默认头是 `application/x-www-form-urlencoded;charset=utf-8`，body 走 `qs.stringify`，
 *   所以是**表单**不是 JSON；
 * - 分页对象是 `{currentPageNo, pageSize:10}`，代码里先 `currentPageNo++` 再发，所以第一页是 1；
 * - 「我的申请」= `myExecution.do`，参数 `{currentPageNo, pageSize, timeType, status:"3"}`；
 *   「待办」= `getRunTaskByUserCode.do`，参数 `{currentPageNo, pageSize, approvedType}`。
 *
 * 空身份直接探测过这个地址，服务端回 `{"data":{},"flag":false,"result":"非法访问"}`，
 * 所以外层是 `{data,flag,result,rows,total}`（注意这套不是门户那套 `code/msg`），列表在顶层 `rows`。
 *
 * 写接口（`submitForm` / `saveForm` / `withdrawExecution` / `agree` / `disagree`）一个都没接。
 */
interface LeaveApi {

    @FormUrlEncoded
    @POST("mobile/flowRuTask/myExecution.do")
    suspend fun myApplies(
        @Field("currentPageNo") currentPageNo: Int,
        @Field("pageSize") pageSize: Int,
        @Field("timeType") timeType: String = "",
        @Field("status") status: String = "3",
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveListResponse

    @FormUrlEncoded
    @POST("mobile/flowRuTask/getRunTaskByUserCode.do")
    suspend fun todoTasks(
        @Field("currentPageNo") currentPageNo: Int,
        @Field("pageSize") pageSize: Int,
        @Field("approvedType") approvedType: String = "2",
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveListResponse

    /**
     * 请假表单定义。 是一段 JSON 字符串，解开才是字段树；
     * radio/select 的选项就在各自节点的  里，不用另外查字典。
     */
    @FormUrlEncoded
    @POST("mobile/process/getFlowSheet.do")
    suspend fun getFlowSheet(
        @Field("processId") processId: String,
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveFlowSheetResponse

    /**
     * 提交。body 只有三个业务字段：（首次为空串）、（=processId）、
     * （表单值的 JSON 字符串，键是 flowSheet 里各节点的 model）。
     * 暂存是同一个 body 打到 saveForm.do，这版不做暂存。
     */
    @FormUrlEncoded
    @POST("mobile/process/submitForm.do")
    suspend fun submitForm(
        @Field("executionId") executionId: String,
        @Field("definitionId") definitionId: String,
        @Field("dataJson") dataJson: String,
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveSubmitResponse

    /** 撤回。body 只有 executionId，H5 弹的是"确定要撤回申请吗？" */
    @FormUrlEncoded
    @POST("mobile/flowRuTask/withdrawExecution.do")
    suspend fun withdraw(
        @Field("executionId") executionId: String,
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveListResponse
    /**
     * 打开表单后 H5 紧接着调这个拿默认值（标题/申请人/班级/学号/手机号/流水号都是它给的，
     * 界面上这些字段是只读预填，不让人手打）。
     */
    @FormUrlEncoded
    @POST("mobile/process/findDefinitionBase.do")
    suspend fun findDefaults(
        @Field("definitionId") definitionId: String,
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveDefaultsResponse
}
