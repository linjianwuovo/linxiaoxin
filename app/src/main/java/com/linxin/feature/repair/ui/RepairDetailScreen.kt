package com.linxin.feature.repair.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import com.linxin.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkGhost
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSand
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.feature.repair.data.RepairInfo
import com.linxin.feature.repair.data.RepairRepository
import com.linxin.feature.repair.data.RepairStep
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class RepairDetailUiState(
    val detail: RepairInfo? = null,
    val steps: List<RepairStep> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * 详情 + 处理进度两条请求一起发：抓包确认它们是各自独立的接口，
 * 谁先回来都不影响，但界面要等两个都齐了一次性显示（他的硬规矩：不要一路加载一路蹦）。
 */
@HiltViewModel
class RepairDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RepairRepository,
) : ViewModel() {

    private val bxdh: String = savedStateHandle.get<String>("bxdh") ?: ""

    private val _uiState = MutableStateFlow(RepairDetailUiState())
    val uiState: StateFlow<RepairDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (bxdh.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "缺少报修单号") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val detailResult = repository.detail(bxdh)
            val flowResult = repository.flow(bxdh)
            _uiState.update {
                it.copy(
                    detail = detailResult.getOrNull(),
                    steps = flowResult.getOrNull().orEmpty(),
                    isLoading = false,
                    error = detailResult.exceptionOrNull()?.message
                        ?: flowResult.exceptionOrNull()?.message,
                )
            }
        }
    }
}

@Composable
fun RepairDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RepairDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.repair_detail), onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize())
            uiState.detail == null && uiState.error != null -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::load,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.detail?.let { info ->
                    item { RepairInfoCard(info) }
                }
                item {
                    Text(
                        text = stringResource(R.string.repair_flow),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = LxInk,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (uiState.steps.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.repair_flow_empty),
                            style = MiuixTheme.textStyles.body2,
                            color = LxInkMuted,
                        )
                    }
                } else {
                    itemsIndexed(uiState.steps) { index, step ->
                        RepairStepRow(step = step, isLast = index == uiState.steps.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepairInfoCard(info: RepairInfo) {
    LxCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = info.type.ifBlank { stringResource(R.string.repair_untitled) },
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            Spacer(modifier = Modifier.height(10.dp))
            RepairField(label = stringResource(R.string.repair_no), value = info.bxdh)
            RepairField(label = stringResource(R.string.repair_status), value = info.status)
            RepairField(label = stringResource(R.string.repair_place), value = info.place)
            RepairField(label = stringResource(R.string.repair_time), value = info.time)
            val expect = listOf(info.expectFrom, info.expectTo).filter { it.isNotBlank() }.joinToString(" ~ ")
            RepairField(label = stringResource(R.string.repair_expect), value = expect)
            RepairField(label = stringResource(R.string.repair_desc), value = info.description)
            RepairField(label = stringResource(R.string.repair_repair_note), value = info.repairNote)
            RepairField(label = stringResource(R.string.repair_finish), value = info.finishNote)
            RepairField(label = stringResource(R.string.repair_evaluation), value = info.evaluation)
        }
    }
}

@Composable
private fun RepairField(label: String, value: String) {
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
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = LxInk,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RepairStepRow(step: RepairStep, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.width(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(LxSandDeep),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(56.dp)
                        .background(LxSand),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.text,
                style = MiuixTheme.textStyles.body2,
                color = LxInk,
            )
            if (step.time.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = step.time,
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkGhost,
                )
            }
        }
    }
}
