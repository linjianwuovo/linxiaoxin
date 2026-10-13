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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxDialogConfirmTone
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.feature.leave.data.LeaveDetail
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
    /** 当前点开在看的那一条；null = 没在看详情 */
    val openedItem: LeaveItem? = null,
    val detail: LeaveDetail? = null,
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
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

    /** 点开一条请假：看它当时填了什么、审批走到哪一步 */
    fun openDetail(item: LeaveItem) {
        _uiState.update {
            it.copy(
                openedItem = item,
                detail = null,
                isLoadingDetail = true,
                detailError = null,
            )
        }
        viewModelScope.launch {
            val result = repository.formDetail(item.id, item.formType, item.ruTaskNodeId)
            _uiState.update { current ->
                if (current.openedItem?.id != item.id) return@update current
                current.copy(
                    detail = result.getOrNull(),
                    isLoadingDetail = false,
                    detailError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun retryDetail() {
        _uiState.value.openedItem?.let(::openDetail)
    }

    fun closeDetail() {
        _uiState.update {
            it.copy(openedItem = null, detail = null, isLoadingDetail = false, detailError = null)
        }
    }

    fun withdraw(executionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.withdraw(executionId).fold(
                onSuccess = { load() },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }
}

@Composable
fun LeaveScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
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
            LxButton(
                text = stringResource(R.string.leave_new),
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )
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
                        LeaveCard(
                            item = item,
                            onWithdraw = { viewModel.withdraw(item.id) },
                            onClick = { viewModel.openDetail(item) },
                        )
                    }
                }
            }
        }
    }

    // 点开一条请假就在这一页上层看详情，不推新页面
    if (uiState.openedItem != null) {
        LeaveDetailDialog(
            item = uiState.openedItem!!,
            detail = uiState.detail,
            isLoading = uiState.isLoadingDetail,
            error = uiState.detailError,
            onRetry = viewModel::retryDetail,
            onWithdraw = { viewModel.withdraw(uiState.openedItem!!.id) },
            onDismiss = viewModel::closeDetail,
        )
    }
}

/** 详情页里的一节标题 */
@Composable
private fun LeaveDetailSectionTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = LxInkMuted,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun LeaveDetailDialog(
    item: LeaveItem,
    detail: LeaveDetail?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onWithdraw: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmWithdraw by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Scaffold(
            containerColor = MiuixTheme.colorScheme.background,
            topBar = {
                LxTopBar(title = stringResource(R.string.leave_detail_title), onBack = onDismiss)
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "detail_head") {
                    LxCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 标题不再截断：学校把类型、日期、时长全拼在这一串里，截了就等于没说
                            Text(
                                text = item.title.ifBlank { stringResource(R.string.leave_untitled) },
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold,
                                color = LxInk,
                            )
                            val meta = listOf(item.status, item.time).filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = meta,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = LxSandDeep,
                                )
                            }
                        }
                    }
                }

                when {
                    isLoading -> item(key = "detail_loading") {
                        LxLoading(modifier = Modifier.fillMaxWidth().height(160.dp))
                    }

                    detail == null -> item(key = "detail_error") {
                        LxError(message = error ?: stringResource(R.string.leave_detail_failed), onRetry = onRetry)
                    }

                    else -> {
                        item(key = "detail_fields_title") {
                            LeaveDetailSectionTitle(stringResource(R.string.leave_detail_fields))
                        }
                        if (detail.fields.isEmpty()) {
                            item(key = "detail_fields_empty") {
                                LxCard {
                                    Text(
                                        text = stringResource(
                                            if (detail.fieldsUnavailable) {
                                                R.string.leave_detail_fields_unavailable
                                            } else {
                                                R.string.leave_detail_fields_empty
                                            },
                                        ),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }
                        } else {
                            items(detail.fields, key = { "f_" + it.label }) { field ->
                                LxCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    ) {
                                        Text(
                                            text = field.label,
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = LxInkMuted,
                                            modifier = Modifier.width(110.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = field.value,
                                            style = MiuixTheme.textStyles.body2,
                                            color = LxInk,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "detail_flow_title") {
                            LeaveDetailSectionTitle(stringResource(R.string.leave_detail_flow))
                        }
                        if (detail.steps.isEmpty()) {
                            item(key = "detail_flow_empty") {
                                LxCard {
                                    Text(
                                        text = stringResource(
                                            if (detail.stepsUnavailable) {
                                                R.string.leave_detail_flow_unavailable
                                            } else {
                                                R.string.leave_detail_flow_empty
                                            },
                                        ),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }
                        } else {
                            items(detail.steps, key = { "s_" + it.hashCode() }) { step ->
                                LxCard {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        val head = listOf(step.who, step.action, step.status)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                        Text(
                                            text = head.ifBlank { stringResource(R.string.leave_step_no_info) },
                                            style = MiuixTheme.textStyles.body2,
                                            fontWeight = FontWeight.Medium,
                                            color = LxInk,
                                        )
                                        if (step.time.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = step.time,
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = LxInkMuted,
                                            )
                                        }
                                        if (step.remark.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = step.remark,
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = LxInk,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (item.withdrawable) {
                    item(key = "detail_withdraw") {
                        LxOutlinedButton(
                            text = stringResource(R.string.leave_withdraw),
                            onClick = { confirmWithdraw = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
    if (confirmWithdraw) {
        LxDialog(
            title = stringResource(R.string.leave_withdraw),
            message = stringResource(R.string.leave_withdraw_confirm),
            confirmText = stringResource(R.string.action_confirm),
            confirmTone = LxDialogConfirmTone.Destructive,
            onConfirm = {
                confirmWithdraw = false
                onDismiss()
                onWithdraw()
            },
            onDismissRequest = { confirmWithdraw = false },
        )
    }
}

@Composable
private fun LeaveCard(
    item: LeaveItem,
    onWithdraw: () -> Unit,
    onClick: () -> Unit,
) {
    var confirmWithdraw by remember { mutableStateOf(false) }
    LxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title.ifBlank { stringResource(R.string.leave_untitled) },
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
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
            if (item.withdrawable) {
                Spacer(modifier = Modifier.height(8.dp))
                LxOutlinedButton(
                    text = stringResource(R.string.leave_withdraw),
                    onClick = { confirmWithdraw = true },
                )
            }
        }
    }
    if (confirmWithdraw) {
        LxDialog(
            title = stringResource(R.string.leave_withdraw),
            message = stringResource(R.string.leave_withdraw_confirm),
            confirmText = stringResource(R.string.action_confirm),
            confirmTone = LxDialogConfirmTone.Destructive,
            onConfirm = {
                confirmWithdraw = false
                onWithdraw()
            },
            onDismissRequest = { confirmWithdraw = false },
        )
    }
}
