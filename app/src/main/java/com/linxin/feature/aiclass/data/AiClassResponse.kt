package com.linxin.feature.aiclass.data

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement

/** 通用响应 */
data class AiClassBaseResponse(
    val status: String?,
    val message: String?,
)

/** 学期列表响应 */
/** A.I微课：data 是个分页对象，list 里每条字段名还没实测，先原样收着 */
data class AiClassWeikeResponse(
    val status: String?,
    val message: String?,
    val data: WeikePage?,
) {
    data class WeikePage(
        val dataList: List<com.google.gson.JsonObject>?,
        val total: Int?,
    )
}

/** 课件资源列表：这一族接口的信封是 data/location/message/status，data 直接就是数组 */
data class AiClassCoursewareResponse(
    val status: String?,
    val message: String?,
    val data: List<CoursewareItem>?,
) {
    data class CoursewareItem(
        val id: String?,
        val resourceId: String?,
        val resourceName: String?,
        val fileType: String?,
        val resourceUrl: String?,
        val createTime: String?,
        val lockState: Int?,
        val isExamine: String?,
    )
}

/** 消息未读数：这一发的 status 是数字，跟别的口不一样，照实声明 */
data class AiClassUnreadResponse(
    val status: Int?,
    val message: String?,
    val data: Int?,
    val success: Boolean?,
)


data class AiClassTermResponse(
    val status: String?,
    val message: String?,
    val data: TermListData?,
) {
    data class TermListData(
        val dataList: List<TermItem>?,
    )

    data class TermItem(
        val year: String?,
        val num: String?,
        val name: String?,
        val id: String?,
        val selected: String?,
    )
}

/** 课程列表响应 */
data class AiClassCourseResponse(
    val status: String?,
    val message: String?,
    val data: CourseListData?,
) {
    data class CourseListData(
        val dataList: List<CourseItem>?,
    )

    data class CourseItem(
        val id: String?,
        val code: String?,
        val classId: String?,
        val courseId: String?,
        val courseRecordId: String?,
        val courseName: String?,
        val teacherName: String?,
        val studentNum: Int?,
        val teachClassId: String?,
        val className: String?,
        val classNames: String?,
        val classNameStr: String?,
        val cover: String?,
        val termYear: String?,
        val term: String?,
        val typeName: String?,
    )
}

/** 签到信息响应 */
data class AiClassSignInInfoResponse(
    val status: String?,
    val message: String?,
    val data: SignInData?,
) {
    data class SignInData(
        val signId: String?,
        val teacherName: String?,
        val signStatus: String?,
    )
}

/** 正在上课记录响应 */
data class AiClassWorkingRecordResponse(
    val status: String?,
    val message: String?,
    val data: List<WorkingRecordData>?,
) {
    data class WorkingRecordData(
        val courseRecordId: String?,
        val courseName: String?,
        @SerializedName("courseItemName") val courseItemName: String?,
        val courseId: String?,
        val teachClassId: String?,
    )
}

/** 测验列表响应 */
data class AiClassQuizListResponse(
    val status: String?,
    val message: String?,
    val data: JsonElement?,
) {
    // data 可能是 HAR 中的 [{ paperDetail: [...] }] 分组结构，也可能是扁平数组。
    // 保留 JsonElement，由 AiClassQuizParser 递归提取真实测验条目。
}

/** 课程详情页里的试卷信息，结构暂未完全稳定，先保留原始 JSON 再做前端提取 */
data class AiClassCoursePaperInfoResponse(
    val status: String?,
    val message: String?,
    val data: JsonElement?,
)

/** AI课堂课表响应，先保留原始 JSON，由 Repository 递归提取课程条目 */
data class AiClassTimetableResponse(
    val status: String?,
    val message: String?,
    val data: JsonElement?,
)

/** 作业列表响应 */
data class AiClassHomeworkListResponse(
    val status: String?,
    val message: String?,
    val data: HomeworkListData?,
) {
    data class HomeworkListData(
        val pageConfig: PageConfig?,
        val dataList: List<HomeworkDayGroup>?,
    )

    data class PageConfig(
        val totalPage: Int?,
        val totalCount: Int?,
    )

    data class HomeworkDayGroup(
        val dayTime: String?,
        val fileList: List<HomeworkItem>?,
    )

    data class HomeworkItem(
        val id: String?,
        val jobTitle: String?,
        val typeName: String?,
        val jobState: String?,
        val endState: String?,
        val allowOvertime: String?,
        val startTime: String?,
        val endTime: String?,
        val score: String?,
    )
}

/** 作业详情URL响应 */
data class AiClassHomeworkUrlResponse(
    val status: Int?,
    val message: String?,
    val data: String?,
)

/**
 * 小组教学活动列表：抓到的信封是 data(数组) + code/status/message/location。
 * data 元素只记到键名没记到类型，原样收 JsonObject，由 Repository 按候选键取。
 */
data class AiClassGroupListResponse(
    val status: String?,
    val code: String?,
    val message: String?,
    val data: List<com.google.gson.JsonObject>?,
)

/** 小组成员与我所在小组：data 是对象，抓到的键是 groupList / microGroupInfo / isBind / groupScreenName */
data class AiClassStudentGroupInfoResponse(
    val status: String?,
    val code: String?,
    val message: String?,
    val data: com.google.gson.JsonObject?,
)

/** 小组待办数：抓到的 data 是三个数字，discuss 那个键服务端拼成了 discsuss，按原样取 */
data class AiClassGroupTodoResponse(
    val status: String?,
    val code: String?,
    val message: String?,
    val data: com.google.gson.JsonObject?,
)

/** 我的小组讨论：抓到的那次 data.studentDis 是空数组，条目字段名未知，先整个收着 */
data class AiClassGroupDiscussResponse(
    val status: String?,
    val code: String?,
    val message: String?,
    val data: com.google.gson.JsonElement?,
)
