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
import com.linxin.core.designsystem.component.LxTextField
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.feature.leave.data.LeaveDetail
import com.linxin.feature.leave.data.LeaveItem
import com.linxin.feature.leave.data.LeaveRepository
import com.linxin.feature.leave.data.LeaveXjInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.linxin.core.designsystem.component.LxFilterChip
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 四栏，跟学校那个「待办事务」的四个视图一一对应：
 * 待处理发 `approvedType=1`、待阅发 `approvedType=2`（同一个口换 tab 号），已办和我的请求各有自己的口。
 * 顺序也照它的卡片排：待办 / 待阅 / 已办事项 / 我的请求。
 */
private const val TAB_TODO = 0
private const val TAB_READ = 1
private const val TAB_DONE = 2
private const val TAB_APPLY = 3

data class LeaveUiState(
    /** 每栏一份列表；没拉过的是 null，切过去才拉（省得开页就四发一起打） */
    val lists: Map<Int, List<LeaveItem>> = emptyMap(),
    val tab: Int = TAB_TODO,
    val loadingTabs: Set<Int> = emptySet(),
    val errors: Map<Int, String> = emptyMap(),
    /** 卡片上的角标数；学校那套键名没样本，取不到就是空表 */
    val counts: Map<String, Int> = emptyMap(),
    /** 当前点开在看的那一条；null = 没在看详情 */
    val openedItem: LeaveItem? = null,
    val detail: LeaveDetail? = null,
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
    /** 有写操作在飞：按钮全灭，防止连点两下变成审两次 */
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    /** 销假弹层：点「销假」先去问学校超没超假（`getXjxx.do`），这三行全是它给的，我们不自己算 */
    val xjInfo: LeaveXjInfo? = null,
    val isLoadingXj: Boolean = false,
    val xjError: String? = null,
) {
    val shown: List<LeaveItem> get() = lists[tab].orEmpty()
    val isLoading: Boolean get() = tab in loadingTabs
    val error: String? get() = errors[tab]
}

/**
 * 待办事务只读页。四栏各自独立要不要拉：进来先拉「待办」+ 角标，
 * 别的栏切到了再拉，来回切不重复打；空列表是正常状态，只有真抛异常才算错。
 */
@HiltViewModel
class LeaveViewModel @Inject constructor(
    private val repository: LeaveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveUiState())
    val uiState: StateFlow<LeaveUiState> = _uiState.asStateFlow()

    init {
        loadCounts()
        load(TAB_TODO)
    }

    /** 角标是锦上添花：取不到就当没有，不弹错也不挡列表 */
    private fun loadCounts() {
        viewModelScope.launch {
            val result = repository.todoCounts()
            _uiState.update { it.copy(counts = result.getOrNull() ?: emptyMap()) }
        }
    }

    fun load(tab: Int = _uiState.value.tab) {
        _uiState.update {
            it.copy(loadingTabs = it.loadingTabs + tab, errors = it.errors - tab)
        }
        viewModelScope.launch {
            val result = when (tab) {
                TAB_READ -> repository.readTasks()
                TAB_DONE -> repository.doneTasks()
                TAB_APPLY -> repository.myApplies()
                else -> repository.todoTasks()
            }
            _uiState.update { current ->
                current.copy(
                    lists = current.lists + (tab to (result.getOrNull()?.items ?: emptyList())),
                    loadingTabs = current.loadingTabs - tab,
                    errors = result.exceptionOrNull()?.message
                        ?.let { message -> current.errors + (tab to message) }
                        ?: (current.errors - tab),
                )
            }
        }
    }

    fun refresh() {
        load(_uiState.value.tab)
        loadCounts()
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(tab = tab) }
        val current = _uiState.value
        if (current.lists[tab] == null && tab !in current.loadingTabs) {
            load(tab)
        }
    }

    /** 点开一条请假：看它当时填了什么、审批走到哪一步 */
    fun openDetail(item: LeaveItem) {
        _uiState.update {
            it.copy(
                openedItem = item,
                detail = null,
                isLoadingDetail = true,
                detailError = null,
                xjInfo = null,
                isLoadingXj = false,
                xjError = null,
                // 上一条的失败原因不许跟到这一条的弹层里
                submitError = null,
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
            it.copy(
                openedItem = null,
                detail = null,
                isLoadingDetail = false,
                detailError = null,
                xjInfo = null,
                isLoadingXj = false,
                xjError = null,
                submitError = null,
            )
        }
    }

    /**
     * 开弹层前先去问「超没超假」。这是只读，挂了可以在弹层里重试；
     * 没拿到之前【确认】不会发写请求 —— 免得显示着"读不到"还把人那条销假发出去了。
     */
    fun askCancelLeave() {
        val item = _uiState.value.openedItem ?: return
        if (_uiState.value.isLoadingXj) return
        _uiState.update { it.copy(xjInfo = null, isLoadingXj = true, xjError = null) }
        viewModelScope.launch {
            val result = repository.xjInfo(item.id)
            _uiState.update { current ->
                if (current.openedItem?.id != item.id) return@update current
                current.copy(
                    xjInfo = result.getOrNull(),
                    isLoadingXj = false,
                    xjError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun cancelLeave(item: LeaveItem, dataJson: String) {
        submit("销假") { repository.cancelLeave(item.id, item.ruTaskNodeId, dataJson) }
    }

    fun withdraw(executionId: String) {
        viewModelScope.launch {
            val tab = _uiState.value.tab
            repository.withdraw(executionId).fold(
                onSuccess = { refresh() },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errors = it.errors + (tab to (error.message ?: "撤回失败")))
                    }
                },
            )
        }
    }

    /**
     * 四个写操作都走这里：转圈时不重复发，失败只把原因摆出来**绝不自动重试**
     * （这类请求重发一次可能就是多审一次）。成功就关掉详情、重新拉这一栏和角标。
     */
    private fun submit(label: String, block: suspend () -> Result<Unit>) {
        if (_uiState.value.isSubmitting) return
        val tab = _uiState.value.tab
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = block()
            _uiState.update { current ->
                current.copy(
                    isSubmitting = false,
                    submitError = result.exceptionOrNull()?.message?.let { "$label：$it" },
                    errors = result.exceptionOrNull()?.message
                        ?.let { message -> current.errors + (tab to message) }
                        ?: (current.errors - tab),
                )
            }
            if (result.isSuccess) {
                closeDetail()
                load(tab)
                loadCounts()
            }
        }
    }

    fun approve(item: LeaveItem, dataJson: String, remark: String) {
        submit("同意") { repository.approve(item.id, item.ruTaskNodeId, dataJson, remark) }
    }

    fun reject(item: LeaveItem, dataJson: String, remark: String) {
        submit("驳回") { repository.reject(item.id, item.ruTaskNodeId, dataJson, remark) }
    }

    fun delete(item: LeaveItem) {
        submit("删除") { repository.deleteExecution(item.id) }
    }

    fun markAllRead() {
        val ids = _uiState.value.shown.map { it.ruTaskNodeId }
        submit("批量已阅") { repository.markRead(ids) }
    }
}

@Composable
fun LeaveScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    // 不给默认值：默认空实现会让「点了没反应」这种问题在屏幕上看不出来，上一轮踩过
    onOpenCheckin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LeaveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmMarkAllRead by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_leave), onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 四栏跟学校那张卡的四个入口同名同序，横着放得下，窄屏能滑
                val tabs = listOf(
                    TAB_TODO to stringResource(R.string.leave_tab_todo),
                    TAB_READ to stringResource(R.string.leave_tab_read),
                    TAB_DONE to stringResource(R.string.leave_tab_done),
                    TAB_APPLY to stringResource(R.string.leave_tab_apply),
                )
                tabs.forEachIndexed { index, (tabId, label) ->
                    if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                    LxFilterChip(
                        label = label,
                        selected = uiState.tab == tabId,
                        onClick = { viewModel.selectTab(tabId) },
                    )
                }
            }
            // 待阅那一栏的批量动作：一次把列出来的都标成已阅，弹层里报数让他看清要动几条
            if (uiState.tab == TAB_READ && uiState.shown.isNotEmpty()) {
                LxOutlinedButton(
                    text = stringResource(R.string.leave_mark_all_read, uiState.shown.size),
                    enabled = !uiState.isSubmitting,
                    onClick = { confirmMarkAllRead = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            // 「发起」只属于「我的请求」那一栏，别的栏上它没有意义
            if (uiState.tab == TAB_APPLY) {
                LxButton(
                    text = stringResource(R.string.leave_new),
                    onClick = onCreate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            when {
                uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize())
                uiState.error != null && uiState.shown.isEmpty() -> LxError(
                    message = uiState.error!!,
                    onRetry = { viewModel.load() },
                )
                uiState.shown.isEmpty() -> LxEmpty(
                    message = stringResource(
                        when (uiState.tab) {
                            TAB_READ -> R.string.leave_empty_read
                            TAB_DONE -> R.string.leave_empty_done
                            TAB_APPLY -> R.string.leave_empty_apply
                            else -> R.string.leave_empty_todo
                        },
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.shown, key = { it.id }) { item ->
                        // 查寝类待办不进流程单详情（学校那边也是跳查寝页），直接送过去
                        LeaveCard(
                            item = item,
                            onWithdraw = { viewModel.withdraw(item.id) },
                            onClick = {
                                if (item.isCheckinTodo) onOpenCheckin() else viewModel.openDetail(item)
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmMarkAllRead) {
        LxDialog(
            title = stringResource(R.string.leave_mark_all_read, uiState.shown.size),
            message = stringResource(R.string.leave_mark_all_read_confirm),
            confirmText = stringResource(R.string.action_confirm),
            onConfirm = {
                confirmMarkAllRead = false
                viewModel.markAllRead()
            },
            onDismiss = { confirmMarkAllRead = false },
            onDismissRequest = { confirmMarkAllRead = false },
        )
    }

    // 点开一条请假就在这一页上层看详情，不推新页面
    if (uiState.openedItem != null) {
        val opened = uiState.openedItem!!
        LeaveDetailDialog(
            item = opened,
            detail = uiState.detail,
            isLoading = uiState.isLoadingDetail,
            error = uiState.detailError,
            canApprove = uiState.tab == TAB_TODO && !opened.canCancelLeave,
            canDelete = uiState.tab == TAB_APPLY,
            // 待销假（状态码 5）才给这颗钮；学校自己的页面在这种件上只摆「销假」，
            // 同意/驳回一起出现就成了两个都能点、不知道该点哪个，所以上面把 canApprove 关了
            canCancelLeave = opened.canCancelLeave,
            xjInfo = uiState.xjInfo,
            isLoadingXj = uiState.isLoadingXj,
            xjError = uiState.xjError,
            submitError = uiState.submitError,
            isSubmitting = uiState.isSubmitting,
            onRetry = viewModel::retryDetail,
            onWithdraw = { viewModel.withdraw(opened.id) },
            onApprove = { remark -> viewModel.approve(opened, uiState.detail?.rawFormValues.orEmpty(), remark) },
            onReject = { remark -> viewModel.reject(opened, uiState.detail?.rawFormValues.orEmpty(), remark) },
            onDelete = { viewModel.delete(opened) },
            onAskXj = viewModel::askCancelLeave,
            onCancelLeave = { viewModel.cancelLeave(opened, uiState.detail?.rawFormValues.orEmpty()) },
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
    canApprove: Boolean,
    canDelete: Boolean,
    canCancelLeave: Boolean,
    xjInfo: LeaveXjInfo?,
    isLoadingXj: Boolean,
    xjError: String?,
    submitError: String?,
    isSubmitting: Boolean,
    onRetry: () -> Unit,
    onWithdraw: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onDelete: () -> Unit,
    onAskXj: () -> Unit,
    onCancelLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmWithdraw by remember { mutableStateOf(false) }
    var confirmApprove by remember { mutableStateOf(false) }
    var confirmReject by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showXjDialog by remember { mutableStateOf(false) }
    var rejectRemark by remember { mutableStateOf("") }
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

                // 只有「待办」那一栏才有资格同意/驳回；dataJson 用详情拿到的原文回显
                if (canApprove && detail != null) {
                    item(key = "detail_approve") {
                        LxButton(
                            text = stringResource(R.string.leave_approve),
                            enabled = !isSubmitting,
                            onClick = { confirmApprove = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "detail_reject") {
                        LxOutlinedButton(
                            text = stringResource(R.string.leave_reject),
                            enabled = !isSubmitting,
                            onClick = { confirmReject = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 销假：批下来了、人回来了要报这一条。点开弹层才去问超没超假（只读那一发）
                if (canCancelLeave && detail != null) {
                    item(key = "detail_xj") {
                        LxButton(
                            text = stringResource(R.string.leave_xj),
                            enabled = !isSubmitting,
                            onClick = {
                                showXjDialog = true
                                onAskXj()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (item.withdrawable) {
                    item(key = "detail_withdraw") {
                        LxOutlinedButton(
                            text = stringResource(R.string.leave_withdraw),
                            enabled = !isSubmitting,
                            onClick = { confirmWithdraw = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 删除只在自己发起的那些里面
                if (canDelete) {
                    item(key = "detail_delete") {
                        LxOutlinedButton(
                            text = stringResource(R.string.leave_delete),
                            enabled = !isSubmitting,
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 写操作失败的原因以前只写进 errors[tab]，列表非空时屏幕上看不见 —— 现在摆在这
                if (!submitError.isNullOrBlank()) {
                    item(key = "detail_submit_error") {
                        Text(
                            text = submitError,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }

                if (isSubmitting) {
                    item(key = "detail_submitting") {
                        Text(
                            text = stringResource(R.string.leave_submitting),
                            style = MiuixTheme.textStyles.footnote1,
                            color = LxInkMuted,
                            modifier = Modifier.padding(vertical = 8.dp),
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

    if (confirmApprove) {
        LxDialog(
            title = stringResource(R.string.leave_approve),
            message = stringResource(R.string.leave_approve_confirm),
            confirmText = stringResource(R.string.leave_approve),
            onConfirm = {
                confirmApprove = false
                onApprove("")
            },
            onDismiss = { confirmApprove = false },
            onDismissRequest = { confirmApprove = false },
        )
    }

    if (confirmReject) {
        LxDialog(
            title = stringResource(R.string.leave_reject),
            confirmText = stringResource(R.string.leave_reject),
            confirmTone = LxDialogConfirmTone.Destructive,
            onConfirm = {
                confirmReject = false
                onReject(rejectRemark)
            },
            onDismiss = { confirmReject = false },
            onDismissRequest = { confirmReject = false },
            content = {
                Text(
                    text = stringResource(R.string.leave_reject_confirm),
                    style = MiuixTheme.textStyles.footnote1,
                    color = LxInkMuted,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LxTextField(
                    value = rejectRemark,
                    onValueChange = { rejectRemark = it },
                    label = stringResource(R.string.leave_remark_label),
                    singleLine = false,
                )
            },
        )
    }

    if (confirmDelete) {
        LxDialog(
            title = stringResource(R.string.leave_delete),
            message = stringResource(R.string.leave_delete_confirm),
            confirmText = stringResource(R.string.leave_delete),
            confirmTone = LxDialogConfirmTone.Destructive,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
            onDismissRequest = { confirmDelete = false },
        )
    }

    // 销假弹层：内容照学校那三行摆，说明也抄它自己的话。这一发是写操作，点确认就出去了、撤不回来
    if (showXjDialog) {
        LxDialog(
            title = stringResource(R.string.leave_xj),
            // 一个弹层只留一个主动作：读到数据了这颗就是「确定」（发写请求），
            // 没读到它就是「再问一次」（发读请求），标签跟着作用改，不摆第二个重试钮
            confirmText = stringResource(
                if (xjInfo != null && !isLoadingXj) R.string.action_confirm else R.string.leave_xj_reask,
            ),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showXjDialog = false },
            onDismissRequest = { showXjDialog = false },
            onConfirm = {
                if (xjInfo != null && !isLoadingXj) {
                    showXjDialog = false
                    onCancelLeave()
                } else {
                    // 没问出超没超假就不发：弹层留着，转圈或报错都在这一层看
                    onAskXj()
                }
            },
            content = {
                Text(
                    text = stringResource(R.string.leave_xj_confirm),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.leave_xj_note),
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                )
                Spacer(modifier = Modifier.height(10.dp))
                when {
                    isLoadingXj -> Text(
                        text = stringResource(R.string.leave_xj_loading),
                        style = MiuixTheme.textStyles.footnote1,
                        color = LxInkMuted,
                    )

                    xjInfo == null -> {
                        Text(
                            text = xjError?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.leave_xj_failed),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }

                    else -> {
                        val info = xjInfo
                        LeaveXjRow(stringResource(R.string.leave_xj_over), info.over)
                        LeaveXjRow(stringResource(R.string.leave_xj_range), info.rangeText())
                        LeaveXjRow(stringResource(R.string.leave_xj_duration), info.duration)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.leave_xj_no_file),
                    style = MiuixTheme.textStyles.footnote1,
                    color = LxInkMuted,
                )
            },
        )
    }
}

/** 销假弹层里的一行：学校没给值的那行干脆不显示，不摆个空值当"是 0" */
@Composable
private fun LeaveXjRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = LxInkMuted,
            modifier = Modifier.width(96.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = LxInk,
            modifier = Modifier.weight(1f),
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
            if (item.isCheckinTodo) {
                // 让他看得出来这条点了不是看表单，是去查寝那一页
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.leave_open_checkin),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
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
