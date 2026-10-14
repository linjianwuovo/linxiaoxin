package com.linxin.feature.running.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxSecondaryButton
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSage
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.feature.running.domain.RouteTemplate
import com.linxin.feature.running.domain.RunningDashboard
import com.linxin.feature.running.service.RunTrackingService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 开始运动那一屏 —— 布局照官方 `/startSports`：
 * 学期标题 → 进度（已完成/计划 + 百分数）→ 任务起止 → 最长里程记录 → 今日里程 →
 * 跑步有效性 → 健康小贴士 → 「开始运动」。
 *
 * 官方那颗「开始运动」是 `window.android.goRunning(...)` 把界面交给原生壳；我们没有壳，
 * 直接开自己那一屏运动中。多出来的「模拟路线」是我们自己那条：挑一条画好的线，
 * 开始跑步时按官方那个频率（三秒一个点）往自己进程里喂点。
 */
@Composable
fun RunningHomeScreen(
    viewModel: RunningViewModel,
    onOpenActive: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenPlanner: () -> Unit = {},
    onOpenRecords: () -> Unit = {},
    onOpenSim: () -> Unit = {},
    onOpenClub: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionError by remember { mutableStateOf<String?>(null) }
    var showRoutePicker by remember { mutableStateOf(false) }
    val permErrorText = stringResource(R.string.run_perm_error)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (!result.values.all { it }) {
            permissionError = permErrorText
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            viewModel.startRealRun().onSuccess {
                RunTrackingService.start(context)
                onOpenActive()
            }
        }
    }

    fun requestAndStart() {
        permissionError = null
        if (uiState.trackerState.isSessionActive) {
            onOpenActive()
            return
        }
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LxTopBar(
                title = stringResource(R.string.ss_title_start),
                onBack = onBack,
                actions = {
                    Text(
                        text = stringResource(R.string.ss_records_entry),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier
                            .clickable(onClick = onOpenRecords)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        when {
            uiState.isDashboardLoading -> LxLoading(modifier = Modifier.fillMaxSize().padding(padding))
            uiState.dashboardError != null -> LxError(
                message = uiState.dashboardError ?: "",
                onRetry = viewModel::refreshDashboard,
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.dashboard?.let { dashboard ->
                    ProgressCard(dashboard, onClick = onOpenClub)
                    RecordCard(dashboard)
                    RuleCard(dashboard)
                }
                TipsCard()

                permissionError?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                if (uiState.trackerState.isSessionActive) {
                    LxButton(
                        text = stringResource(R.string.ra_title_running),
                        onClick = onOpenActive,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                } else {
                    LxButton(
                        text = stringResource(R.string.ss_btn_start),
                        onClick = { requestAndStart() },
                        enabled = !uiState.isStarting,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                LxSecondaryButton(
                    text = uiState.armedRoute?.let {
                        stringResource(R.string.ss_sim_armed, it.name, "%.0f".format(uiState.armedPaceKmh))
                    } ?: stringResource(R.string.ss_btn_sim_route),
                    onClick = { showRoutePicker = true },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    if (showRoutePicker) {
        RoutePickerDialog(
            choices = uiState.routeChoices,
            armedId = uiState.armedRoute?.id,
            paceKmh = uiState.armedPaceKmh,
            onPick = viewModel::armRoute,
            onPace = viewModel::setArmedPace,
            onOpenPlanner = {
                showRoutePicker = false
                onOpenPlanner()
            },
            onDismiss = { showRoutePicker = false },
        )
    }
}

/** 学期标题 + 进度 + 起止：官方那屏顶上就是这三行 */
@Composable
private fun ProgressCard(dashboard: RunningDashboard, onClick: () -> Unit) {
    LxCard(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = if (dashboard.termTitle.isBlank()) {
                dashboard.studentTypeLabel
            } else {
                stringResource(R.string.ss_term_title, dashboard.termTitle)
            },
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.SemiBold,
            color = LxInk,
        )
        Spacer(Modifier.height(12.dp))
        val target = dashboard.taskTargetKm
        LinearProgressIndicator(
            progress = {
                if (target <= 0.0) 0f else (dashboard.completedKm / target).toFloat().coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%.2fkm /%.1fkm".format(dashboard.completedKm, dashboard.taskTargetKm),
                style = MiuixTheme.textStyles.body2,
                color = LxInk,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%d%%".format(dashboard.ratioPercent),
                style = MiuixTheme.textStyles.body2,
                color = LxTerra,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (dashboard.termStart.isNotBlank() && dashboard.termEnd.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ss_range_to, dashboard.termStart, dashboard.termEnd),
                style = MiuixTheme.textStyles.footnote1,
                color = LxInkMuted,
            )
        }
    }
}

/** 最长里程记录 + 今日里程 */
@Composable
private fun RecordCard(dashboard: RunningDashboard) {
    LxCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        TwoLine(
            label = stringResource(R.string.ss_max_record),
            value = "%.2fkm".format(dashboard.maxKm),
            caption = dashboard.maxKmDate,
        )
        Spacer(Modifier.height(10.dp))
        TwoLine(
            label = stringResource(R.string.ss_today_mile),
            value = "%.2fkm".format(dashboard.todayKm),
            caption = dashboard.todayDate,
        )
    }
}

@Composable
private fun TwoLine(label: String, value: String, caption: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = LxInkMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.SemiBold,
            color = LxInk,
        )
        if (caption.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = caption,
                style = MiuixTheme.textStyles.footnote2,
                color = LxInkMuted,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/** 服务端下发的那三条约束，一个字都不写死 */
@Composable
private fun RuleCard(dashboard: RunningDashboard) {
    LxCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.ss_valid_rule_title),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            color = LxInk,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.ss_valid_rule_once, fmt(dashboard.singleRunTargetKm)) +
                stringResource(R.string.ss_valid_rule_day, fmt(dashboard.maxDayKm)) +
                stringResource(R.string.ss_valid_rule_speed, fmt(dashboard.minSpeedKmh), fmt(dashboard.maxSpeedKmh)),
            style = MiuixTheme.textStyles.footnote1,
            color = LxInkMuted,
        )
    }
}

@Composable
private fun TipsCard() {
    LxCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.ss_tips_title),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            color = LxInk,
        )
        Spacer(Modifier.height(6.dp))
        listOf(R.string.ss_tip_1, R.string.ss_tip_2, R.string.ss_tip_3, R.string.ss_tip_4).forEach {
            Text(
                text = stringResource(it),
                style = MiuixTheme.textStyles.footnote1,
                color = LxInkMuted,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

/** 挑线 + 挑配速那层弹层 */
@Composable
private fun RoutePickerDialog(
    choices: List<RouteTemplate>,
    armedId: String?,
    paceKmh: Double,
    onPick: (RouteTemplate) -> Unit,
    onPace: (Double) -> Unit,
    onOpenPlanner: () -> Unit,
    onDismiss: () -> Unit,
) {
    LxDialog(
        title = stringResource(R.string.ss_btn_sim_route),
        confirmText = stringResource(R.string.run_start),
        onConfirm = onDismiss,
        onDismissRequest = onDismiss,
        dismissText = stringResource(R.string.ss_sim_open_planner),
        onDismiss = onOpenPlanner,
        content = {
            if (choices.isEmpty()) {
                Text(
                    text = stringResource(R.string.ss_sim_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = LxInkMuted,
                )
            } else {
                choices.forEach { route ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(route) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = if (route.id == armedId) LxSage else LxInkMuted,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = route.name,
                                style = MiuixTheme.textStyles.body1,
                                color = LxInk,
                            )
                            Text(
                                text = "%.2f km · %d 个拐点".format(
                                    route.totalDistanceMeters / 1000.0,
                                    route.pointCount,
                                ),
                                style = MiuixTheme.textStyles.footnote2,
                                color = LxInkMuted,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.ss_sim_pace),
                style = MiuixTheme.textStyles.footnote1,
                color = LxInkMuted,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6.0, 7.0, 9.0).forEach { kmh ->
                    LxFilterChip(
                        label = "%.0f km/h".format(kmh),
                        selected = paceKmh == kmh,
                        onClick = { onPace(kmh) },
                    )
                }
            }
        },
    )
}

private fun fmt(value: Double): String = when {
    value <= 0.0 -> "—"
    value % 1.0 == 0.0 -> value.toInt().toString()
    else -> String.format(Locale.CHINA, "%.1f", value)
}
