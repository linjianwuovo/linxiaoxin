package com.linxin.feature.checkin.ui

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.feature.checkin.data.CheckinRepository
import com.linxin.feature.checkin.domain.CheckinDay
import com.linxin.feature.checkin.domain.CheckinTask
import com.linxin.feature.checkin.domain.MonthStatics
import dagger.hilt.android.lifecycle.HiltViewModel
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
) {
    val isPreparing: Boolean
        get() = isLoading || !secondaryReady

    /** 各路数据源全空且没有报错，才算真的没内容；任何一路有数据或有错误都要渲染列表，否则错误会被空态吞掉 */
    val hasNothing: Boolean
        get() = tasks.isEmpty() && subjectTasks.isEmpty() &&
            statics == null && days.isEmpty() &&
            error == null && subjectError == null && summaryError == null
}

@HiltViewModel
class CheckinViewModel @Inject constructor(
    private val repository: CheckinRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState

    init {
        loadInitial()
        loadSecondary()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

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
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = state.currentPage + 1

            repository.getTasks(page = nextPage).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            tasks = it.tasks + list,
                            isLoadingMore = false,
                            currentPage = nextPage,
                            hasMore = list.size >= 10,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMore = false) }
                },
            )
        }
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
