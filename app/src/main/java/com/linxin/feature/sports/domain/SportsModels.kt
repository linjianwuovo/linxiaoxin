package com.linxin.feature.sports.domain

/** 服务端下发的那一套跑步约束，界面上一律读它、不写死 */
data class RunRules(
    val grade: String = "",
    val termTitle: String = "",
    val planStart: String = "",
    val planEnd: String = "",
    val planMileKm: Double = 0.0,
    val completeMileKm: Double = 0.0,
    val surplusMileKm: Double = 0.0,
    val todayMileKm: Double = 0.0,
    val todayDate: String = "",
    val bestMileKm: Double = 0.0,
    val bestMileDate: String = "",
    /** 单次最少多少公里（`mixOnceMile`，实测 1） */
    val singleMinKm: Double = 1.0,
    /** 一天最多按多少公里计（`maxDayMile`，实测 3） */
    val dailyMaxKm: Double = 3.0,
    /** 速率窗口（`minSpeed` / `maxSpeed`，实测 2 和 15，单位 km/h） */
    val minSpeedKmh: Double = 2.0,
    val maxSpeedKmh: Double = 15.0,
    val memberId: String = "",
    /** 大一大二没有 startRunning.do，会话标识就是这条 extraId */
    val extraId: String = "",
) {
    val isJunior: Boolean get() = grade.contains("大一大二")

    val ratioPercent: Int
        get() = if (planMileKm <= 0.0) 0 else ((completeMileKm / planMileKm) * 100).toInt()
}

/** 开场信息：交作业时要带回去的那三样 */
data class RunSession(
    val exerciseId: String,
    val memberId: String,
    val runningType: String = "1",
    /** 这一场要跑够多少公里才算有效（服务器下发的 mixOnceMile），流体云那条进度条照它走 */
    val targetKm: Double = 1.0,
    val startedAtMillis: Long = System.currentTimeMillis(),
)

/** 一个轨迹点，本地一律 WGS-84（上交给学校时才换 BD-09） */
data class WgsPoint(
    val latitude: Double,
    val longitude: Double,
    val atMillis: Long = System.currentTimeMillis(),
)

/** 跑完一场、准备上交的那一坨 */
data class RunRecordDraft(
    val session: RunSession,
    val startText: String,
    val endText: String,
    val mileKm: Double,
    val timeSeconds: Long,
    val speedKmh: Double,
    val points: List<WgsPoint>,
)

enum class RecordState {
    VALID, INVALID, PROCESSING, UNKNOWN;

    fun label(): String = when (this) {
        VALID -> "有效"
        INVALID -> "无效"
        PROCESSING -> "处理中"
        UNKNOWN -> "未标注"
    }
}

/** `getCheckList.do` 的行 */
data class RecordRow(
    val id: String,
    val name: String,
    /** 1 打卡 / 2 跑步（课外锻炼）/ 3 团体课 */
    val type: String,
    val mileKm: Double,
    val coefficient: String,
    val startText: String,
    /** 官方止期只给时分 */
    val endText: String,
    val stateCode: String,
) {
    val state: RecordState
        get() = when (stateCode) {
            "1" -> RecordState.VALID
            "2" -> RecordState.INVALID
            "3" -> RecordState.PROCESSING
            else -> RecordState.UNKNOWN
        }

    val timeRange: String get() = if (endText.isBlank()) startText else "$startText-$endText"
}

data class RecordPage(val rows: List<RecordRow>, val total: Int)

data class RecordTotals(
    val totalMileKm: Double = 0.0,
    val groupMileKm: Double = 0.0,
    val groupCount: Int = 0,
    val extraMileKm: Double = 0.0,
    val morningMileKm: Double = 0.0,
)

/**
 * `extra/getExtraCheck.do` 的明细。
 *
 * 明细页的状态判定跟列表**不是一套**：官方渲染函数里 `3`=处理中、`1|4`=有效、其余=无效，
 * 而且只有 `state==="0"` 才把 `remark` 当「说明：xxx」显示。
 */
data class RecordDetail(
    val checkId: String,
    val startText: String,
    val endText: String,
    val mileKm: Double,
    val speedKmh: Double,
    val timeText: String,
    val stateCode: String,
    val remark: String,
    /** 库里存的那条轨迹，坐标是 BD-09 */
    val trackBd09: List<WgsPoint>,
    val handleTime: String,
    val handleUser: String,
    val handleRemark: String,
    val applyTime: String,
    val applyReason: String,
    val auditTime: String,
    val auditUser: String,
    val auditState: String,
    val auditRemark: String,
) {
    val state: RecordState
        get() = when (stateCode) {
            "3" -> RecordState.PROCESSING
            "1", "4" -> RecordState.VALID
            else -> RecordState.INVALID
        }

    val showsRemark: Boolean get() = stateCode == "0" && remark.isNotBlank()
    val showsHandle: Boolean get() = stateCode in setOf("3", "4", "5")
    val titleText: String get() = listOf(startText, endText).filter { it.isNotBlank() }.joinToString("-")
}

/** `getApplyRecordList.do` 的行（这台账号抓包时 rows 全空，字段名来自官方渲染函数，取不到就留空） */
data class AppealRow(
    val id: String,
    val timeText: String,
    val mileKm: Double,
    val timeUsedText: String,
    val speedKmh: Double,
    val exerciseState: String,
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

data class AppealPage(val rows: List<AppealRow>, val total: Int)
