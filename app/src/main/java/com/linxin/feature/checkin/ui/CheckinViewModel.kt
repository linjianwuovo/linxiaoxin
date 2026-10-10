package com.linxin.feature.checkin.ui

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.feature.checkin.data.CheckinRepository
import com.linxin.feature.holiday.data.HolidayRepository
import com.linxin.feature.holiday.domain.HolidayHistory
import com.linxin.feature.checkin.domain.CheckinDay
import com.linxin.feature.checkin.domain.CheckinTask
import com.linxin.feature.checkin.domain.MonthStatics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CheckinUiState(
    val tasks: List<CheckinTask> = emptyList(),
    /** 主题签到（taskMajorType=2），与查寝并行的另一套任务 */
    val subjectTasks: List<CheckinTask> = emptyList(),
    val statics: MonthStatics? = null,
    val days: List<CheckinDay> = emptyList(),
    val monthLabel: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val subjectError: String? = null,
    val summaryError: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    /** 串行加载的分组是否全回来了；没回齐之前整页保持 loading，避免每回一路列表就跳一下 */
    val secondaryReady: Boolean = false,
    /** 月历上点中的那天（yyyy-MM-dd）；null = 不筛选，照旧显示全部 */
    val selectedDate: String? = null,
    /** 离校/返校登记，用来回答"这天我到底算什么事" —— 6 号那种就是离校，不是查寝任务 */
    val leaves: List<HolidayHistory> = emptyList(),
    val leavesError: String? = null,
    /** 点中那天的历史查寝（collectionStudentPage）；待办端点不返回历史，只能单独拉 */
    val historyTasks: List<CheckinTask> = emptyList(),
    val historyError: String? = null,
    /** 历史主题签到（app/signin/queryPage），和查寝历史并列两路 */
    val subjectHistory: List<CheckinTask> = emptyList(),
    val subjectHistoryError: String? = null,
    /** 这批历史是为哪天拉的，用来判断"还在翻"还是"真没有" */
    val historyLoadedFor: String? = null,
    val isLoadingHistory: Boolean = false,
    val historyPage: Int = 1,
    val historyHasMore: Boolean = true,
) {
    val isPreparing: Boolean
        get() = isLoading || !secondaryReady

    /** 各路数据源全空且没有报错，才算真的没内容；任何一路有数据或有错误都要渲染列表，否则错误会被空态吞掉 */
    val hasNothing: Boolean
        get() = tasks.isEmpty() && subjectTasks.isEmpty() &&
            statics == null && days.isEmpty() &&
            error == null && subjectError == null && summaryError == null

    /**
     * 没点日子：显示待办两路。点中某天：待办里那天的 + 那天的历史，按 taskDateId 去重
     * （当天没签完的任务两边都会出现）。
     */
    val visibleTasks: List<CheckinTask>
        get() {
            val date = selectedDate ?: return tasks
            return (filterBySelected(tasks) + historyTasks).distinctBy { it.taskDateId }
        }

    val visibleSubjectTasks: List<CheckinTask>
        get() {
            val date = selectedDate ?: return subjectTasks
            return (filterBySelected(subjectTasks) + subjectHistory).distinctBy { it.taskDateId }
        }

    /** 这天到底还有没有得翻：点了日子就走历史翻页，没点才翻待办 */
    val canLoadMore: Boolean
        get() = if (selectedDate != null) historyHasMore else hasMore

    /** 这天落在哪些离校区间里（离校日 <= 当天 < 返校日） */
    val visibleLeaves: List<HolidayHistory>
        get() {
            val day = selectedDate ?: return emptyList()
            return leaves.filter { it.covers(day) }
        }

    /** 这天到底有没有东西：历史两路 + 当天待办 + 离校区间，任一有就不算空 */
    val hasSelectedDay: Boolean
        get() = selectedDate != null &&
            (visibleTasks.isNotEmpty() || visibleSubjectTasks.isNotEmpty() || visibleLeaves.isNotEmpty())

    private fun filterBySelected(list: List<CheckinTask>): List<CheckinTask> {
        val date = selectedDate ?: return list
        return list.filter { it.taskDate == date }
    }
}

@HiltViewModel
class CheckinViewModel @Inject constructor(
    private val repository: CheckinRepository,
    private val holidayRepository: HolidayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState

    private var findDayJob: Job? = null

    init {
        loadInitial()
        loadSecondary()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, selectedDate = null) }

            withNetworkRetry { repository.getTasks(page = 1) }.fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            tasks = list,
                            isLoading = false,
                            currentPage = 1,
                            hasMore = list.size >= 10,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "加载失败")
                    }
                },
            )
        }
    }

    /**
     * 其余分组串行加载，不要和主列表一起并发。
     * 真机抓包：fdygl 每条响应都带 Connection: close，服务器不复用连接，进页面一次打 5 个
     * 请求就是同时做 5 次全新 TLS 握手，偶发被掐就报"网络异常"（手点重试只发一个所以必成）。
     * 节假日登记 / 历史登记已挪到独立的「节假日离返校」页，各自一次请求。
     */
    private fun loadSecondary() {
        viewModelScope.launch {
            _uiState.update { it.copy(secondaryReady = false) }
            try {
                loadSummary()
                loadSubject()
                loadLeaves()
            } finally {
                // 任何一路出意外都不能把整页卡在 loading 上
                _uiState.update { it.copy(secondaryReady = true) }
            }
        }
    }

    private suspend fun loadSubject() {
        _uiState.update { it.copy(subjectError = null) }
        withNetworkRetry { repository.getSubjectTasks(page = 1) }.fold(
            onSuccess = { list ->
                _uiState.update { it.copy(subjectTasks = list, subjectError = null) }
            },
            onFailure = { e ->
                _uiState.update { it.copy(subjectError = e.message ?: "主题签到加载失败") }
            },
        )
    }

    /**
     * 离校/返校登记。只取第一页（历史登记按假期给，一页 10 条足够覆盖一个学期），
     * 用来在月历点某天时把"那天其实在离校"也说出来。
     */
    private suspend fun loadLeaves() {
        _uiState.update { it.copy(leavesError = null) }
        withNetworkRetry { holidayRepository.getHistoryList(page = 1) }.fold(
            onSuccess = { list ->
                _uiState.update { it.copy(leaves = list, leavesError = null) }
            },
            onFailure = { e ->
                _uiState.update { it.copy(leavesError = e.message ?: "获取离校登记失败") }
            },
        )
    }

    /** 本月统计 + 签到日历，两者都按当前自然月取 */
    private suspend fun loadSummary() {
        _uiState.update { it.copy(summaryError = null) }
        val today = LocalDate.now()
        val month = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val firstDay = today.withDayOfMonth(1)
        val lastDay = today.withDayOfMonth(today.lengthOfMonth())

        val staticsResult = withNetworkRetry { repository.getMonthStatics(month) }
        val daysResult = withNetworkRetry {
            repository.getDateCheck(startTime = firstDay.toString(), endTime = lastDay.toString())
        }

        _uiState.update { state ->
            state.copy(
                monthLabel = month,
                statics = staticsResult.getOrNull() ?: state.statics,
                days = daysResult.getOrNull() ?: state.days,
                summaryError = staticsResult.exceptionOrNull()?.message
                    ?: daysResult.exceptionOrNull()?.message,
            )
        }
    }

    /**
     * Repository 把底层异常保留在 cause 里，所以能只针对连接类失败重试，
     * 服务端业务错误（非法访问 / 500）不重。
     * 必须带退避：紧跟着的第二次请求往往撞上同一个坏连接，真机表现为"自动重试没用，手点才有用"。
     */
    private suspend fun <T> withNetworkRetry(block: suspend () -> Result<T>): Result<T> {
        var last = block()
        for (backoff in RETRY_BACKOFF_MS) {
            if (last.exceptionOrNull()?.cause !is IOException) return last
            delay(backoff)
            last = block()
        }
        return last
    }

    private companion object {
        val RETRY_BACKOFF_MS = longArrayOf(600L, 1800L)

        /** pageStudentSignIn 每页 10 条，和 loadInitial 里的判据一致 */
        const val PAGE_SIZE = 10

        /** 历史两路每页 20 条，跟官方 H5 的历史 tab 一致 */
        const val HISTORY_PAGE_SIZE = 20
    }

    fun loadMore() {
        viewModelScope.launch {
            if (_uiState.value.selectedDate != null) fetchNextHistoryPage() else fetchNextPage()
        }
    }

    /** 往后翻一页待办；返回是否真的翻到了新数据 */
    private suspend fun fetchNextPage(): Boolean {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return false

        _uiState.update { it.copy(isLoadingMore = true) }
        val nextPage = state.currentPage + 1
        val list = withNetworkRetry { repository.getTasks(page = nextPage) }.getOrNull()
        if (list == null) {
            // 失败只停这一次，不把 hasMore 判死，用户再滚还能试
            _uiState.update { it.copy(isLoadingMore = false) }
            return false
        }
        _uiState.update {
            it.copy(
                tasks = it.tasks + list,
                isLoadingMore = false,
                currentPage = nextPage,
                hasMore = list.size >= PAGE_SIZE,
            )
        }
        return true
    }

    /**
     * 点月历上的某天：待办端点（pageStudentSignIn）只给当天，历史得按天单独拉，
     * 所以这里改走 collectionStudentPage + signin/queryPage 两路，串行打
     * （理由同 loadSecondary：fdygl 每条响应都 Connection: close，并发容易整路挂掉）。
     */
    fun selectDate(date: String?) {
        findDayJob?.cancel()
        _uiState.update {
            it.copy(
                selectedDate = date,
                historyTasks = emptyList(),
                subjectHistory = emptyList(),
                historyError = null,
                subjectHistoryError = null,
                historyLoadedFor = null,
                historyPage = 1,
                historyHasMore = true,
                isLoadingHistory = date != null,
            )
        }
        if (date == null) return
        findDayJob = viewModelScope.launch { loadHistoryFor(date) }
    }

    private suspend fun loadHistoryFor(date: String) {
        _uiState.update { it.copy(isLoadingHistory = true, historyError = null, subjectHistoryError = null) }
        val dorm = withNetworkRetry { repository.getHistoryTasks(date) }
        _uiState.update {
            it.copy(
                historyTasks = dorm.getOrNull().orEmpty(),
                historyError = dorm.exceptionOrNull()?.message,
            )
        }
        val subject = withNetworkRetry { repository.getSubjectHistoryTasks(date) }
        _uiState.update {
            it.copy(
                subjectHistory = subject.getOrNull().orEmpty(),
                subjectHistoryError = subject.exceptionOrNull()?.message,
                historyLoadedFor = date,
                isLoadingHistory = false,
                historyHasMore = (dorm.getOrNull()?.size ?: 0) >= HISTORY_PAGE_SIZE ||
                    (subject.getOrNull()?.size ?: 0) >= HISTORY_PAGE_SIZE,
            )
        }
    }

    /** 翻了这天第一页还不够就接着翻：两路一起往后走，页码共用一个 */
    private suspend fun fetchNextHistoryPage() {
        val state = _uiState.value
        val date = state.selectedDate ?: return
        if (state.isLoadingHistory || !state.historyHasMore) return

        _uiState.update { it.copy(isLoadingHistory = true) }
        val nextPage = state.historyPage + 1
        val dorm = withNetworkRetry { repository.getHistoryTasks(date, nextPage) }
        val subject = withNetworkRetry { repository.getSubjectHistoryTasks(date, nextPage) }
        if (dorm.isFailure && subject.isFailure) {
            // 两路都失败才停，且只停这一次，用户再滚还能试
            _uiState.update { it.copy(isLoadingHistory = false) }
            return
        }
        val dormList = dorm.getOrNull().orEmpty()
        val subjectList = subject.getOrNull().orEmpty()
        _uiState.update {
            it.copy(
                historyTasks = it.historyTasks + dormList,
                subjectHistory = it.subjectHistory + subjectList,
                historyError = dorm.exceptionOrNull()?.message ?: it.historyError,
                subjectHistoryError = subject.exceptionOrNull()?.message ?: it.subjectHistoryError,
                historyPage = nextPage,
                historyHasMore = dormList.size >= HISTORY_PAGE_SIZE || subjectList.size >= HISTORY_PAGE_SIZE,
                isLoadingHistory = false,
            )
        }
    }

    fun clearDateFilter() = selectDate(null)

    /** 历史两路的重试：只重拉选中的这天，不把整页（含月历、统计）重来 */
    fun retryHistory() {
        val date = _uiState.value.selectedDate ?: return
        findDayJob?.cancel()
        findDayJob = viewModelScope.launch { loadHistoryFor(date) }
    }

    fun retry() {
        loadInitial()
        loadSecondary()
    }

    fun retrySubject() {
        viewModelScope.launch { loadSubject() }
    }

    fun retrySummary() {
        viewModelScope.launch { loadSummary() }
    }

    fun refresh() {
        loadInitial()
        loadSecondary()
    }
}

/**
 * 离校区间是否覆盖某一天：`startDate` 是离校当天，`returnStartDate` 是返校当天，
 * 所以按"离校日 <= 当天 < 返校日"算。两个字段都是 "yyyy-MM-dd HH:mm:ss" 或纯日期，
 * 取空格前的日期部分再比，字典序即时间序。
 */
private fun HolidayHistory.covers(day: String): Boolean {
    val from = startDate.trim().substringBefore(' ')
    val to = returnStartDate.trim().substringBefore(' ')
    if (from.length != 10 || day.length != 10) return false
    return day >= from && (to.length != 10 || day < to)
}
