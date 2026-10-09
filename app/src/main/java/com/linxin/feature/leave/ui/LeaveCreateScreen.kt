package com.linxin.feature.leave.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.linxin.R
import androidx.compose.runtime.Composable
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
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.network.ApiConstants
import com.linxin.feature.leave.data.LeaveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** flowSheet 里要渲染的一种字段 */
data class LeaveField(
    val key: String,
    val label: String,
    val type: String,
    val options: List<Pair<String, String>>,
    val required: Boolean,
)

data class LeaveCreateUiState(
    val fields: List<LeaveField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    val validation: String? = null,
)

/**
 * 发起请假。表单不是写死的：字段、选项、必填全部来自 `getFlowSheet.do` 返回的那段 JSON，
 * 和 H5 用的是同一份定义（选项就在各节点的 properties.options 里，不用另查字典）。
 *
 * `QJSC`（请假时长）是 formula 字段：H5 的算法是把 flowSheet 里的 formula token 拼成表达式再 eval，
 * 其中 dateRange token 取两端的小时差。请假这张表的公式就是 QJRQ 的小时差，所以这里按小时差算，
 * 和服务端期望的数值一致；如果哪天公式里加了别的 token，这里会算不出，界面上就留空让人自己填。
 */
@HiltViewModel
class LeaveCreateViewModel @Inject constructor(
    private val repository: LeaveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveCreateUiState())
    val uiState: StateFlow<LeaveCreateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.flowSheet(ApiConstants.LEAVE_PROCESS_ID).fold(
                onSuccess = { sheet -> _uiState.update { it.copy(fields = parse(sheet), isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    private fun parse(sheet: String): List<LeaveField> {
        val out = mutableListOf<LeaveField>()
        fun walk(node: JSONObject) {
            val type = node.optString("type")
            val key = node.optString("model").ifBlank { node.optString("key") }
            if (key.isNotBlank() && type in setOf("input", "textarea", "radio", "select", "dateRange", "formula")) {
                val options = mutableListOf<Pair<String, String>>()
                node.optJSONObject("properties")?.optJSONArray("options")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        options += o.optString("label") to o.optString("value")
                    }
                }
                val required = node.optJSONArray("rules")?.let { rules ->
                    (0 until rules.length()).any { rules.optJSONObject(it)?.optBoolean("required") == true }
                } ?: false
                out += LeaveField(
                    key = key,
                    label = node.optString("label").ifBlank { key },
                    type = type,
                    options = options,
                    required = required,
                )
            }
            node.optJSONArray("children")?.let { children ->
                for (i in 0 until children.length()) {
                    children.optJSONObject(i)?.let { walk(it) }
                }
            }
        }
        runCatching {
            val root = JSONArray(sheet)
            for (i in 0 until root.length()) root.optJSONObject(i)?.let { walk(it) }
        }.onFailure {
            runCatching { walk(JSONObject(sheet)) }
        }
        return out
    }

    fun setValue(key: String, value: String) {
        _uiState.update { st ->
            val values = st.values + (key to value)
            st.copy(values = values, validation = null)
        }
        if (key == "QJRQ_START" || key == "QJRQ_END") recomputeHours()
    }

    /** QJRQ 在 dataJson 里是 [start, end] 两个 "YYYY-MM-DD HH:mm"；界面上拆成两个输入框 */
    private fun recomputeHours() {
        val st = _uiState.value
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val start = runCatching { LocalDateTime.parse(st.values["QJRQ_START"].orEmpty(), fmt) }.getOrNull()
        val end = runCatching { LocalDateTime.parse(st.values["QJRQ_END"].orEmpty(), fmt) }.getOrNull()
        if (start != null && end != null && !end.isBefore(start)) {
            val hours = ChronoUnit.HOURS.between(start, end)
            _uiState.update { it.copy(values = it.values + ("QJSC" to hours.toString())) }
        }
    }

    fun submit() {
        val st = _uiState.value
        val missing = st.fields.filter { it.required && st.values[it.key].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            _uiState.update { it.copy(validation = "还有必填项没填：" + missing.joinToString("、") { f -> f.label }) }
            return
        }
        val json = JSONObject()
        st.fields.forEach { f -> json.put(f.key, "") }
        st.values.forEach { (k, v) -> if (k != "QJRQ_START" && k != "QJRQ_END") json.put(k, v) }
        val range = JSONArray()
            .put(st.values["QJRQ_START"].orEmpty())
            .put(st.values["QJRQ_END"].orEmpty())
        if (st.fields.any { it.key == "QJRQ" }) json.put("QJRQ", range)
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, validation = null) }
            repository.submit(ApiConstants.LEAVE_PROCESS_ID, json.toString()).fold(
                onSuccess = { _uiState.update { it.copy(isSubmitting = false, submitted = true) } },
                onFailure = { e -> _uiState.update { it.copy(isSubmitting = false, validation = e.message) } },
            )
        }
    }
}

@Composable
fun LeaveCreateScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LeaveCreateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.leave_new), onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding).fillMaxSize())
            uiState.fields.isEmpty() && uiState.error != null -> LxError(
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
                uiState.fields.forEach { field ->
                    LeaveFieldEditor(field = field, state = uiState, onValue = viewModel::setValue)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                uiState.validation?.let {
                    Text(text = it, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                LxButton(
                    text = stringResource(R.string.leave_submit),
                    enabled = !uiState.isSubmitting,
                    onClick = { confirm = true },
                )
            }
        }
    }

    if (confirm) {
        LxDialog(
            title = stringResource(R.string.leave_submit),
            message = stringResource(R.string.leave_submit_warn),
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
            title = stringResource(R.string.leave_submitted),
            confirmText = stringResource(R.string.action_confirm),
            onConfirm = onSubmitted,
            onDismissRequest = onSubmitted,
        )
    }
}

@Composable
private fun LeaveFieldEditor(
    field: LeaveField,
    state: LeaveCreateUiState,
    onValue: (String, String) -> Unit,
) {
    Column {
        Text(
            text = field.label + if (field.required) " *" else "",
            style = MiuixTheme.textStyles.footnote1,
            color = LxInkMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        when (field.type) {
            "radio", "select" -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(field.options, key = { it.second }) { (label, value) ->
                    LxFilterChip(
                        label = label,
                        selected = state.values[field.key] == value,
                        onClick = { onValue(field.key, value) },
                    )
                }
            }
            "dateRange" -> {
                LxTextField(
                    value = state.values["QJRQ_START"].orEmpty(),
                    onValueChange = { onValue("QJRQ_START", it) },
                    label = stringResource(R.string.leave_start),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LxTextField(
                    value = state.values["QJRQ_END"].orEmpty(),
                    onValueChange = { onValue("QJRQ_END", it) },
                    label = stringResource(R.string.leave_end),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            "formula" -> Text(
                text = state.values[field.key].orEmpty().ifBlank { "-" },
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            else -> LxTextField(
                value = state.values[field.key].orEmpty(),
                onValueChange = { onValue(field.key, it) },
                label = field.label,
                singleLine = field.type != "textarea",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
