package com.linxin.feature.aiclass.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface AiClassApi {

    /** 课程列表 */
    @FormUrlEncoded
    @POST("studycenter/mobile/student/myClassroom")
    suspend fun getCourses(
        @Field("termYear") termYear: String,
        @Field("term") term: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassCourseResponse

    /** 学期列表 */
    @POST("studycenter/mobile/common/termList")
    suspend fun getTermList(
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassTermResponse

    /** 查询当前签到信息 */
    @FormUrlEncoded
    @POST("coursecenter-interaction/sign/getSignInCourseInfo")
    suspend fun getSignInInfo(
        @Field("teachClassId") teachClassId: String,
        @Field("courseRecordId") courseRecordId: String,
        @Field("studentId") studentId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassSignInInfoResponse

    /** 数字码签到 */
    @FormUrlEncoded
    @POST("coursecenter-interaction/qrcodeV2/checkQrcodeHandler")
    suspend fun submitSignCode(
        @Field("signId") signId: String,
        @Field("userName") userName: String,
        @Field("signCode") signCode: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassBaseResponse

    /** 查询当前正在上课记录 */
    @FormUrlEncoded
    @POST("coursecenter-interaction/courseRecord/getWorkingCourseRecordByStudentId")
    suspend fun getWorkingCourseRecord(
        @Field("studentId") studentId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassWorkingRecordResponse

    /** 扫码签到：处理二维码 token（302 跳转，需手动处理） */
    @GET("coursecenter-interaction/qrcodeV2/qrcodeHandler")
    suspend fun qrcodeHandler(
        @Query("token") token: String,
        @Query("openTheWay") openTheWay: String = "2",
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): retrofit2.Response<okhttp3.ResponseBody>

    /** 课程详情里的试卷信息，可能包含历史测验入口数据 */
    @GET("coursecenter-interaction/paper/getPaperInCourseInfo")
    suspend fun getPaperInCourseInfo(
        @Query("courseId") courseId: String,
        @Query("studentId") studentId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassCoursePaperInfoResponse

    /** 学生可见测验列表 */
    @GET("coursecenter-interaction/paper/getPublishPaperListOfStudent")
    suspend fun getPublishPaperListOfStudent(
        @Query("courseId") courseId: String,
        @Query("studentId") studentId: String,
        @Query("userId") userId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassQuizListResponse

    /** AI课堂课表，用于补齐“我的课程”未返回的当前学期课程 */
    @GET("coursecenter-interaction/liveAndRecord/timetableInfo")
    suspend fun getTimetableInfo(
        @Query("schoolId") schoolId: String,
        @Query("mid") memberId: String,
        @Query("identity") identity: String = "2",
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassTimetableResponse

    /** 课件资源列表：resourceType=1 就是课件（抓到的 22 条全是 pptx） */
    @GET("coursecenter-interaction/courseware/queryCoursewareResourceList")
    suspend fun getCoursewareList(
        @Query("courseId") courseId: String,
        @Query("teachClassId") teachClassId: String,
        @Query("resourceType") resourceType: String = "1",
        @Query("userId") userId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassCoursewareResponse

    /**
     * A.I微课列表。参数是 2026-10-12 在 OPPO 上从安小信自己那次点击里量出来的：
     * courseId(19) + userName(14) + pageNum + pageSize(4)，信封 data{dataList,pageNum,pageSize,total}。
     * dataList 里每条的字段名没验过（那次回的 112 字节是空页），所以这里只吃 JsonObject，
     * 取名字时按几个候选键试，不硬写一个没见过的字段名。
     */
    @GET("coursecenter-interaction/weike/listWeikeByCourseId")
    suspend fun getWeikeList(
        @Query("courseId") courseId: String,
        @Query("userName") userName: String,
        @Query("pageNum") pageNum: String = "1",
        @Query("pageSize") pageSize: String = "50",
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassWeikeResponse

    /**
     * 小组教学：这门课开了哪些小组教学活动。
     * 参数是抓包里安小信实际发的那一发，只有 teachClassId。
     * 抓到的信封是 data(数组)/code/status/message/location，data 里每条的键名见到过
     * id、gName、gCount、gNum、gCountLimit、groupStuNum、gStatus、classId、creatorId、
     * createTime、updateTime、endTime、rule，但每个键的类型没记下来，所以只收 JsonObject，
     * 取值一律按候选键试。
     */
    @GET("studycenter/mobile/groupTeaching/groupList")
    suspend fun getGroupTeachingList(
        @Query("teachClassId") teachClassId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassGroupListResponse

    /**
     * 某个小组里有谁、我在不在里面、是不是组长。抓包参数：teachClassId + studentId(32 位成员 id) + groupId。
     * data 里 groupList 是成员数组（键名见到过 id、isLeader、stuName、stuNum、className、majorName、
     * departmentName、photoUrl），microGroupInfo 是我自己那个组（键名见到过 gId、groupId、xName、xNum、
     * xCount、xCode、createTime、creatorId）。同样只记到了键名没记到类型，继续只收 JsonObject。
     */
    @GET("studycenter/mobile/groupTeaching/getStudentGroupInfo")
    suspend fun getStudentGroupInfo(
        @Query("teachClassId") teachClassId: String,
        @Query("studentId") studentId: String,
        @Query("groupId") groupId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassStudentGroupInfoResponse

    /**
     * 小组待办数。抓包参数：microGroupId + schoolId + teachClassId + studentId + courseId + groupId
     * （那次 microGroupId 传的是空串，接口照样回）。
     * 抓到的 data 是 homeWorkNoCommit / discsussNoCommit / voteNoCommit 三个数字，
     * 中间那个 discuss 服务端就是拼错的，这里照抄不改。
     */
    @GET("coursecenter-interaction/groupTeaching/getOpeningGroupCourseCountInfo")
    suspend fun getGroupTodoCount(
        @Query("microGroupId") microGroupId: String,
        @Query("schoolId") schoolId: String,
        @Query("teachClassId") teachClassId: String,
        @Query("studentId") studentId: String,
        @Query("courseId") courseId: String,
        @Query("groupId") groupId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassGroupTodoResponse

    /**
     * 我的小组讨论列表。抓包参数：microGroupId(32 位，就是 microGroupInfo 里的 gId) + studentId。
     * 那次回的 data.studentDis 是个空数组，所以讨论条目的字段名一点实测依据都没有，
     * 全部按候选键试，一个都对不上就把这一行当无名讨论显示，绝不假设某个键一定存在。
     */
    @GET("studycenter/mobile/groupTeaching/findStudentSelfGroupDiscuss")
    suspend fun getStudentSelfGroupDiscuss(
        @Query("microGroupId") microGroupId: String,
        @Query("studentId") studentId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassGroupDiscussResponse

    /** 课程作业列表 */
    /** AI 课堂消息中心未读数：安小信进 AI 课堂时会打这一发，我们之前没有 */
    @FormUrlEncoded
    @POST("studycenter/mobile/ZjzxMessage/getUnReadCount")
    suspend fun getMessageUnreadCount(
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassUnreadResponse

    /**
     * 「直录播」那一屏的本体。八个字段是 2026-10-12 深夜在 OPPO 上从安小信自己那一发里量出来的：
     * roomType=0、liveStatus=0、termId(32 位学期 id)、weekNum / dayNum / timeRange 那次是空串（= 不限）、
     * courseId、teachClassId。实测回 17621 字节 / 20 条。
     * 条目键名记到了 livePath、liveStatus、isNowPlay、isOpen、isAllowDownload、resourceId、
     * roomId、roomName、courseName、currentDate、currentDay、currentWeek、classType，
     * 但每个键的类型没记下来，照旧只收 JsonObject，取值一律按候选键试。
     */
    @FormUrlEncoded
    @POST("studycenter/mobile/course/getTeachHourLiveAndRecordList")
    suspend fun getLiveAndRecordList(
        @Field("roomType") roomType: String = "0",
        @Field("liveStatus") liveStatus: String = "0",
        @Field("termId") termId: String,
        @Field("weekNum") weekNum: String = "",
        @Field("dayNum") dayNum: String = "",
        @Field("timeRange") timeRange: String = "",
        @Field("courseId") courseId: String,
        @Field("teachClassId") teachClassId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassLiveRecordListResponse

    /** 直录播的周次选择：回 data.currentWeek 和 data.weekList（实测 22 个整数） */
    @GET("studycenter/mobile/course/liveAndRecord/getWeekList")
    suspend fun getLiveRecordWeekList(
        @Query("identity") identity: String = "2",
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassWeekListResponse

    /**
     * 点开一个课件。抓包参数：resourceId + schoolId + type=3 + courseWareId + openCourse=1 + teachClassId。
     * 关键是 data.pages[] —— 每页一张已经渲染好的 minio PNG（imgUrl、page、flagType、questionList），
     * 安小信自己那个「1 / 75」的翻页查看器吃的就是这批图。所以我们不必跳去 Office 在线预览就能看全篇。
     * data 里另有 resourceName、resourceUrl（又一条签名不同的源文件直链）、audioPath、videoPath、resourceType。
     */
    @GET("coursecenter-interaction/afterClassCoursewarePaper/viewCoursewareTeacher")
    suspend fun getCoursewareDetail(
        @Query("resourceId") resourceId: String,
        @Query("schoolId") schoolId: String,
        @Query("type") type: String = "3",
        @Query("courseWareId") courseWareId: String,
        @Query("openCourse") openCourse: String = "1",
        @Query("teachClassId") teachClassId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassCoursewareDetailResponse

    /**
     * 换录播的播放地址。这个口挂在资源中心（resc.fifedu.com）上，跟主站不是一个 base，
     * 所以用 @Url 传整条地址。抓包实测 data.videoList 是数组，一条一个机位
     * （videoCode=1 教师机位 / 2 HDMI），每条的 videoPath 是**当场签名**的 minio mp4 直链
     * （authKey/timestamp/memberId/X-Amz-Signature 每次调用都变），videoSize 是字节数。
     * audioPath 和 phaseUrl 实测是空串，别指望它们。
     *
     * 请求头照 2026-10-13 抓到的官方那一发抄：它**不带** Visit-Type: mobile，
     * 而是带播放页自己的 Referer/Origin（mresc.fifedu.com）。我们一开始按主站习惯加了 Visit-Type，
     * 服务器直接回 data=null，看着像"这一节没录像"，其实是头不对。
     */
    @Headers(
        "Accept: application/json, text/plain, */*",
        "Content-Type: application/json",
        "Referer: https://mresc.fifedu.com/",
        "Origin: https://mresc.fifedu.com",
        "X-Requested-With: com.linxin",
        "User-Agent: Mozilla/5.0 (Linux; Android 17; PMA110 Build/CP2A.260605.016; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/156.0.8078.4 Mobile Safari/537.36",
    )
    @GET
    suspend fun getVideoClassInfo(
        @Url url: String,
        @Header("authorization") auth: String,
    ): AiClassVideoClassInfoResponse


    @GET("coursecenter-interaction/izuoye/homework/public/listStudent")
    suspend fun listStudentHomework(
        @Query("courseId") courseId: String,
        @Query("teachClassId") teachClassId: String,
        @Query("isAll") isAll: String = "0",
        @Query("page") page: String = "1",
        @Query("size") size: String = "1000",
        @Query("type") type: String = "1",
        @Query("appKey") appKey: String = "aikt",
        @Query("userName") userName: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassHomeworkListResponse

    /** 获取作业详情入口URL（含jtzy token） */
    @GET("coursecenter-interaction/ai/homework/getHomeworkDetaisIndexUrl")
    suspend fun getHomeworkDetailUrl(
        @Query("userName") userName: String,
        @Query("appId") appId: String = "akt",
        @Query("classId") classId: String,
        @Query("applyType") applyType: String = "1",
        @Query("cwId") cwId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassHomeworkUrlResponse
}
