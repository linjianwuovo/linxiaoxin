package com.linxin.feature.running.domain

data class RunningDashboard(
    val todayKm: Double = 0.0,
    val completedKm: Double = 0.0,
    val leftKm: Double = 0.0,
    val taskTargetKm: Double = 0.0,
    val singleRunTargetKm: Double = 0.0,
    val maxKm: Double = 0.0,
    val maxKmDate: String = "",
    val dsFlag: Boolean = false,
    val studentTypeLabel: String = "",
    val clubSummary: ClubSummary? = null,
    /** 下面这几条全是服务端下发的「跑步有效性」约束，别自己写死 */
    val maxDayKm: Double = 0.0,
    val minSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val termStart: String = "",
    val termEnd: String = "",
    val todayDate: String = "",
    /** 官方标题那串：`2026-2027-1` */
    val termTitle: String = "",
) {
    /** 完成比，官方进度条上那个百分数 */
    val ratioPercent: Int
        get() = if (taskTargetKm <= 0.0) 0 else ((completedKm / taskTargetKm) * 100).toInt()
}

/** 大一大二首页俱乐部摘要，来自 index/clubInfo.do；大三大四为 null。 */
data class ClubSummary(
    val courseName: String = "",
    val term: String = "",
    val teacherName: String = "",
    val memberLevel: String = "",
)

data class RunningStartInfo(
    val exerciseId: String,
    val memberId: String,
    val runningType: String = "1",
    val mixOnceMileKm: Double = 1.0,
    val validSettingLj: String = "",
    val taskFlag: String = "",
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class RunningTrackerState(
    val startInfo: RunningStartInfo? = null,
    val startTimeMillis: Long = 0L,
    val isSessionActive: Boolean = false,
    val isCollecting: Boolean = false,
    val totalDistanceMeters: Double = 0.0,
    val points: List<TrackPoint> = emptyList(),
    val lastPoint: TrackPoint? = null,
    val locationLabel: String = "未开始",
    val errorMessage: String? = null,
    /** 暂停中：官方那颗「暂停」= clearWatch + clearInterval，一个点都不收、时长也不走 */
    val isPaused: Boolean = false,
    /** 这一次是按下去的那一刻（null = 没在暂停） */
    val pausedAtMillis: Long? = null,
    /** 累计暂停了多少毫秒，算时长要从头扣掉 */
    val pausedTotalMillis: Long = 0L,
)

data class RunningSnapshot(
    val startInfo: RunningStartInfo,
    val startTimeMillis: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val points: List<TrackPoint>,
    val pointsAreBd09: Boolean = false,
)

data class RunningResult(
    val success: Boolean,
    val message: String,
    val uploadId: String = "",
    val startDate: String,
    val endDate: String,
    val distanceKm: Double,
    val durationSeconds: Long,
    val speedKmh: Double,
    val pointCount: Int,
)
