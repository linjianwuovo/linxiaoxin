package com.linxin.feature.running.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.feature.running.data.RunningRepository
import com.linxin.feature.running.domain.MaintainPage
import com.linxin.feature.running.domain.RunDetail
import com.linxin.feature.running.domain.RunRecordRow
import com.linxin.feature.running.domain.RunTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** 列表那一屏的查询条件：月份 + 页签（页签号就是官方 queryType 的序号） */
data class SportRecordUiState(
    val month: YearMonth = YearMonth.now(),
    val tab: Int = TAB_ALL,
    val rows: List<RunRecordRow> = emptyList(),
    val totals: RunTotals? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val queryDate: String get() = month.format(MONTH_FORMAT)
    val monthLabel: String get() = "${month.year}年${month.monthValue}月"

    companion object {
        const val TAB_ALL = 0
        const val TAB_SELF = 1
        const val TAB_EXTRA = 2
        const val TAB_GROUP = 3
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.CHINA)
    }
}

@HiltViewModel
class SportRecordViewModel @Inject constructor(
    private val repository: RunningRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SportRecordUiState())
    val uiState: StateFlow<SportRecordUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val month = _uiState.value.month
        val tab = _uiState.value.tab
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val pageResult = repository.runRecords(queryDate = month.format(SportRecordUiState.MONTH_FORMAT), queryType = tab)
            val totalsResult = repository.runTotals()
            _uiState.update { state ->
                if (state.month != month || state.tab != tab) {
                    state // 条件已经变了，这一份结果作废
                } else {
                    state.copy(
                        rows = pageResult.getOrNull()?.rows ?: state.rows,
                        totals = totalsResult.getOrNull() ?: state.totals,
                        isLoading = false,
                        error = pageResult.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    fun selectTab(tab: Int) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab) }
        reload()
    }

    /** [delta] 为 -1 往前一个月，+1 往后一个月；不许翻到未来 */
    fun shiftMonth(delta: Int) {
        val next = _uiState.value.month.plusMonths(delta.toLong())
        if (next.isAfter(YearMonth.now())) return
        _uiState.update { it.copy(month = next) }
        reload()
    }
}

/** 运动明细那一屏 */
@HiltViewModel
class RunDetailViewModel @Inject constructor(
    private val repository: RunningRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val checkId: String = savedStateHandle.get<String>("checkId").orEmpty()

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    data class DetailUiState(
        val detail: RunDetail? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    init {
        reload()
    }

    fun reload() {
        if (checkId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "缺少记录编号") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.runDetail(checkId).fold(
                onSuccess = { detail ->
                    _uiState.update { it.copy(detail = detail, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }
}

/** 考勤维护记录那一屏 */
@HiltViewModel
class MaintainRecordViewModel @Inject constructor(
    private val repository: RunningRepository,
) : ViewModel() {

    data class MaintainUiState(
        val month: YearMonth = YearMonth.now(),
        val tab: Int = 0,
        val auditState: String = "",
        val page: MaintainPage? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
    ) {
        val monthLabel: String get() = "${month.year}年${month.monthValue}月"
    }

    private val _uiState = MutableStateFlow(MaintainUiState())
    val uiState: StateFlow<MaintainUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.maintainRecords(
                queryDate = state.month.format(SportRecordUiState.MONTH_FORMAT),
                queryType = state.tab,
                auditState = state.auditState,
            )
            _uiState.update { current ->
                current.copy(
                    page = result.getOrNull() ?: current.page,
                    isLoading = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun selectTab(tab: Int) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab) }
        reload()
    }

    fun selectAudit(auditState: String) {
        if (_uiState.value.auditState == auditState) return
        _uiState.update { it.copy(auditState = auditState) }
        reload()
    }

    fun shiftMonth(delta: Int) {
        val next = _uiState.value.month.plusMonths(delta.toLong())
        if (next.isAfter(YearMonth.now())) return
        _uiState.update { it.copy(month = next) }
        reload()
    }
}

/** 官方那屏只翻到当月为止 */
internal fun todayMonth(): YearMonth = LocalDate.now().atStartOfDay().toLocalDate().let { YearMonth.of(it.year, it.month) }
