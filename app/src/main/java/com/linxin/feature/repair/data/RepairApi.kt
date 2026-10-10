package com.linxin.feature.repair.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * 报修平台 `repair.aiit.edu.cn/bxjlSjd/`，与安小信「报修申请」那个 H5 同源。
 *
 * 协议不是猜的，两条证据：① 2026-10-09 抓包（见 BX-PROTOCOL / BX-PROTOCOL-DEEP 脱敏结论）；
 * ② 直接读它 H5 的 JS（`https://repair.aiit.edu.cn/dist-app/static/js/…`）——
 * 所有 `*ForSjd.do` 都走同一个 `post()` 助手：`qs.stringify(merge(业务参数, 全局 userInfo))`，
 * 也就是**表单编码**，不是 JSON（它另外有个 `postJson` 才带 `application/json`，报修接口一个都没用）。
 * 全局 userInfo 就是 `accessToken` / `userName` / `xh` / `userType` 这四个，每个请求都并进 body，
 * 所以这里四个字段在每个方法上都显式声明，不靠 AuthInterceptor 注入（那个分支已在
 * `AuthInterceptor` 里对 repair 域名跳过，免得把门户那套 `access_token`/`_userCode`/`appId` 多塞进去）。
 *
 * 抓包里 `getWdbxListForSjd.do` 除身份四件套外没有任何业务参数，一次回全部报修单，所以没有分页字段。
 * 另外 JS 里还有三个抓包没出现的接口，等做申请时再用：`getBxxzForSjd.do`（报修须知）、
 * `getXqByXhForSjd.do`（按学号取信息）、`saveOrUpdateBxdForSjd.do`（**写**，提交报修）。
 */
interface RepairApi {

    /** 我的报修单列表 */
    @FormUrlEncoded
    @POST("bxjlSjd/getWdbxListForSjd.do")
    suspend fun getMyRepairs(
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userCode") userCode: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairListResponse

    /** 报修单详情，键是报修单号 bxdh */
    @FormUrlEncoded
    @POST("bxjlSjd/getBxdXqForSjd.do")
    suspend fun getRepairDetail(
        @Field("bxdh") bxdh: String,
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userCode") userCode: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairDetailResponse

    /** 报修单的流程时间线 */
    @FormUrlEncoded
    @POST("bxjlSjd/getLctByBxdhForSjd.do")
    suspend fun getRepairFlow(
        @Field("bxdh") bxdh: String,
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userCode") userCode: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairFlowResponse

    /** 报修地点树，99 个节点 {name,parent,value}，value 是校区/楼栋/层的编码 */
    @FormUrlEncoded
    @POST("bxjlSjd/getBxddForSjd.do")
    suspend fun getPlaces(
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userCode") userCode: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairTreeResponse

    /** 报修类型树，66 个节点，提交时 bxlx 取选中的最深一级 value */
    @FormUrlEncoded
    @POST("bxjlSjd/getBxlxForSjd.do")
    suspend fun getTypes(
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userCode") userCode: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairTreeResponse

    /** 受理人信息 {sjh, xm}：表单里手机号和姓名的预填值 */
    @FormUrlEncoded
    @POST("bxjlSjd/getSjhForSjd.do")
    suspend fun getContact(
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userCode") userCode: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairContactResponse

    /**
     * 新建报修单。字段照它 H5 `submitClick` 里那个对象原样：
     * `xqdm/lyId/qyfjh`（地点三级 value）、`xxdz`（详细地址 ≤50）、`sjh`（/^1\d{10}$/）、
     * `gzms`（≤200）、`xm`、`userCode`、`tpId`（上传图 id 逗号串，不传图就是空串）、
     * `bxlx`（类型最深一级 value）。H5 还会带 `loginType`，但安小信打开页面时 URL 没给这个参数，
     * store 里是 undefined，qs 会丢掉，所以这里也不发。
     */
    @FormUrlEncoded
    @POST("bxjlSjd/saveOrUpdateBxdForSjd.do")
    suspend fun submitRepair(
        @Field("xqdm") xqdm: String,
        @Field("lyId") lyId: String,
        @Field("qyfjh") qyfjh: String,
        @Field("xxdz") xxdz: String,
        @Field("sjh") sjh: String,
        @Field("gzms") gzms: String,
        @Field("xm") xm: String,
        @Field("userCode") userCode: String,
        @Field("tpId") tpId: String,
        @Field("bxlx") bxlx: String,
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairWriteResponse

    /**
     * 取消申请。H5 的确认弹窗标题就是"取消申请"，成功提示"已成功取消！"，
     * body 是 `{bxdh, cz:8, wxry: 详情的 wxry, userCode: 详情的 wxyDlm}`（userCode 随后被身份合并覆盖）。
     * 只有 `dqzt == "1"`（未接单）时才显示这个按钮。
     */
    @FormUrlEncoded
    @POST("bxjlSjd/addLcbzForSjd.do")
    suspend fun cancelRepair(
        @Field("bxdh") bxdh: String,
        @Field("cz") cz: Int,
        @Field("wxry") wxry: String,
        @Field("userCode") userCode: String,
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairWriteResponse

    /**
     * 确认并评价。`id` 传详情里的 `bxdId`（不是 bxdh）；`wcqk` 1=已完成 0=未完成；
     * `fwpj` 1..4 = 非常满意/满意/一般/不满意，选"未完成"时 H5 也照发空串；
     * `yjfk` 意见反馈 ≤50，`wcqk==0` 时必填。
     */
    @FormUrlEncoded
    @POST("bxjlSjd/updateBxdByIdForSjd.do")
    suspend fun evaluateRepair(
        @Field("id") id: String,
        @Field("wcqk") wcqk: Int,
        @Field("fwpj") fwpj: String,
        @Field("yjfk") yjfk: String,
        @Field("userCode") userCode: String,
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairWriteResponse

    /**
     * 打开列表某一行时 H5 会静默发 `{id, qtYqbz:1}`，把"未读提醒"标掉。
     * 它不是取消报修（取消走 addLcbzForSjd cz=8）。
     */
    @FormUrlEncoded
    @POST("bxjlSjd/updateBxdByIdForSjd.do")
    suspend fun markRead(
        @Field("id") id: String,
        @Field("qtYqbz") qtYqbz: Int,
        @Field("accessToken") accessToken: String,
        @Field("xh") xh: String,
        @Field("userCode") userCode: String,
        @Field("userName") userName: String,
        @Field("userType") userType: String,
    ): RepairWriteResponse
}
