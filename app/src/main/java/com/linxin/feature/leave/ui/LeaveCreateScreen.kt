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
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.network.ApiConstants
import com.linxin.feature.leave.data.LeaveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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

/** flowSheet 里要渲染的一种字段（结构证据来自它 H5 的 VFormItem / VAddress 组件） */
data class LeaveField(
    val key: String,
    val label: String,
    val type: String,
    val options: List<Pair<String, String>>,
    val required: Boolean,
    val disabled: Boolean,
    /** dateRange 用：格式串和两个输入框的提示词 */
    val dateFormat: String,
    val startLabel: String,
    val endLabel: String,
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
 * 发起请假。表单不写死：字段、选项、必填、只读、隐藏全部来自 `getFlowSheet.do` 那段 JSON，
 * 默认值（标题/申请人/班级/学号/手机号/流水号）来自 `findDefinitionBase.do`，
 * 和 H5 打开页面时调的两个接口一模一样。
 *
 * 树的下钻键是 `list`（card 下面）和 `columns`（grid 下面，每列再有自己的 `list`）——
 * 不是 `children`，之前按 `children` 走所以一个字段都没渲染出来。
 *
 * 两个已知限制，都是"没证据就不猜"：
 * - 附件（uploadFile）没接，抓包里那条 rules 是 required=false，不挡提交；
 * - 地址（address）只给一个详细地址输入框。它 H5 的 VAddress 发的是
 *   `{province, city, county, address}`，不选省市区时前三项就是空串，
 *   所以我发空串 + 详细地址，是它本身允许的一种状态；要选省市区编码得再扒一份区划表。
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
                onSuccess = { sheet ->
                    val fields = parse(sheet)
                    _uiState.update { it.copy(fields = fields, isLoading = false) }
                    repository.defaults(ApiConstants.LEAVE_PROCESS_ID).onSuccess { defs ->
                        _uiState.update { st ->
                            val merged = st.values + defs.filterKeys { key ->
                                st.fields.any { it.key == key }
                            }
                            st.copy(values = merged)
                        }
                    }
                },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    private fun parse(sheet: String): List<LeaveField> {
        val out = mutableListOf<LeaveField>()
        val wanted = setOf("input", "textarea", "radio", "select", "dateRange", "formula", "address")

        fun visit(node: JSONObject) {
            val type = node.optString("type")
            val model = node.optString("model")
            val props = node.optJSONObject("properties")
            val hidden = props?.optBoolean("hidden", false) == true
            if (model.isNotBlank() && type in wanted && !hidden) {
                val options = mutableListOf<Pair<String, String>>()
                props?.optJSONArray("options")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        options += o.optString("label") to o.optString("value")
                    }
                }
                val rangeLabels = props?.optJSONArray("rangePlaceholder")
                out += LeaveField(
                    key = model,
                    label = node.optString("label").ifBlank { model },
                    type = type,
                    options = options,
                    // H5 的 isRequired 就是看 rules[0].required
                    required = node.optJSONArray("rules")?.optJSONObject(0)?.optBoolean("required") == true,
                    disabled = props?.optBoolean("disabled", false) == true,
                    dateFormat = props?.optString("format").orEmpty().ifBlank { "YYYY-MM-DD HH:mm" },
                    startLabel = rangeLabels?.optString(0).orEmpty().ifBlank { "开始时间" },
                    endLabel = rangeLabels?.optString(1).orEmpty().ifBlank { "结束时间" },
                )
                return
            }
            node.optJSONArray("list")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { visit(it) }
            }
            node.optJSONArray("columns")?.let { cols ->
                for (i in 0 until cols.length()) cols.optJSONObject(i)?.let { visit(it) }
            }
        }

        runCatching {
            val root = JSONObject(sheet)
            root.optJSONArray("list")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { visit(it) }
            }
        }
        return out
    }

    fun setValue(key: String, value: String) {
        _uiState.update { it.copy(values = it.values + (key to value), validation = null) }
        if (key == START_KEY || key == END_KEY) recomputeHours()
    }

    /** QJSC 是 formula 字段，它 H5 的公式 token 只有一个 dateRange，即两端的小时差 */
    private fun recomputeHours() {
        val st = _uiState.value
        val field = st.fields.firstOrNull { it.key == "QJRQ" } ?: return
        val pattern = field.dateFormat.replace("YYYY", "yyyy").replace("DD", "dd")
        val fmt = DateTimeFormatter.ofPattern(pattern)
        val start = parseTime(st.values[START_KEY], fmt)
        val end = parseTime(st.values[END_KEY], fmt)
        if (start != null && end != null && !end.isBefore(start)) {
            val hours = ChronoUnit.HOURS.between(start, end)
            _uiState.update { it.copy(values = it.values + ("QJSC" to hours.toString())) }
        }
    }

    private fun parseTime(text: String?, fmt: DateTimeFormatter): LocalDateTime? =
        runCatching { LocalDateTime.parse(text.orEmpty(), fmt) }.getOrNull()

    fun buildDataJson(): String {
        val st = _uiState.value
        val json = JSONObject()
        st.fields.forEach { f ->
            when (f.type) {
                "dateRange" -> json.put(f.key, JSONArray().put(st.values[START_KEY].orEmpty()).put(st.values[END_KEY].orEmpty()))
                "address" -> json.put(
                    f.key,
                    JSONObject()
                        .put("province", "")
                        .put("city", "")
                        .put("county", "")
                        .put("address", st.values[f.key].orEmpty()),
                )
                "formula" -> json.put(f.key, st.values[f.key].orEmpty().toDoubleOrNull() ?: 0)
                else -> json.put(f.key, st.values[f.key].orEmpty())
            }
        }
        return json.toString()
    }

    fun validate(): String? {
        val st = _uiState.value
        val missing = st.fields.filter { it.required && st.values[it.key].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            return "还没填：" + missing.joinToString("、") { it.label }
        }
        val range = st.fields.firstOrNull { it.type == "dateRange" }
        if (range != null) {
            val pattern = range.dateFormat.replace("YYYY", "yyyy").replace("DD", "dd")
            val fmt = DateTimeFormatter.ofPattern(pattern)
            val start = parseTime(st.values[START_KEY], fmt)
                ?: return "${range.label}的开始时间格式不对，要像 ${exampleOf(pattern)}"
            val end = parseTime(st.values[END_KEY], fmt)
                ?: return "${range.label}的结束时间格式不对，要像 ${exampleOf(pattern)}"
            if (end.isBefore(start)) return "结束时间不能早于开始时间"
        }
        return null
    }

    private fun exampleOf(pattern: String): String =
        if (pattern.contains("HH")) "2026-10-10 08:30" else "2026-10-10"

    fun submit() {
        val problem = validate()
        if (problem != null) {
            _uiState.update { it.copy(validation = problem) }
            return
        }
        val dataJson = buildDataJson()
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, validation = null) }
            repository.submit(ApiConstants.LEAVE_PROCESS_ID, dataJson).fold(
                onSuccess = { _uiState.update { it.copy(isSubmitting = false, submitted = true) } },
                onFailure = { e -> _uiState.update { it.copy(isSubmitting = false, validation = e.message) } },
            )
        }
    }

    companion object {
        private const val START_KEY = "QJRQ_START"
        private const val END_KEY = "QJRQ_END"
        private val DateTimeParseExceptionUnused: Unit = Unit
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
            uiState.fields.isEmpty() -> LxError(
                message = stringResource(R.string.leave_sheet_empty),
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
                    LeaveFieldEditor(
                        field = field,
                        values = uiState.values,
                        onValue = viewModel::setValue,
                    )
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
    values: Map<String, String>,
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
                        selected = values[field.key] == value,
                        onClick = { onValue(field.key, value) },
                    )
                }
            }
            "dateRange" -> Column {
                LxTextField(
                    value = values["QJRQ_START"].orEmpty(),
                    onValueChange = { onValue("QJRQ_START", it) },
                    label = "${field.startLabel}（${field.dateFormat.replace("YYYY", "yyyy").replace("DD", "dd")}）",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LxTextField(
                    value = values["QJRQ_END"].orEmpty(),
                    onValueChange = { onValue("QJRQ_END", it) },
                    label = field.endLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            "formula" -> Row {
                Text(
                    text = values[field.key].orEmpty().ifBlank { "-" },
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = " ${stringResource(R.string.leave_hours_unit)}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                )
            }
            "address" -> LxTextField(
                value = values[field.key].orEmpty(),
                onValueChange = { onValue(field.key, it) },
                label = stringResource(R.string.leave_address_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            else -> LxTextField(
                value = values[field.key].orEmpty(),
                onValueChange = { onValue(field.key, it) },
                label = field.label,
                enabled = !field.disabled,
                singleLine = field.type != "textarea",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
