package com.linxin.navigation

import android.net.Uri
import kotlinx.serialization.Serializable

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val HOME = "home"

    // Checkin
    const val CHECKIN_LIST = "checkin/list"
    const val CHECKIN_DETAIL = "checkin/detail/{taskDateId}"
    fun checkinDetail(taskDateId: String) = "checkin/detail/$taskDateId"

    // Holiday
    const val HOLIDAY_LIST = "holiday/list"
    const val HOLIDAY_REGISTER = "holiday/register/{holidayId}"
    fun holidayRegister(holidayId: String) = "holiday/register/$holidayId"

    // News 公告
    const val NEWS_DETAIL = "news/detail/{newsId}"

    // 消息中心（通知点进来落这一页，底栏那个 tab 是同一块内容）
    const val MESSAGES = "messages/list"
    fun newsDetail(newsId: String) = "news/detail/${Uri.encode(newsId)}"

    // Running
    const val RUNNING_HOME = "running/home"
    const val RUNNING_ACTIVE = "running/active"
    /** 地图上点选规划路线那一页 */
    const val RUNNING_ROUTE_PLANNER = "running/route/planner"
    /** 运动记录（官方 /motionRecord） */
    const val SPORT_RECORDS = "sport/records"
    /** 运动明细（官方 /runDetail），带记录 id */
    const val SPORT_RECORD_DETAIL = "sport/record/{checkId}"
    fun sportRecordDetail(checkId: String) = "sport/record/${Uri.encode(checkId)}"
    /** 考勤维护记录（官方 /attendRecord） */
    const val SPORT_MAINTAIN = "sport/maintain"

    // Labor
    const val LABOR_SUMMARY = "labor/summary"

    // AI Class
    const val AICLASS_HOME = "aiclass/home"
    const val AICLASS_SCAN = "aiclass/scan"
    const val AICLASS_DETAIL = "aiclass/detail/{classId}"
    fun aiClassDetail(classId: String) = "aiclass/detail/${Uri.encode(classId)}"

    // More Features
    const val MORE_FEATURES = "more"

    // Exam
    const val EXAM_SCORES = "exam/scores"

    // Credit
    const val CREDIT_OVERVIEW = "credit/overview"

    // 报修（只读：我的报修单 + 详情/流程）
    const val REPAIR_LIST = "repair/list"
    const val REPAIR_DETAIL = "repair/detail/{bxdh}"
    fun repairDetail(bxdh: String) = "repair/detail/${Uri.encode(bxdh)}"
    const val REPAIR_FORM = "repair/form"

    // 请假（只读：我的申请 / 待办）
    const val LEAVE_LIST = "leave/list"
    const val LEAVE_CREATE = "leave/create"

    // 校园卡（只读：余额与卡状态）
    const val CAMPUS_CARD = "card"

    // AI Homework
    const val AICLASS_HOMEWORK_DETAIL = "aiclass/homework/{cwId}/{teachClassId}"
    fun aiClassHomeworkDetail(cwId: String, teachClassId: String) =
        "aiclass/homework/${Uri.encode(cwId)}/${Uri.encode(teachClassId)}"

    // About
    const val ABOUT = "about"

    // Theme
    const val THEME = "theme"
}
