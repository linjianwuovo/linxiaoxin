package com.linxin.feature.running.domain

/**
 * 运动记录那一屏的数据形状。
 *
 * 字段名是照官方 H5（`mobile/check/getCheckList.do` 的真实响应 + 明细页渲染函数）
 * 一个个对出来的，不是我们自己起的。
 */
data class RunRecordRow(
    val id: String,
    val name: String,
    /** 1 打卡 / 2 跑步（课外锻炼）/ 3 团体课 —— 官方按这个分三套状态文案 */
    val type: String,
    val mileKm: Double,
    /** 里程系数，官方行里写 " * 1" 就是它 */
    val coefficient: String,
    /** 起：`2026-10-14 17:01` */
    val startText: String,
    /** 止：官方只给时分 `17:07` */
    val endText: String,
    val durationText: String?,
    /** 打卡类那一行官方给的是打卡时间 */
    val checkTimeText: String?,
    /** 官方状态码，见 [RunRecordState] */
    val stateCode: String,
) {
    val state: RunRecordState
        get() = when (stateCode) {
            "1" -> RunRecordState.VALID
            "2" -> RunRecordState.INVALID
            "3" -> RunRecordState.PROCESSING
            else -> RunRecordState.UNKNOWN
        }

    val timeRangeText: String
        get() = if (endText.isBlank()) startText else "$startText-$endText"
}

enum class RunRecordState {
    VALID, INVALID, PROCESSING, UNKNOWN;

    /** 官方列表页那颗胶囊的字：type=2 时 1 有效 / 2 无效 / 3 处理中 */
    fun label(): String = when (this) {
        VALID -> "有效"
        INVALID -> "无效"
        PROCESSING -> "处理中"
        UNKNOWN -> "未标注"
    }
}

data class RunRecordPage(
    val rows: List<RunRecordRow>,
    val total: Int,
)

/** 列表顶上那几格汇总 */
data class RunTotals(
    val totalMileKm: Double,
    val groupMileKm: Double,
    val groupCount: Int,
    val extraMileKm: Double,
    val morningMileKm: Double,
)

/**
 * 运动明细（`mobile/extra/getExtraCheck.do`）。
 *
 * 官方这一屏的判定跟列表不一样：明细用的是另一套 `state`，
 * 3 处理中、1/4 有效、其余无效，且只有 state=0 才把 remark 当「说明」显示。
 */
data class RunDetail(
    val checkId: String,
    val titleText: String,
    val mileKm: Double,
    val speedKmh: Double,
    val durationText: String,
    val stateCode: String,
    val remark: String,
    val trackPoints: List<TrackPoint>,
    /** 异常处理信息 */
    val handledAt: String,
    val handledBy: String,
    val handleRemark: String,
    /** 申请维护信息 */
    val appliedAt: String,
    val applyReason: String,
    /** 审核信息 */
    val auditedAt: String,
    val auditor: String,
    val auditOpinion: String,
    val auditRemark: String,
    val auditState: String,
) {
    val state: RunRecordState
        get() = when (stateCode) {
            "3" -> RunRecordState.PROCESSING
            "1", "4" -> RunRecordState.VALID
            else -> RunRecordState.INVALID
        }

    /** 官方：只有这条才在明细里显示「说明：xxx」 */
    val showsRemark: Boolean get() = stateCode == "0" && remark.isNotBlank()
    val showsHandled: Boolean get() = stateCode in setOf("3", "4", "5")
    val canApply: Boolean get() = checkId.isNotBlank()
}

/** 考勤维护记录的一行 */
data class MaintainRow(
    val id: String,
    /** 官方页签号：0 自主练习考勤 / 1 体育团体课考勤 / 2 课外锻炼考勤 */
    val queryType: Int,
    val exerciseType: String,
    val timeText: String,
    val mileKm: Double,
    val durationText: String,
    val speedKmh: Double,
    /** 有效 / 无效 */
    val exerciseState: String,
    /** 同意 / 驳回 / 处理中 */
    val auditState: String,
    val reason: String,
) {
    val auditLabel: String
        get() = when (auditState) {
            "0" -> "处理中"
            "1" -> "同意"
            "2" -> "驳回"
            else -> "未审核"
        }
}

data class MaintainPage(
    val rows: List<MaintainRow>,
    val total: Int,
)
