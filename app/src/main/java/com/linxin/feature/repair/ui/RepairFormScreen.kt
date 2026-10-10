package com.linxin.feature.repair.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import com.linxin.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTextField
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.feature.repair.data.RepairDraft
import com.linxin.feature.repair.data.RepairFormInit
import com.linxin.feature.repair.data.RepairNode
import com.linxin.feature.repair.data.RepairRepository
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

data class RepairFormUiState(
    val init: RepairFormInit? = null,
    val place1: RepairNode? = null,
    val place2: RepairNode? = null,
    val place3: RepairNode? = null,
    val type1: RepairNode? = null,
    val type2: RepairNode? = null,
    val type3: RepairNode? = null,
    val address: String = "",
    val phone: String = "",
    val description: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    val validation: String? = null,
) {
    /** 类型取选中的最深一级，和 H5 的 `applytypemod[2]||[1]||[0]` 一个意思 */
    val typeValue: String
        get() = (type3 ?: type2 ?: type1)?.value.orEmpty()

    val canSubmit: Boolean
        get() = place1 != null && place2 != null && place3 != null &&
            typeValue.isNotBlank() && address.isNotBlank() &&
            phone.matches(Regex("^1\\d{10}$")) && description.isNotBlank()
}

@HiltViewModel
class RepairFormViewModel @Inject constructor(
    private val repository: RepairRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepairFormUiState())
    val uiState: StateFlow<RepairFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.formInit().fold(
                onSuccess = { init ->
                    _uiState.update {
                        it.copy(
                            init = init,
                            phone = init.phone,
                            isLoading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun pickPlace(level: Int, node: RepairNode) {
        _uiState.update {
            when (level) {
                1 -> it.copy(place1 = node, place2 = null, place3 = null)
                2 -> it.copy(place2 = node, place3 = null)
                else -> it.copy(place3 = node)
            }
        }
    }

    fun pickType(level: Int, node: RepairNode) {
        _uiState.update {
            when (level) {
                1 -> it.copy(type1 = node, type2 = null, type3 = null)
                2 -> it.copy(type2 = node, type3 = null)
                else -> it.copy(type3 = node)
            }
        }
    }

    fun setAddress(v: String) = _uiState.update { it.copy(address = v.take(50)) }
    fun setPhone(v: String) = _uiState.update { it.copy(phone = v.take(11)) }
    fun setDescription(v: String) = _uiState.update { it.copy(description = v.take(200)) }

    fun submit() {
        val s = _uiState.value
        if (!s.canSubmit) {
            _uiState.update { it.copy(validation = "还有必填项没填或格式不对") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, validation = null) }
            repository.submit(
                RepairDraft(
                    placeLevel1 = s.place1!!.value,
                    placeLevel2 = s.place2!!.value,
                    placeLevel3 = s.place3!!.value,
                    typeValue = s.typeValue,
                    address = s.address,
                    phone = s.phone,
                    description = s.description,
                    personName = s.init?.personName.orEmpty(),
                ),
            ).fold(
                onSuccess = { _uiState.update { it.copy(isSubmitting = false, submitted = true) } },
                onFailure = { e -> _uiState.update { it.copy(isSubmitting = false, validation = e.message) } },
            )
        }
    }
}

/**
 * 新建报修。地点/类型都是 H5 那两棵树（parent/value 扁平表），
 * 这里按层级展开成三排可选片，选法和服务端期望的三级 value 一致。
 */
@Composable
fun RepairFormScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RepairFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.repair_new), onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding).fillMaxSize())
            uiState.init == null && uiState.error != null -> LxError(
                message = uiState.error!!,
                onRetry = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                uiState.init?.let { init ->
                    FormLabel(stringResource(R.string.repair_place_pick))
                    TreePicker(
                        nodes = init.places,
                        selected = listOf(uiState.place1, uiState.place2, uiState.place3),
                        onPick = { level, node -> viewModel.pickPlace(level, node) },
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    FormLabel(stringResource(R.string.repair_type_pick))
                    TreePicker(
                        nodes = init.types,
                        selected = listOf(uiState.type1, uiState.type2, uiState.type3),
                        onPick = { level, node -> viewModel.pickType(level, node) },
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LxTextField(
                        value = uiState.address,
                        onValueChange = viewModel::setAddress,
                        label = stringResource(R.string.repair_addr),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LxTextField(
                        value = uiState.phone,
                        onValueChange = viewModel::setPhone,
                        label = stringResource(R.string.repair_phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LxTextField(
                        value = uiState.description,
                        onValueChange = viewModel::setDescription,
                        label = stringResource(R.string.repair_desc_input),
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    uiState.validation?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    LxButton(
                        text = stringResource(R.string.repair_submit),
                        enabled = !uiState.isSubmitting,
                        onClick = { confirm = true },
                    )
                }
            }
        }
    }

    if (confirm) {
        LxDialog(
            title = stringResource(R.string.repair_submit),
            message = stringResource(R.string.repair_submit_warn),
            confirmText = stringResource(R.string.action_confirm),
            onConfirm = {
                confirm = false
                viewModel.submit()
            },
            onDismissRequest = { confirm = false },
        )
    }
    if (uiState.submitted) {
        LxDialog(
            title = stringResource(R.string.repair_submitted),
            confirmText = stringResource(R.string.action_confirm),
            onConfirm = onSubmitted,
            onDismissRequest = onSubmitted,
        )
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = LxInkMuted,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** 一棵扁平树按层级展开：第 N 排只显示 parent 等于上一级 value 的节点 */
@Composable
private fun TreePicker(
    nodes: List<RepairNode>,
    selected: List<RepairNode?>,
    onPick: (Int, RepairNode) -> Unit,
) {
    val levels = listOf(
        "0" to 1,
        selected[0]?.value to 2,
        selected[1]?.value to 3,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        levels.forEach { (parentValue, level) ->
            if (parentValue == null) return@forEach
            val options = nodes.filter { it.parent == parentValue }
            if (options.isEmpty()) return@forEach
            val current = selected[level - 1]
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(options, key = { it.value }) { node ->
                    LxFilterChip(
                        label = node.name.ifBlank { "-" },
                        selected = current?.value == node.value,
                        onClick = { onPick(level, node) },
                    )
                }
            }
        }
    }
}

