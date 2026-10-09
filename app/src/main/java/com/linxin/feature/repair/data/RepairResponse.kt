package com.linxin.feature.repair.data

/**
 * 报修平台的响应外层：`{code, data, flag, msg, rows, total}`，列表/对象都塞在 `data.data` 里。
 * 字段名照抓包原样，一个都不改。
 */
// 抓包里 rows 是数组不是数字（和请假那套信封同名但类型不同），界面不用就干脆不声明，Gson 会忽略
data class RepairListResponse(
    val flag: Boolean?,
    val code: String?,
    val msg: String?,
    val data: RepairListShell?,
)

data class RepairListShell(
    val data: List<RepairRow>?,
)

/**
 * `getWdbxListForSjd.do` 一条报修单，抓包 28 个键。
 *
 * 注意：`xm`（报修人姓名）和 `xh` 这类身份字段**故意不接**，界面上也不显示，见仓库红线；
 * 未声明的键 Gson 会直接忽略，所以不影响解析。
 */
data class RepairRow(
    val id: String?,
    /** 报修单号 */
    val bxdh: String?,
    /** 报修时间 */
    val bxsj: String?,
    /** 报修类型，一级,二级 拼好的串 */
    val bxlx: String?,
    /** 报修地点全串 */
    val bxdd: String?,
    val yiji: String?,
    val erji: String?,
    /** 楼栋 */
    val lymc: String?,
    /** 区域/楼层 */
    val qyfjh: String?,
    /** 故障描述 */
    val gzms: String?,
    /** 当前状态码 + 中文名 */
    val dqzt: String?,
    val dqztMc: String?,
    /** 期望完成 / 期望时间 */
    val kssj: String?,
    val jssj: String?,
    val yqbz: String?,
    val cjwc: String?,
    /** 维修员姓名 / 手机号 / 登录名（第三方信息，只在详情里按需显示） */
    val wxyXm: String?,
    val wxySjh: String?,
    val wxyDlm: String?,
    val fileList: List<RepairFile>?,
)

data class RepairFile(
    val id: String?,
    val name: String?,
    val url: String?,
    val path: String?,
)

data class RepairDetailResponse(
    val flag: Boolean?,
    val code: String?,
    val msg: String?,
    val data: RepairDetailShell?,
)

data class RepairDetailShell(
    val data: RepairDetail?,
)

/** `getBxdXqForSjd.do` 详情，抓包对象键几十个，这里只接界面要用的那部分 */
data class RepairDetail(
    val bxdId: String?,
    val bxdh: String?,
    val bxlx: String?,
    val bxsj: String?,
    val gzms: String?,
    val dqzt: String?,
    val dqztmc: String?,
    val lymc: String?,
    val qyfjh: String?,
    val sjh: String?,
    val kssj: String?,
    val jssj: String?,
    val fwly: String?,
    val fwpj: String?,
    val wcqk: String?,
    val wxlcms: String?,
    val wxlcsm: String?,
    val sgzt: String?,
    /** 维修员登录名 / 工号：取消申请时要原样回传 */
    val wxry: String?,
    val wxyDlm: String?,
    val dllx: String?,
    val dlmc: String?,
    val fileList: List<RepairFile>?,
    val wxlcFileList: List<RepairFile>?,
)

data class RepairFlowResponse(
    val flag: Boolean?,
    val code: String?,
    val msg: String?,
    val data: RepairFlowShell?,
)

data class RepairFlowShell(
    val data: RepairFlow?,
)

/** `getLctByBxdhForSjd.do` 流程：`{bxdh, clms, sj, list[], yqsmList}`，节点还能再嵌一层 list */
data class RepairFlow(
    val bxdh: String?,
    val clms: String?,
    val sj: String?,
    val list: List<RepairFlowNode>?,
)

data class RepairFlowNode(
    val bxdh: String?,
    /** 处理描述 */
    val clms: String?,
    /** 时间，抓包里是「2026年08月31日 20:35:28」这种中文格式，原样显示 */
    val sj: String?,
    val list: List<RepairFlowNode>?,
)

/** 地点树 / 类型树：扁平节点表，靠 parent 串层级，根是 parent="0" */
data class RepairTreeResponse(
    val flag: Boolean?,
    val code: String?,
    val msg: String?,
    val data: RepairTreeShell?,
)

data class RepairTreeShell(
    val data: List<RepairTreeNode>?,
)

data class RepairTreeNode(
    val name: String?,
    val parent: String?,
    val value: String?,
)

data class RepairContactResponse(
    val flag: Boolean?,
    val code: String?,
    val msg: String?,
    val data: RepairContact?,
)

data class RepairContact(
    val sjh: String?,
    val xm: String?,
)

/** 写操作的响应：只看 flag，失败时 code/msg 里是原因 */
data class RepairWriteResponse(
    val flag: Boolean?,
    val code: String?,
    val msg: String?,
)
