package com.linxin.feature.leave.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import com.linxin.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.feature.leave.data.LeaveItem
import com.linxin.feature.leave.data.LeaveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.linxin.core.designsystem.component.LxFilterChip
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAB_APPLY = 0
private const val TAB_TODO = 1

data class LeaveUiState(
    val applies: List<LeaveItem> = emptyList(),
    val todos: List<LeaveItem> = emptyList(),
    val tab: Int = TAB_APPLY,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val shown: List<LeaveItem>
        get() = if (tab == TAB_TODO) todos else applies
}

/**
 * 请假只读页。两个列表**一起拉、一起显示**：抓包确认它们是各自独立的接口，
 * 但界面不该一路加载一路蹦，所以等两个都回来才收 loading。
 */
@HiltViewModel
class LeaveViewModel @Inject constructor(
    private val repository: LeaveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveUiState())
    val uiState: StateFlow<LeaveUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val applies = async { repository.myApplies() }
            val todos = async { repository.todoTasks() }
            val applyResult = applies.await()
            val todoResult = todos.await()
            val failure = applyResult.exceptionOrNull() ?: todoResult.exceptionOrNull()
            _uiState.update {
                it.copy(
                    applies = applyResult.getOrNull()?.items ?: emptyList(),
                    todos = todoResult.getOrNull()?.items ?: emptyList(),
                    isLoading = false,
                    // 没有异常就是没有异常：空列表是正常状态，不能当成失败
                    error = failure?.message,
                )
            }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(tab = tab) }
    }
}

@Composable
fun LeaveScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LeaveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_leave), onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LxFilterChip(
                    label = stringResource(R.string.leave_tab_apply),
                    selected = uiState.tab == TAB_APPLY,
                    onClick = { viewModel.selectTab(TAB_APPLY) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                LxFilterChip(
                    label = stringResource(R.string.leave_tab_todo),
                    selected = uiState.tab == TAB_TODO,
                    onClick = { viewModel.selectTab(TAB_TODO) },
                )
            }
            when {
                uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize())
                uiState.error != null && uiState.shown.isEmpty() -> LxError(
                    message = uiState.error!!,
                    onRetry = viewModel::load,
                )
                uiState.shown.isEmpty() -> LxEmpty(
                    message = stringResource(
                        if (uiState.tab == TAB_TODO) R.string.leave_empty_todo else R.string.leave_empty_apply,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.shown, key = { it.id }) { item ->
                        LeaveCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveCard(item: LeaveItem) {
    LxCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title.ifBlank { stringResource(R.string.leave_untitled) },
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (item.status.isNotBlank()) {
                    Text(
                        text = item.status,
                        style = MiuixTheme.textStyles.footnote2,
                        color = LxSandDeep,
                    )
                }
            }
            if (item.time.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.time,
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                )
            }
        }
    }
}
