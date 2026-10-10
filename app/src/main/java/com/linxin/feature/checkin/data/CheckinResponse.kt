package com.linxin.feature.checkin.data

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement

/**
 * POST /app/dorm/pageStudentSignIn 响应
 */
data class SignInPageResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: SignInPageData?,
    val rows: List<SignInTaskRow>?,
    val total: Int?,
)

data class SignInPageData(
    val list: List<SignInTaskRow>?,
    /** 历史端点（collectionStudentPage / signin/queryPage）把数组放在这里，不是 list */
    val rows: List<SignInTaskRow>?,
    val totalPage: Int?,
    val totalCount: Int?,
)

data class SignInTaskRow(
    val id: String?,
    val title: String?,
    val taskName: String?,
    val taskId: String?,
    val taskDateId: String?,
    val signinStatus: String?,       // "0"=未签到, "1"=已签到
    @SerializedName("executionedStatus")
    val executionedStatus: String?,
    val executionedStatusTxt: String?,   // 服务端给的状态原文：未开始 / 未签到 / 已签到
    val taskDate: String?,               // 任务所属日期 yyyy-MM-dd
    val startTime: String?,
    val endTime: String?,
    val timedStartTime: String?,
    val timedEndTime: String?,
    val collectionStartTime: String?,
    val collectionEndTime: String?,
    val taskMajorType: String?,
    val signinPhoto: String?,
    val signinPlace: String?,
    val latLng: String?,
    val userTaskId: String?,
)

/**
 * POST /app/dorm/getTaskInfoByDateId 响应
 */
data class TaskInfoResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: TaskInfoData?,
)

data class TaskInfoData(
    val title: String?,
    val taskName: String?,
    val dateId: String?,
    val taskDateId: String?,
    val startTime: String?,
    val endTime: String?,
    val timedStartTime: String?,
    val timedEndTime: String?,
    val collectionStartTime: String?,
    val collectionEndTime: String?,
    val needPhoto: String?,          // "1"=需要拍照
    val photoRequire: String?,
    val signinPhoto: String?,
    val signinStatus: String?,
    val signStatus: String?,
    val signinPlace: String?,
    val locationRange: String?,      // 签到范围(米)
    val lng: String?,                // 签到中心经度
    val lat: String?,                // 签到中心纬度
    val address: String?,            // 签到地点描述
    val signinLocations: List<SigninLocationRow>?,
)

data class SigninLocationRow(
    val content: Any?,
    val lngLat: String?,
    val signinLocation: String?,
)

/**
 * POST /app/file/uploadFileToFastdfs 响应
 */
data class FileUploadResponse(
    val code: String?,
    val msg: String?,
    val data: JsonElement?,          // 上传结果，可能是字符串或对象
)

/**
 * POST /app/dorm/signIn 响应
 */
data class SignInSubmitResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: Any?,
)

/**
 * POST /app/dorm/listDateCheck 响应：区间内每天的签到任务数（用来画月历）
 */
data class DateCheckResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: List<DateCheckItem>?,
)

data class DateCheckItem(
    val taskDate: String?,
    val taskNum: Int?,
)

/**
 * POST /app/dorm/collectionStudentStatics 响应：本月签到统计
 * 字段含义按安小信实测：signined=已签, notSignined=未签, signining=进行中,
 * leave=请假, leaveSchool=离校, qrCode=扫码签到。
 */
data class StudentStaticsResponse(
    val code: String?,
    val flag: Boolean?,
    val msg: String?,
    val data: StudentStaticsData?,
)

data class StudentStaticsData(
    val signinedNum: Int?,
    val notSigninedNum: Int?,
    val signiningNum: Int?,
    val leaveNum: Int?,
    val leaveSchoolNum: Int?,
    val qrCodeNum: Int?,
)
