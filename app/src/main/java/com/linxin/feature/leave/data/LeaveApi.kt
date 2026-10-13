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

    /**
     * 「待我处理」。`approvedType` 其实是它 H5 那个页面的 tab 号：待处理=1、待阅=2
     * （`activeName` 初值 "1"，`onLoad` 直接 `approvedType: this.activeName`，2026-10-13 读
     * chunk-cb78b4cc 对上的）。我们以前发的是 2，等于一直在显示抄送件而不是真要办的，已改回 1。
     */
    @FormUrlEncoded
    @POST("mobile/flowRuTask/getRunTaskByUserCode.do")
    suspend fun todoTasks(
        @Field("currentPageNo") currentPageNo: Int,
        @Field("pageSize") pageSize: Int,
        @Field("approvedType") approvedType: String = "1",
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
     * 打开一条已经提交过的请假。参数照它 H5 的原样：executionId + type + ruTaskNodeId，
     * 从「我的申请」进 type=3、从「待办」进 type=1（2026-10-13 读 ywlz 那套 chunk 的
     * `query:{type:3,executionId:...,ruTaskNodeId:...}` 对上了）。
     * 回来的 data.formJson 是表单结构、data.dataJson 是当时填的值，两个都是"字符串套 JSON"。
     */
    @FormUrlEncoded
    @POST("mobile/process/getForm.do")
    suspend fun getForm(
        @Field("executionId") executionId: String,
        @Field("type") type: String,
        @Field("ruTaskNodeId") ruTaskNodeId: String,
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveFormResponse

    /** 审批进度：一条一步。H5 那发还带了个 `_t` 时间戳防缓存，照抄 */
    @FormUrlEncoded
    @POST("mobile/flowRuTask/getApprovedInfoByExeId.do")
    suspend fun approvedInfo(
        @Field("executionId") executionId: String,
        @Field("_t") t: Long,
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveFlowResponse

    /**
     * 已办事项。参数跟「我的请求」同一个形状，多带 `timeType:1, status:3`，
     * 另外页面还有个「筛选」面板会并进来关键词和时间区间（我们这版先不带筛选）。
     */
    @FormUrlEncoded
    @POST("mobile/flowRuTask/getHiTaskByUserCode.do")
    suspend fun doneTasks(
        @Field("currentPageNo") currentPageNo: Int,
        @Field("pageSize") pageSize: Int,
        @Field("timeType") timeType: String = "1",
        @Field("status") status: String = "3",
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveListResponse

    /**
     * 服务页那张「待办事务」卡上的角标数。**一个业务参数都不带**，
     * 回的是 `data.redDot`（各栏的未读数），页面上有数就显示、没有就不显示。
     */
    @FormUrlEncoded
    @POST("mobile/flowRuTask/getYwdtCount.do")
    suspend fun todoCounts(
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveCountResponse

    /**
     * 同意。body 五个字段照它 H5 原样：`dataJson`（表单值，我们把 `getForm.do` 拿到的原文回显回去，
     * 不自己拼 —— 拼错一个键就可能把申请人填的东西改掉）、`executionId`、`ruTaskNodeId`、
     * `remark`（审批意见，可空）、`_t`（毫秒时间戳）。成功只认外层 `flag`。
     */
    @FormUrlEncoded
    @POST("mobile/process/agree.do")
    suspend fun agree(
        @Field("dataJson") dataJson: String,
        @Field("executionId") executionId: String,
        @Field("ruTaskNodeId") ruTaskNodeId: String,
        @Field("remark") remark: String,
        @Field("_t") t: Long,
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveListResponse

    /** 驳回。跟同意同一个 body 形状，打到 disagree.do */
    @FormUrlEncoded
    @POST("mobile/process/disagree.do")
    suspend fun disagree(
        @Field("dataJson") dataJson: String,
        @Field("executionId") executionId: String,
        @Field("ruTaskNodeId") ruTaskNodeId: String,
        @Field("remark") remark: String,
        @Field("_t") t: Long,
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveListResponse

    /** 删除一条流程。H5 弹的是「一经删除，该流程自动移除，请谨慎操作。」 */
    @FormUrlEncoded
    @POST("mobile/flowRuTask/deleteExecution.do")
    suspend fun deleteExecution(
        @Field("executionId") executionId: String,
        @Field("accessToken") accessToken: String,
        @Field("_userCode") userCodeUnderscore: String,
        @Field("_userType") userType: String,
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
    ): LeaveListResponse

    /** 批量已阅：ids 是 ruTaskNodeId 用逗号拼起来，approvedType 固定 "2"（待阅那一栏） */
    @FormUrlEncoded
    @POST("mobile/flowRuTask/mobileReadBulk.do")
    suspend fun mobileReadBulk(
        @Field("ids") ids: String,
        @Field("approvedType") approvedType: String = "2",
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
