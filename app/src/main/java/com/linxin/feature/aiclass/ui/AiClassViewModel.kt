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
import com.linxin.feature.aiclass.domain.AiQuiz
import com.linxin.feature.aiclass.domain.AiClassQrPayload
import com.linxin.feature.aiclass.domain.AiWorkingRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val VIEWMODEL_LOG_TAG = "AiClassVM"
private const val AICLASS_INITIAL_LOAD_TIMEOUT_MS = 18_000L
private const val AICLASS_OPTIONAL_LOAD_TIMEOUT_MS = 8_000L
private const val AICLASS_DETAIL_LOAD_TIMEOUT_MS = 12_000L

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
        val working = _uiState.value.workingRecord ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, signResult = null) }

            // 先查签到信息获取 signId
            val signInfoResult = repository.getSignInInfo(
                teachClassId = working.teachClassId,
                courseRecordId = working.courseRecordId,
            )
            val signInfo = signInfoResult.getOrNull()

            if (signInfo == null || !signInfo.hasActiveSign) {
                _uiState.update {
                    it.copy(
                        isSigningIn = false,
                        signSucceeded = false,
                        signResult = signInfoResult.exceptionOrNull()?.message ?: AppText.str(R.string.aivm_no_signin),
                    )
                }
                return@launch
            }

            // 提交数字码
            val result = repository.submitSignCode(signInfo.signId, signCode)
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
                isHomeworkLoading = true,
                quizError = null,
                homeworkError = null,
            )
        }

        viewModelScope.launch {
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

        viewModelScope.launch {
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
    }

    fun retryQuiz() {
        _uiState.value.selectedCourse?.stableId?.let(::openCourseDetail)
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
