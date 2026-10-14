package com.linxin.feature.running.ui

import android.content.Context
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxDialogConfirmTone
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.location.InternalGpsFeed
import com.linxin.core.map.LiveTrackMap
import com.linxin.feature.running.data.RunningRepository
import com.linxin.feature.running.data.RouteTemplateStore
import com.linxin.feature.running.domain.RunningTrackerState
import com.linxin.feature.running.service.RunTrackingService
import com.linxin.feature.running.service.RunningTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用内虚拟跑步：跑的是**你自己规划的那条线**。
 *
 * 一页办完：选一条线（地图规划的、或者录过的）→ 开始 → 地图上看着它跑 → 结束 →
 * 提交还是丢掉，两颗分开、提交要二次确认。
 *
 * 喂点只在我们自己进程里（`InternalGpsFeed`），不碰系统定位、不影响别的 App，
 * 所以没 root 的手机也能用。跑的是同一条真链路：`RunTrackingService` + `RunningTracker`，
 * 距离、配速、实况通知都按真 GPS 那套算。
 *
 * 没有"绕个圈"这种选项 —— 圈是我凭空画的，画在哪儿我自己都不知道，要看就画一条真的线。
 */
@HiltViewModel
class VirtualRunViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feed: InternalGpsFeed,
    private val tracker: RunningTracker,
    private val repository: RunningRepository,
    routeTemplateStore: RouteTemplateStore,
) : ViewModel() {

    val trail = feed.trail
    val fedCount = feed.fedCount
    val templates = routeTemplateStore.templates
    val runState: StateFlow<RunningTrackerState> = tracker.state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 跑完一轮之后留在屏上的结果，等他决定要不要提交 */
    private val _finishedMeters = MutableStateFlow(0.0)
    val finishedMeters: StateFlow<Double> = _finishedMeters.asStateFlow()

    var routeId by mutableStateOf<String?>(null)
        private set

    /** 这一场按多快跑：学校那条窗口是 2–15 km/h，三档都在里面 */
    var paceKmh by mutableStateOf(9.0)
        private set

    fun pickPace(kmh: Double) {
        if (isRunning) return
        paceKmh = kmh
    }

    val isRunning: Boolean get() = feed.isOn.value

    /** 没手动选就取最近动过的那条（刚在地图上规划完的那条正好排第一） */
    fun selectedRoute() = templates.value.firstOrNull { it.id == routeId }
        ?: templates.value.maxByOrNull { it.updatedAtMillis }

    fun pickRoute(id: String) {
        if (isRunning) return
        routeId = id
    }

    fun report(text: String) {
        _message.value = text
    }

    /**
     * 开始：先喂点再起服务。
     *
     * 顺序不能反 —— 跑步那条流订阅时决定收谁，先起服务再开喂点会错过头几秒；
     * 另外 `RunningTracker` 要 `isSessionActive` 才收点，所以会话得这页自己开。
     */
    fun start() {
        if (isRunning) return
        _message.value = null
        val route = selectedRoute()
        if (route == null || route.points.size < 2) {
            _message.value = "还没有可选的路线，先点「在地图上规划路线」画一条"
            return
        }
        _busy.value = true
        viewModelScope.launch {
            // 学校那边的"开始跑步"：拿到单次要求公里数，实况通知那根进度条才有分母。
            // 拿不到也照跑，只是通知退回纯文字样式。
            val startInfo = repository.startRunning().getOrNull()
            if (startInfo != null) tracker.beginSession(startInfo) else tracker.beginTemplateSession()
            // 按选定的速度换算成"这条线要走多少秒"，再把线打密到每 2 米一个点
            val meters = route.totalDistanceMeters
            val seconds = (meters / (paceKmh / 3.6)).toLong().coerceAtLeast(1L)
            feed.startRoute(feed.densify(route.points), seconds)
            RunTrackingService.start(context)
            _busy.value = false
            if (startInfo == null) {
                _message.value = "学校体育那边没应（这台服务常在抽风）：本地照跑，距离照走，只是进度条可能不出来"
            }
        }
    }

    /** 结束：停喂点 + 停服务，数据留在屏上等他决定 */
    fun finish() {
        if (!isRunning) return
        _finishedMeters.value = runState.value.totalDistanceMeters
        RunTrackingService.stop(context)
        feed.stop()
    }

    /** 不要了这一场：走 cancel，会话直接丢，学校那边什么都收不到 */
    fun discard() {
        _finishedMeters.value = 0.0
        RunTrackingService.cancel(context)
        feed.stop()
        _message.value = null
    }

    fun upload(onDone: (String) -> Unit) {
        val snapshot = tracker.snapshotForUpload()
        if (snapshot == null) {
            onDone("这一场没有可提交的轨迹")
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val result = repository.uploadTrackedRun(snapshot)
            _busy.value = false
            onDone(
                result.fold(
                    onSuccess = { if (it.success) "已提交：${it.message.ifBlank { "学校收了" }}" else "学校没收：${it.message}" },
                    onFailure = { "提交失败：${it.message ?: "没给原因"}" },
                )
            )
        }
    }
}

@Composable
fun VirtualRunScreen(
    onBack: () -> Unit,
    onOpenPlanner: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VirtualRunViewModel = hiltViewModel(),
) {
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    val fedCount by viewModel.fedCount.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val runState by viewModel.runState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val finished by viewModel.finishedMeters.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmUpload by remember { mutableStateOf(false) }
    val isRunning = viewModel.isRunning
    val route = viewModel.selectedRoute()

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.virtual_run_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "vr_map") {
                LxCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isRunning) {
                                stringResource(R.string.virtual_run_map_running)
                            } else {
                                stringResource(
                                    R.string.virtual_run_map_pick,
                                    route?.name ?: stringResource(R.string.virtual_run_none),
                                    route?.let { "%.2f km".format(it.totalDistanceMeters / 1000.0) }
                                        ?: "--",
                                )
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = LxInkMuted,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
                        )
                        LiveTrackMap(
                            points = if (isRunning) trail else route?.points.orEmpty(),
                            markLast = isRunning,
                            follow = isRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                        )
                    }
                }
            }

            item(key = "vr_numbers") {
                LxCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NumberCell(
                            title = stringResource(R.string.virtual_run_distance),
                            value = "%.2f".format(runState.totalDistanceMeters / 1000.0),
                            unit = "km",
                            modifier = Modifier.weight(1f),
                        )
                        NumberCell(
                            title = stringResource(R.string.virtual_run_points),
                            value = "${runState.points.size}",
                            unit = stringResource(R.string.virtual_run_points_unit),
                            modifier = Modifier.weight(1f),
                        )
                        NumberCell(
                            title = stringResource(R.string.virtual_run_speed),
                            value = "%.1f".format(speedKmh(runState)),
                            unit = "km/h",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item(key = "vr_pace") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6.0 to "慢 6", 9.0 to "中 9", 12.0 to "快 12").forEach { (kmh, label) ->
                        LxFilterChip(
                            label = "%s km/h".format(label),
                            selected = viewModel.paceKmh == kmh,
                            onClick = { viewModel.pickPace(kmh) },
                        )
                    }
                }
            }

            item(key = "vr_planner") {
                LxButton(
                    text = stringResource(R.string.planner_entry),
                    onClick = onOpenPlanner,
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "vr_routes_title") {
                Text(
                    text = stringResource(R.string.virtual_run_routes_title),
                    style = MiuixTheme.textStyles.footnote1,
                    color = LxInkMuted,
                )
            }

            if (templates.isEmpty()) {
                item(key = "vr_no_route") {
                    Text(
                        text = stringResource(R.string.virtual_run_no_route),
                        style = MiuixTheme.textStyles.footnote1,
                        color = LxInkMuted,
                    )
                }
            } else {
                item(key = "vr_routes") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { template ->
                            val picked = route?.id == template.id
                            LxCard(onClick = { viewModel.pickRoute(template.id) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = template.name,
                                        style = MiuixTheme.textStyles.body2,
                                        color = if (picked) LxSandDeep else LxInk,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = if (picked) {
                                            stringResource(R.string.mock_loc_picked)
                                        } else {
                                            "%.2f km".format(template.totalDistanceMeters / 1000.0)
                                        },
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = if (picked) LxSandDeep else LxInkMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            message?.let { text ->
                item(key = "vr_message") {
                    Text(
                        text = text,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }

            item(key = "vr_action") {
                if (isRunning) {
                    LxButton(
                        text = stringResource(R.string.virtual_run_finish),
                        onClick = viewModel::finish,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LxButton(
                        text = stringResource(R.string.virtual_run_start),
                        enabled = !busy && route != null,
                        onClick = viewModel::start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (!isRunning && finished > 0.0) {
                item(key = "vr_upload") {
                    LxButton(
                        text = stringResource(R.string.virtual_run_upload, "%.2f".format(finished / 1000.0)),
                        enabled = !busy,
                        onClick = { confirmUpload = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "vr_discard") {
                    LxOutlinedButton(
                        text = stringResource(R.string.virtual_run_discard),
                        onClick = { confirmDiscard = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "vr_note") {
                Text(
                    text = stringResource(R.string.virtual_run_note),
                    style = MiuixTheme.textStyles.footnote1,
                    color = LxInkMuted,
                )
            }
        }
    }

    if (confirmUpload) {
        LxDialog(
            title = stringResource(R.string.virtual_run_upload_title),
            message = stringResource(R.string.virtual_run_upload_confirm, "%.2f".format(finished / 1000.0)),
            confirmText = stringResource(R.string.virtual_run_upload_ok),
            onConfirm = {
                confirmUpload = false
                viewModel.upload(viewModel::report)
            },
            onDismiss = { confirmUpload = false },
            onDismissRequest = { confirmUpload = false },
        )
    }

    if (confirmDiscard) {
        LxDialog(
            title = stringResource(R.string.virtual_run_discard),
            message = stringResource(R.string.virtual_run_discard_confirm),
            confirmText = stringResource(R.string.virtual_run_discard_ok),
            confirmTone = LxDialogConfirmTone.Destructive,
            onConfirm = {
                confirmDiscard = false
                viewModel.discard()
            },
            onDismiss = { confirmDiscard = false },
            onDismissRequest = { confirmDiscard = false },
        )
    }
}

/** 这一场的平均速度：距离除时长，单位 km/h，跟学校那条 2–15 的窗口一个口径 */
private fun speedKmh(state: RunningTrackerState): Double {
    if (state.startTimeMillis <= 0L) return 0.0
    val seconds = ((System.currentTimeMillis() - state.startTimeMillis) / 1000L).coerceAtLeast(1L)
    return state.totalDistanceMeters / 1000.0 / seconds * 3600.0
}

@Composable
private fun NumberCell(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            color = LxInkMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                style = MiuixTheme.textStyles.footnote2,
                color = LxInkMuted,
            )
        }
    }
}
