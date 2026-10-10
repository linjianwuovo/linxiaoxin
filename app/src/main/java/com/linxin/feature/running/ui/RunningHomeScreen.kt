package com.linxin.feature.running.ui

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SsidChart
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linxin.R
import com.linxin.core.designsystem.theme.LxTabularNums
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDetailRow
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.feature.running.domain.ClubSummary
import com.linxin.feature.running.domain.RunningDashboard
import com.linxin.feature.running.service.RunTrackingService
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun RunningHomeScreen(
    onOpenActive: () -> Unit,
    onOpenSim: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenClub: () -> Unit = {},
    viewModel: RunningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionError by remember { mutableStateOf<String?>(null) }
    val dashboardError = uiState.dashboardError

    // 权限回调的 lambda 不是组合上下文，文案要先在外面取好
    val permErrorText = stringResource(R.string.run_perm_error)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.all { it }
        if (!granted) {
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
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            if (onBack != null) {
                LxTopBar(title = stringResource(R.string.title_running_home), onBack = onBack)
            }
        },
    ) { padding ->
        when {
            uiState.isDashboardLoading -> LxLoading(modifier = Modifier.padding(padding))
            dashboardError != null -> LxError(
                message = dashboardError,
                onRetry = viewModel::refreshDashboard,
                modifier = Modifier.padding(padding),
            )
            else -> RunningHomeContent(
                dashboard = uiState.dashboard,
                isStarting = uiState.isStarting,
                isActive = uiState.trackerState.isSessionActive,
                trackerLabel = uiState.trackerState.locationLabel,
                startError = permissionError ?: uiState.startError,
                advancedEnabled = uiState.advancedEnabled,
                onOpenClub = onOpenClub,
                onPrimaryAction = {
                    if (uiState.trackerState.isSessionActive) {
                        onOpenActive()
                    } else {
                        requestAndStart()
                    }
                },
                onSimAction = onOpenSim,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun RunningHomeContent(
    dashboard: RunningDashboard?,
    isStarting: Boolean,
    isActive: Boolean,
    trackerLabel: String,
    startError: String?,
    advancedEnabled: Boolean,
    onPrimaryAction: () -> Unit,
    onSimAction: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenClub: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = if (isActive) stringResource(R.string.run_title_active) else stringResource(R.string.run_title_home),
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isActive) {
                    stringResource(R.string.run_subtitle_active, trackerLabel)
                } else {
                    stringResource(R.string.run_subtitle_home)
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        dashboard?.clubSummary?.let { club ->
            item {
                ClubSummaryCard(club = club, onClick = onOpenClub)
            }
        }

        item {
            SummaryCard(dashboard = dashboard, trackerLabel = trackerLabel, isActive = isActive)
        }

        item {
            InsightCard(dashboard = dashboard)
        }

        if (!startError.isNullOrBlank()) {
            item {
                Text(
                    text = startError,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
            }
        }

        item {
            val primaryText = when {
                isStarting -> stringResource(R.string.run_starting)
                isActive -> stringResource(R.string.run_resume)
                else -> stringResource(R.string.run_start)
            }
            LxButton(
                text = primaryText,
                onClick = onPrimaryAction,
                enabled = !isStarting,
            )
            if (advancedEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                LxOutlinedButton(
                    text = stringResource(R.string.title_mock_submit),
                    onClick = onSimAction,
                    enabled = !isStarting,
                )
            }
        }
    }
}

@Composable
private fun ClubSummaryCard(
    club: ClubSummary,
    onClick: () -> Unit,
) {
    LxCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = club.courseName.ifBlank { stringResource(R.string.run_pe_course) },
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOf(club.term, club.teacherName.let { if (it.isBlank()) "" else stringResource(R.string.run_coach, it) })
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (club.memberLevel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = club.memberLevel,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.secondary,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.title_club_detail),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun SummaryCard(
    dashboard: RunningDashboard?,
    trackerLabel: String,
    isActive: Boolean,
) {
    val todayKm = dashboard?.todayKm ?: 0.0
    val completedKm = dashboard?.completedKm ?: 0.0
    val targetKm = dashboard?.taskTargetKm ?: 0.0
    val progress = if (targetKm > 0) completedKm / targetKm else 0.0

    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.run_today_km),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = formatKm(todayKm, 2),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBlock(
                    icon = Icons.Default.Flag,
                    title = stringResource(R.string.run_task_progress),
                    value = if (targetKm > 0.0) {
                        "${formatKmValue(completedKm)} / ${formatKmValue(targetKm)} km"
                    } else {
                        formatKm(completedKm, 2)
                    },
                    iconOnStart = true,
                    startPadding = 4.dp,
                    modifier = Modifier.weight(1f),
                )
                MetricBlock(
                    icon = Icons.Default.SsidChart,
                    title = if (isActive) stringResource(R.string.run_session_state) else stringResource(R.string.run_ratio),
                    value = if (isActive) trackerLabel else String.format(Locale.CHINA, "%.0f%%", progress * 100),
                    iconOnStart = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InsightCard(dashboard: RunningDashboard?) {
    val singleRunTargetKm = dashboard?.singleRunTargetKm ?: 0.0
    val leftKm = dashboard?.leftKm ?: 0.0
    val maxKm = dashboard?.maxKm ?: 0.0
    val maxKmDate = dashboard?.maxKmDate.orEmpty()

    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.run_overview),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            LxDetailRow(
                label = stringResource(R.string.run_student_type),
                value = dashboard?.studentTypeLabel?.ifBlank { stringResource(R.string.run_unknown) } ?: stringResource(R.string.run_unknown),
                labelWidth = 76.dp,
                showDivider = false,
            )
            LxDetailRow(
                label = stringResource(R.string.run_single_target),
                value = if (singleRunTargetKm > 0.0) formatKm(singleRunTargetKm, 0) else stringResource(R.string.run_pending),
                labelWidth = 76.dp,
                showDivider = false,
            )
            LxDetailRow(
                label = stringResource(R.string.run_remaining),
                value = formatKm(leftKm, 2),
                labelWidth = 76.dp,
                showDivider = false,
            )
            LxDetailRow(
                label = stringResource(R.string.run_best),
                value = if (maxKm > 0.0) {
                    buildString {
                        append(formatKm(maxKm, 2))
                        if (maxKmDate.isNotBlank()) {
                            append(" · ")
                            append(maxKmDate)
                        }
                    }
                } else {
                    stringResource(R.string.run_no_records)
                },
                labelWidth = 76.dp,
                showDivider = false,
            )
            LxDetailRow(
                label = stringResource(R.string.run_task_state),
                value = if (dashboard?.dsFlag == true) stringResource(R.string.run_required_now) else stringResource(R.string.run_no_mandatory),
                labelWidth = 76.dp,
                showDivider = false,
            )
        }
    }
}

@Composable
private fun MetricBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    iconOnStart: Boolean = false,
    startPadding: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    LxCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(
                start = startPadding,
                top = 14.dp,
                end = 14.dp,
                bottom = 14.dp,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (iconOnStart) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // value 单行 + 末尾省略：保证左右两块高度恒等，不会因 "48 / 120 km" 之类长文本折行失衡
            Text(
                text = value,
                style = MiuixTheme.textStyles.title4.merge(LxTabularNums),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatKm(value: Double, digits: Int): String =
    String.format(Locale.CHINA, "%.${digits}f km", value)

private fun formatKmValue(value: Double): String =
    if (value % 1.0 == 0.0) {
        String.format(Locale.CHINA, "%.0f", value)
    } else {
        String.format(Locale.CHINA, "%.1f", value)
    }
