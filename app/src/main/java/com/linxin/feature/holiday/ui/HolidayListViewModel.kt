package com.linxin.feature.holiday.ui

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.feature.holiday.data.HolidayRepository
import com.linxin.feature.holiday.domain.HolidayHistory
import com.linxin.feature.holiday.domain.HolidayTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HolidayListUiState(
    val tasks: List<HolidayTask> = emptyList(),
    val history: List<HolidayHistory> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val historyError: String? = null,
)

@HiltViewModel
class HolidayListViewModel @Inject constructor(
    private val repository: HolidayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HolidayListUiState())
    val uiState: StateFlow<HolidayListUiState> = _uiState

    init {
        loadAll()
    }

    /**
     * 两个标签的数据各一次请求，串行发：fdygl 每条响应都带 Connection: close，
     * 连接不复用，并发就是同时做多次全新 TLS 握手，偶发被掐。
     */
    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, historyError = null) }

            repository.getRegistrationList(page = 1).fold(
                onSuccess = { list -> _uiState.update { it.copy(tasks = list, error = null) } },
                onFailure = { e -> _uiState.update { it.copy(error = e.message ?: "节假日加载失败") } },
            )

            repository.getHistoryList(page = 1).fold(
                onSuccess = { list -> _uiState.update { it.copy(history = list, historyError = null) } },
                onFailure = { e ->
                    _uiState.update { it.copy(historyError = e.message ?: "历史登记加载失败") }
                },
            )

            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
