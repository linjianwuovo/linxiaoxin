package com.linxin.feature.aiclass.domain

data class AiCourse(
    val stableId: String,
    val id: String,
    val code: String,
    val classId: String,
    val courseId: String,
    val courseRecordId: String,
    val courseName: String,
    val teacherName: String,
    val studentNum: Int,
    val teachClassId: String,
    val cover: String,
    val termYear: String,
    val term: String,
    val typeName: String,
)

data class AiSignInInfo(
    val signId: String,
    val teacherName: String,
    val hasActiveSign: Boolean,
)

data class AiWorkingRecord(
    val courseRecordId: String,
    val courseName: String,
    val courseItemName: String,
    val teachClassId: String,
)

data class AiQuiz(
    val id: String,
    val title: String,
    val isCommitted: Boolean,
    val status: String,
    val publishTime: String,
    val publishDateTime: String,
    val publishWeek: String,
    val answerDurationMinutes: Int?,
)

fun AiCourse.displayName(): String {
    if (courseName.isBlank()) return courseName
    return when {
        typeName.contains("实验") && !courseName.contains("实验") -> "${courseName}（实验）"
        typeName.contains("理论") && !courseName.contains("理论") -> "${courseName}（理论）"
        else -> courseName
    }
}

fun AiCourse.studentCountText(): String {
    return studentNum.takeIf { it > 0 }?.let { "${it}人" } ?: "人数未知"
}

/** 一门课下的 A.I微课 */
data class AiMicroLesson(
    val id: String,
    val name: String,
    val meta: String,
)

/** 一门课下的课件 */
data class AiCourseware(
    val id: String,
    val name: String,
    val fileType: String,
    val url: String,
    val createdAt: String,
)

/**
 * 课程详情页「小组」的一行：一个小组教学活动下我自己那个组。
 * name / activityName / memberCount 这些能拿到多少取决于接口回多少，
 * 拿不到的一律留空或 0，由界面显式区分「未知」和「没有」。
 */
data class AiStudentGroup(
    val id: String,
    val microGroupId: String,
    val name: String,
    val activityName: String,
    val memberCount: Int,
    val roleKnown: Boolean,
    val isLeader: Boolean,
    val pendingHomework: Int?,
    val pendingDiscuss: Int?,
    val pendingVote: Int?,
)

/** 小组里的一条讨论 */
data class AiGroupDiscuss(
    val id: String,
    val title: String,
    val meta: String,
)
