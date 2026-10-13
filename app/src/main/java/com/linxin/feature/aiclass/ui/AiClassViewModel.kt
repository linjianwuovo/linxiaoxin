package com.linxin.feature.aiclass.ui

import com.linxin.core.locale.AppText
import com.linxin.R
import androidx.compose.ui.res.stringResource
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.core.network.FifSessionManager
import com.linxin.feature.aiclass.data.AiClassRepository
import com.linxin.feature.aiclass.domain.AiCourse
import com.linxin.feature.aiclass.domain.AiMicroLesson
import com.linxin.feature.aiclass.domain.AiCourseware
import com.linxin.feature.aiclass.domain.AiCoursewareDetail
import com.linxin.feature.aiclass.domain.AiLiveRecord
import com.linxin.feature.aiclass.domain.AiRecordedVideo
import com.linxin.feature.aiclass.domain.AiQuiz
import com.linxin.feature.aiclass.domain.AiGroupDiscuss
import com.linxin.feature.aiclass.domain.AiStudentGroup
import com.linxin.feature.aiclass.domain.AiClassQrPayload
import com.linxin.feature.aiclass.domain.AiWorkingRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val VIEWMODEL_LOG_TAG = "AiClassVM"
private const val AICLASS_INITIAL_LOAD_TIMEOUT_MS = 18_000L
private const val AICLASS_OPTIONAL_LOAD_TIMEOUT_MS = 8_000L
// 测验走的是学校自己的库、作业走的是爱作业（第三方），12 秒这两条经常不够用，
// 用户看到的就是「测验/作业超时」——和其他几发对齐到 25 秒。
private const val AICLASS_DETAIL_LOAD_TIMEOUT_MS = 25_000L
/** 课件这一发实测比测验慢得多，先给它到 25 秒，再拿屏幕上的出现时间判断是慢还是回不来 */
private const val COURSEWARE_LOAD_TIMEOUT_MS = 25_000L

data class AiClassUiState(
    val courses: List<AiCourse> = emptyList(),
    val workingRecord: AiWorkingRecord? = null,
    val selectedCourse: AiCourse? = null,
    val quizList: List<AiQuiz> = emptyList(),
    val homeworkList: List<com.linxin.feature.aiclass.domain.AiHomework> = emptyList(),
    val isLoading: Boolean = true,
    val isSsoInProgress: Boolean = false,
    val isSigningIn: Boolean = false,
    val isQuizLoading: Boolean = false,
    val isHomeworkLoading: Boolean = false,
    val error: String? = null,
    val quizError: String? = null,
    val homeworkError: String? = null,
    val signResult: String? = null,
    /** signResult 是成功还是失败；扫码页据此决定「确定」是退出还是继续扫 */
    val signSucceeded: Boolean = false,
    /** AI 课堂消息中心未读数；null 表示还没拿到 */
    val messageUnread: Int? = null,
    val coursewareList: List<AiCourseware> = emptyList(),
    val isCoursewareLoading: Boolean = false,
    val coursewareError: String? = null,
    val microLessons: List<AiMicroLesson> = emptyList(),
    val microError: String? = null,
    val studentGroups: List<AiStudentGroup> = emptyList(),
    val isGroupLoading: Boolean = false,
    val groupError: String? = null,
    /** 展开看讨论的那个小组的 id；null 表示没展开 */
    val selectedGroupId: String? = null,
    val groupDiscusses: List<AiGroupDiscuss> = emptyList(),
    val isGroupDiscussLoading: Boolean = false,
    val groupDiscussError: String? = null,
    /** 「直录播」这一屏：一节一节，直播中的和能回放的混在一起 */
    val liveRecords: List<AiLiveRecord> = emptyList(),
    val isLiveLoading: Boolean = false,
    val liveError: String? = null,
    /** 正在放的那节录播：换到的机位列表 + 选中的那条；空列表 = 没在放 */
    val liveVideos: List<AiRecordedVideo> = emptyList(),
    val selectedVideoIndex: Int = 0,
    val openingLiveTitle: String = "",
    val isOpeningLive: Boolean = false,
    val openLiveError: String? = null,
    /** 当前点开的那个课件；null 表示没在看课件 */
    val openedCourseware: AiCoursewareDetail? = null,
    /** 正在打开或打开失败的那个课件，标题和「再试一次」都靠它 */
    val openingCourseware: AiCourseware? = null,
    val isOpeningCourseware: Boolean = false,
    val openCoursewareError: String? = null,
)

@HiltViewModel
class AiClassViewModel @Inject constructor(
    private val repository: AiClassRepository,
    private val fifSession: FifSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiClassUiState())
    val uiState: StateFlow<AiClassUiState> = _uiState

    init {
        load()
        loadUnread()
    }

    /** 未读角标单独一发，不跟课程加载抢，也不参与整页 loading */
    private fun loadUnread() {
        viewModelScope.launch {
            _uiState.update { it.copy(messageUnread = repository.getMessageUnreadCount()) }
        }
    }

    fun load() {
        viewModelScope.launch {
            // 有缓存先秒开（不显示整页 loading），随后后台刷新覆盖；无缓存才走整页加载。
            val cachedCourses = repository.peekCourses()
            _uiState.update {
                it.copy(
                    courses = cachedCourses ?: it.courses,
                    workingRecord = repository.peekWorking() ?: it.workingRecord,
                    isLoading = cachedCourses == null,
                    error = null,
                )
            }

            // 确保 SSO
            if (!fifSession.isSessionValid()) {
                _uiState.update { it.copy(isSsoInProgress = true) }
                val ssoResult = fifSession.performSso()
                _uiState.update { it.copy(isSsoInProgress = false) }
                if (ssoResult.isFailure) {
                    _uiState.update {
                        it.copy(isLoading = false, error = ssoResult.exceptionOrNull()?.message)
                    }
                    return@launch
                }
            }

            // 课程是主内容；课堂状态是附属信息，不能拖住整页加载。
            suspend fun loadPair(): Pair<Result<List<AiCourse>>, Result<AiWorkingRecord?>> {
                val coursesDeferred = async {
                    withResultTimeout(AppText.str(R.string.aivm_act_courses), AICLASS_INITIAL_LOAD_TIMEOUT_MS) {
                        repository.getCourses()
                    }
                }
                val workingDeferred = async {
                    withResultTimeout(AppText.str(R.string.aivm_act_status), AICLASS_OPTIONAL_LOAD_TIMEOUT_MS) {
                        repository.getWorkingRecord()
                    }
                }
                return coursesDeferred.await() to workingDeferred.await()
            }

            var (coursesResult, workingResult) = loadPair()

            // 首次拉课程失败（多为 FIF 会话刚失效/连接冷启动）时，自动强制刷新 SSO 再试一次，
            // 免去用户每次进页面都要手动点"重试"。
            if (coursesResult.isFailure) {
                Log.w(VIEWMODEL_LOG_TAG, "首次加载课程失败，自动刷新 SSO 重试: ${coursesResult.exceptionOrNull()?.message}")
                val refreshed = fifSession.performSso()
                if (refreshed.isSuccess) {
                    val retry = loadPair()
                    coursesResult = retry.first
                    // 课堂状态若之前也没拿到，用重试结果补上
                    if (workingResult.isFailure) workingResult = retry.second
                }
            }

            _uiState.update {
                val courses = coursesResult.getOrDefault(emptyList()).ifEmpty { it.courses }
                it.copy(
                    courses = courses,
                    workingRecord = workingResult.getOrNull() ?: it.workingRecord,
                    isLoading = false,
                    // 刷新失败但仍有缓存时不报错、继续展示缓存，避免偶发抖动直接甩错误页
                    error = if (courses.isEmpty()) coursesResult.exceptionOrNull()?.message else null,
                )
            }
        }
    }

    fun submitSignCode(signCode: String) {
        if (signCode.length != 6) {
            _uiState.update {
                it.copy(signSucceeded = false, signResult = AppText.str(R.string.sign_code_too_short))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, signResult = null) }

            // signId 只是加分项：官方那个输入框传的是 `this.signId || $route.query.signId`，
            // 两个都没有就是空串，服务器照样按那 6 位码认人。
            // 之前这里没查到"正在上课"就直接 return，用户按了签到屏幕上一点反应都没有。
            val working = _uiState.value.workingRecord
            var signId = ""
            if (working != null) {
                val info = repository.getSignInInfo(
                    teachClassId = working.teachClassId,
                    courseRecordId = working.courseRecordId,
                ).getOrNull()
                if (info != null && info.hasActiveSign) signId = info.signId
            }

            val result = repository.submitSignCode(signId, signCode)
            _uiState.update {
                it.copy(
                    isSigningIn = false,
                    signSucceeded = result.isSuccess,
                    signResult = result.getOrElse { e -> e.message ?: AppText.str(R.string.aivm_sign_failed) },
                )
            }
        }
    }

    /** 直接用 signId 签到（当已知 signId 时） */
    fun submitSignCodeDirect(signId: String, signCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, signResult = null) }
            val result = repository.submitSignCode(signId, signCode)
            _uiState.update {
                it.copy(
                    isSigningIn = false,
                    signSucceeded = result.isSuccess,
                    signResult = result.getOrElse { e -> e.message ?: AppText.str(R.string.aivm_sign_failed) },
                )
            }
        }
    }

    fun consumeSignResult() {
        _uiState.update { it.copy(signResult = null, signSucceeded = false) }
    }

    /** 扫码签到 */
    fun submitQrCode(payload: AiClassQrPayload) {
        viewModelScope.launch {
            Log.i(
                VIEWMODEL_LOG_TAG,
                "submitQrCode called, raw=${payload.rawValue.previewForLog(32)}, token=${payload.token.previewForLog()}, length=${payload.token.length}",
            )
            _uiState.update { it.copy(isSigningIn = true, signResult = null) }
            val result = repository.submitQrCode(payload)
            Log.i(
                VIEWMODEL_LOG_TAG,
                "submitQrCode finished, success=${result.isSuccess}, message=${result.getOrNull() ?: result.exceptionOrNull()?.message}",
            )
            _uiState.update {
                it.copy(
                    isSigningIn = false,
                    signSucceeded = result.isSuccess,
                    signResult = result.getOrElse { e -> e.message ?: AppText.str(R.string.aivm_scan_sign_failed) },
                )
            }
        }
    }

    fun retry() {
        load()
    }

    fun openCourseDetail(classId: String) {
        if (classId == "_working") return // 由 openWorkingRecordDetail() 已处理
        val course = _uiState.value.courses.find { it.stableId == classId }
        if (course == null) {
            _uiState.update {
                it.copy(
                    selectedCourse = null,
                    quizList = emptyList(),
                    homeworkList = emptyList(),
                    isQuizLoading = false,
                    isHomeworkLoading = false,
                    quizError = AppText.str(R.string.aivm_no_course),
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedCourse = course,
                quizList = emptyList(),
                homeworkList = emptyList(),
                isQuizLoading = true,
                microLessons = emptyList(),
                microError = null,
                isCoursewareLoading = true,
                coursewareError = null,
                coursewareList = emptyList(),
                studentGroups = emptyList(),
                isGroupLoading = true,
                groupError = null,
                selectedGroupId = null,
                groupDiscusses = emptyList(),
                isGroupDiscussLoading = false,
                groupDiscussError = null,
                liveRecords = emptyList(),
                isLiveLoading = true,
                liveError = null,
                openedCourseware = null,
                openingCourseware = null,
                isOpeningCourseware = false,
                openCoursewareError = null,
                isHomeworkLoading = true,
                quizError = null,
                homeworkError = null,
            )
        }

        // 同一个域名 OkHttp 默认只放 5 条并发，六发一起挤的时候排在后面的那几发光排队就好几秒，
        // 这就是「整体很慢、测验作业还容易超时」的主因。所以分两波：
        // 先屏幕上最先要看的三样（测验、作业、课件），跑完再放其余三样。
        viewModelScope.launch {
            val firstWave = listOf(
                launch { loadQuizList(classId, course) },
                launch { loadHomework(classId, course) },
                launch { loadCourseware(classId, course) },
            )
            firstWave.joinAll()
            val secondWave = listOf(
                launch { loadMicroLessons(classId, course) },
                launch { loadStudentGroups(classId, course) },
                launch { loadLiveRecords(classId, course) },
            )
            secondWave.joinAll()
        }
    }

    private suspend fun loadQuizList(classId: String, course: AiCourse) {
        val quizResult = withResultTimeout(AppText.str(R.string.aivm_act_quizzes), AICLASS_DETAIL_LOAD_TIMEOUT_MS) {
            repository.getQuizList(course.courseId)
        }
        _uiState.update { current ->
            if (current.selectedCourse?.stableId != classId) return@update current
            current.copy(
                quizList = quizResult.getOrDefault(emptyList()),
                isQuizLoading = false,
                quizError = quizResult.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun loadHomework(classId: String, course: AiCourse) {
        val hwResult = withResultTimeout(AppText.str(R.string.aivm_act_hw), AICLASS_DETAIL_LOAD_TIMEOUT_MS) {
            repository.getHomeworkList(course.courseId, course.teachClassId)
        }
        _uiState.update { current ->
            if (current.selectedCourse?.stableId != classId) return@update current
            current.copy(
                homeworkList = hwResult.getOrDefault(emptyList()),
                isHomeworkLoading = false,
                homeworkError = hwResult.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun loadCourseware(classId: String, course: AiCourse) {
        val cwResult = withResultTimeout(AppText.str(R.string.aivm_act_courseware), COURSEWARE_LOAD_TIMEOUT_MS) {
            repository.getCoursewareList(course.courseId, course.teachClassId)
        }
        _uiState.update { current ->
            if (current.selectedCourse?.stableId != classId) return@update current
            current.copy(
                coursewareList = cwResult.getOrDefault(emptyList()),
                isCoursewareLoading = false,
                coursewareError = cwResult.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun loadMicroLessons(classId: String, course: AiCourse) {
        val wkResult = withResultTimeout(AppText.str(R.string.aivm_act_weike), COURSEWARE_LOAD_TIMEOUT_MS) {
            repository.getMicroLessons(course.courseId)
        }
        _uiState.update { current ->
            if (current.selectedCourse?.stableId != classId) return@update current
            current.copy(
                microLessons = wkResult.getOrDefault(emptyList()),
                microError = wkResult.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun loadStudentGroups(classId: String, course: AiCourse) {
        val groupResult = withResultTimeout(AppText.str(R.string.aivm_act_group), COURSEWARE_LOAD_TIMEOUT_MS) {
            repository.getStudentGroups(course.courseId, course.teachClassId)
        }
        _uiState.update { current ->
            if (current.selectedCourse?.stableId != classId) return@update current
            current.copy(
                studentGroups = groupResult.getOrDefault(emptyList()),
                isGroupLoading = false,
                // 小组这条要区分「这门课没有小组」和「请求没成」，所以失败时把原因留着给界面
                groupError = groupResult.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun loadLiveRecords(classId: String, course: AiCourse) {
        val liveResult = withResultTimeout(
            AppText.str(R.string.aivm_act_live),
            COURSEWARE_LOAD_TIMEOUT_MS,
        ) {
            repository.getLiveRecords(course)
        }
        _uiState.update { current ->
            if (current.selectedCourse?.stableId != classId) return@update current
            current.copy(
                liveRecords = liveResult.getOrDefault(emptyList()),
                isLiveLoading = false,
                liveError = liveResult.exceptionOrNull()?.message,
            )
        }
    }

    fun retryQuiz() {
        _uiState.value.selectedCourse?.stableId?.let(::openCourseDetail)
    }

    fun retryGroup() {
        _uiState.value.selectedCourse?.stableId?.let(::openCourseDetail)
    }

    /** 直录播那一发失败时单独重跑，不用把整页六条请求再打一遍 */
    fun retryLiveRecords() {
        _uiState.value.selectedCourse?.stableId?.let(::openCourseDetail)
    }

    /**
     * 点开一节录播：去资源中心换当场签名的 mp4 直链，然后在我们自己的全屏页里放。
     * 签名带 timestamp，所以每次点开都重新换，不缓存上一次的地址。
     */
    fun openLiveRecord(record: AiLiveRecord) {
        if (record.resourceId.isBlank()) {
            _uiState.update {
                it.copy(
                    openingLiveTitle = record.title,
                    liveVideos = emptyList(),
                    selectedVideoIndex = 0,
                    isOpeningLive = false,
                    openLiveError = AppText.str(R.string.ai_live_no_resource),
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                openingLiveTitle = record.title,
                liveVideos = emptyList(),
                selectedVideoIndex = 0,
                isOpeningLive = true,
                openLiveError = null,
            )
        }
        viewModelScope.launch {
            val result = withResultTimeout(
                AppText.str(R.string.aivm_act_live_open),
                COURSEWARE_LOAD_TIMEOUT_MS,
            ) {
                repository.getLiveRecordVideos(record.resourceId)
            }
            _uiState.update { current ->
                if (current.openingLiveTitle != record.title) return@update current
                current.copy(
                    liveVideos = result.getOrDefault(emptyList()),
                    isOpeningLive = false,
                    openLiveError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    /** 有多个机位时切一个，播放器跟着换地址重开 */
    fun selectLiveVideo(index: Int) {
        _uiState.update { it.copy(selectedVideoIndex = index) }
    }

    fun closeLiveRecord() {
        _uiState.update {
            it.copy(
                liveVideos = emptyList(),
                selectedVideoIndex = 0,
                openingLiveTitle = "",
                isOpeningLive = false,
                openLiveError = null,
            )
        }
    }

    /** 点开一个小组看它的讨论列表，再点同一个组就收起 */
    fun toggleGroupDiscusses(group: AiStudentGroup) {
        val groupId = group.id
        if (_uiState.value.selectedGroupId == groupId) {
            closeGroupDiscusses()
            return
        }
        if (group.microGroupId.isBlank()) {
            // 讨论那发只认 microGroupId，没拿到就直说，不拿别的键去凑
            _uiState.update {
                it.copy(
                    selectedGroupId = groupId,
                    groupDiscusses = emptyList(),
                    isGroupDiscussLoading = false,
                    groupDiscussError = AppText.str(R.string.ai_group_no_micro_id),
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedGroupId = groupId,
                groupDiscusses = emptyList(),
                isGroupDiscussLoading = true,
                groupDiscussError = null,
            )
        }

        viewModelScope.launch {
            val discussResult = withResultTimeout(
                AppText.str(R.string.aivm_act_group_discuss),
                AICLASS_DETAIL_LOAD_TIMEOUT_MS,
            ) {
                repository.getGroupDiscusses(group.microGroupId)
            }
            _uiState.update { current ->
                if (current.selectedGroupId != groupId) return@update current
                current.copy(
                    groupDiscusses = discussResult.getOrDefault(emptyList()),
                    isGroupDiscussLoading = false,
                    groupDiscussError = discussResult.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun closeGroupDiscusses() {
        _uiState.update {
            it.copy(
                selectedGroupId = null,
                groupDiscusses = emptyList(),
                isGroupDiscussLoading = false,
                groupDiscussError = null,
            )
        }
    }

    /** 点开一个课件：换逐页渲染图，全程在我们自己界面里看，不跳安小信也不跳浏览器 */
    fun openCourseware(courseware: AiCourseware) {
        val course = _uiState.value.selectedCourse ?: return
        _uiState.update {
            it.copy(
                openingCourseware = courseware,
                openedCourseware = null,
                isOpeningCourseware = true,
                openCoursewareError = null,
            )
        }
        viewModelScope.launch {
            val detailResult = withResultTimeout(
                AppText.str(R.string.aivm_act_courseware_open),
                COURSEWARE_LOAD_TIMEOUT_MS,
            ) {
                repository.getCoursewareDetail(courseware, course.teachClassId)
            }
            _uiState.update { current ->
                // 用户中途又点了别的一条，这一发就作废，不把旧的盖到新的上面
                if (current.openingCourseware?.id != courseware.id) return@update current
                current.copy(
                    openedCourseware = detailResult.getOrNull(),
                    isOpeningCourseware = false,
                    openCoursewareError = detailResult.exceptionOrNull()?.message,
                )
            }
        }
    }

    /** 打开失败后再来一次，参数还是那条课件 */
    fun retryOpenCourseware() {
        _uiState.value.openingCourseware?.let(::openCourseware)
    }

    fun closeCourseware() {
        _uiState.update {
            it.copy(
                openedCourseware = null,
                openingCourseware = null,
                isOpeningCourseware = false,
                openCoursewareError = null,
            )
        }
    }

    /** 从"正在上课"卡片进入测验详情 */
    fun openWorkingRecordDetail() {
        val record = _uiState.value.workingRecord ?: return
        val existing = _uiState.value.courses.find { it.teachClassId == record.teachClassId }
        val base = existing ?: AiCourse(
            stableId = "",
            id = "",
            code = "",
            classId = record.teachClassId,
            courseId = record.teachClassId,
            courseRecordId = record.courseRecordId,
            courseName = record.courseItemName.ifBlank { record.courseName },
            teacherName = "",
            studentNum = 0,
            teachClassId = record.teachClassId,
            cover = "",
            termYear = "",
            term = "",
            typeName = "",
        )
        val course = base.copy(stableId = "_working") // 统一哨兵值，协程防护依赖此值
        _uiState.update {
            it.copy(selectedCourse = course, quizList = emptyList(), isQuizLoading = true, quizError = null)
        }
        viewModelScope.launch {
            val quizResult = withResultTimeout(AppText.str(R.string.aivm_act_quizzes), AICLASS_DETAIL_LOAD_TIMEOUT_MS) {
                repository.getQuizList(record.teachClassId)
            }
            _uiState.update { current ->
                if (current.selectedCourse?.stableId != "_working") return@update current
                current.copy(
                    quizList = quizResult.getOrDefault(emptyList()),
                    isQuizLoading = false,
                    quizError = quizResult.exceptionOrNull()?.message,
                )
            }
        }
    }
}

private suspend fun <T> withResultTimeout(
    action: String,
    timeoutMs: Long,
    block: suspend () -> Result<T>,
): Result<T> {
    return try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        Result.failure(Exception(AppText.str(R.string.aivm_timeout, action), e))
    }
}

private fun String.previewForLog(maxLen: Int = 16): String {
    if (isBlank()) return "<blank>"
    return if (length <= maxLen) this else take(maxLen) + "..."
}
