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

/**
 * 一门课下的课件。
 * resourceId / coursewareId 是点开那一发要的两个 id —— 抓包实测它们跟列表里的 id 不是同一个值，
 * 少了任何一个就换不到逐页图，所以列表解析时必须原样带下来。
 */
data class AiCourseware(
    val id: String,
    val name: String,
    val fileType: String,
    val url: String,
    val createdAt: String,
    val resourceId: String = "",
    val coursewareId: String = "",
)

/**
 * 「直录播」的一节。只带事实不带文案，状态由界面翻成中英文。
 *
 * 状态为什么是 isUpcoming + canPlay 这两个布尔，而不是照抄服务端的 isNowPlay：
 * 2026-10-12 在 OPPO 屏幕上验过，整学期每一条（包括 10-13、11-10 这些还没上的）
 * isNowPlay 都是真，所以它说的是「这是一节直播课」，不是「现在正在播」。
 * 拿它当「直播中」显示，会把两个月后的课标成正在播，所以这个键我们不用。
 *
 * dateText 是服务端给的 yyyy-MM-dd，界面不显示它，只用来跟今天比出 isUpcoming。
 */
data class AiLiveRecord(
    val id: String,
    val title: String,
    val roomText: String,
    val whenText: String,
    val dateText: String,
    val isUpcoming: Boolean,
    val canPlay: Boolean,
    val playUrl: String,
    val resourceId: String,
)

/**
 * 点开一个课件之后拿到的东西。pages 是学校已经渲染好的逐页 PNG 直链（实测一节 pptx 75 页），
 * 这是安小信自己那个翻页查看器吃的同一批图，所以看全篇不用跳出去。
 * previewUrl / sourceUrl / videoUrl 是三层兜底：换到 Office 在线预览地址、源文件直链、视频地址。
 */
data class AiCoursewareDetail(
    val name: String,
    val pages: List<String>,
    val previewUrl: String,
    val sourceUrl: String,
    val videoUrl: String,
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
