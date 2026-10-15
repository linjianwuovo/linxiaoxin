package com.linxin.feature.sports.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDetailRow
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxIconButton
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxSecondaryButton
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxRose
import com.linxin.core.designsystem.theme.LxSage
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.feature.sports.SportsCoords
import com.linxin.feature.sports.domain.RecordRow
import com.linxin.feature.sports.domain.RecordState
import com.linxin.feature.sports.domain.RunRules
import com.linxin.feature.sports.domain.WgsPoint
import com.linxin.feature.sports.service.SportsRunEngine
import com.linxin.core.map.LiveTrackMap
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/* ----------------------------------------------------------------------------------
 * 开始运动
 * -------------------------------------------------------------------------------- */

@Composable
fun SportsStartScreen(
    onOpenRun: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: SportsStartViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var permissionError by remember { mutableStateOf<String?>(null) }
    // 回调那个 lambda 不是组合作用域，文案得先在外面取好
    val deniedText = stringResource(R.string.run_perm_error)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (!result.values.all { it }) {
            permissionError = deniedText
        } else {
            viewModel.startRun(onOpenRun)
        }
    }

    fun begin() {
        permissionError = null
        if (uiState.rules == null) {
            viewModel.reload()
            return
        }
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        launcher.launch(permissions)
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
            uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize().padding(padding))
            uiState.error != null && uiState.rules == null -> LxError(
                message = uiState.error ?: "",
                onRetry = viewModel::reload,
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
                uiState.rules?.let { RulesHeaderCard(it) }
                uiState.rules?.let { TipsCard() }

                permissionError?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                uiState.startError?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = LxRose,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                LxButton(
                    text = stringResource(R.string.ss_btn_start),
                    onClick = { begin() },
                    enabled = !uiState.isStarting,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

}

@Composable
private fun RulesHeaderCard(rules: RunRules) {
    LxCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = if (rules.termTitle.isBlank()) rules.grade else stringResource(R.string.ss_term_title, rules.termTitle),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.SemiBold,
            color = LxInk,
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = {
                if (rules.planMileKm <= 0.0) 0f else (rules.completeMileKm / rules.planMileKm).toFloat().coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%.2fkm /%.1fkm".format(rules.completeMileKm, rules.planMileKm),
                style = MiuixTheme.textStyles.body2,
                color = LxInk,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%d%%".format(rules.ratioPercent),
                style = MiuixTheme.textStyles.body2,
                color = LxTerra,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (rules.planStart.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ss_range_to, rules.planStart, rules.planEnd),
                style = MiuixTheme.textStyles.footnote1,
                color = LxInkMuted,
            )
        }
        Spacer(Modifier.height(12.dp))
        TwoLine(stringResource(R.string.ss_max_record), "%.2fkm".format(rules.bestMileKm), rules.bestMileDate)
        Spacer(Modifier.height(8.dp))
        TwoLine(stringResource(R.string.ss_today_mile), "%.2fkm".format(rules.todayMileKm), rules.todayDate)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ss_valid_rule_title),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            color = LxInk,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.ss_valid_rule_once, fmt(rules.singleMinKm)) +
                stringResource(R.string.ss_valid_rule_day, fmt(rules.dailyMaxKm)) +
                stringResource(R.string.ss_valid_rule_speed, fmt(rules.minSpeedKmh), fmt(rules.maxSpeedKmh)),
            style = MiuixTheme.textStyles.footnote1,
            color = LxInkMuted,
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
        Text(text = value, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold, color = LxInk)
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


/* ----------------------------------------------------------------------------------
 * 运动中
 * -------------------------------------------------------------------------------- */

@Composable
fun SportsRunScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SportsRunViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val finish by viewModel.finish.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var locked by remember { mutableStateOf(false) }

    LaunchedEffect(state.active, state.paused) {
        while (state.active) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LxTopBar(
                title = when {
                    !state.active -> stringResource(R.string.ra_title_ended)
                    state.paused -> stringResource(R.string.ra_title_paused)
                    else -> stringResource(R.string.ra_title_running)
                },
                onBack = onBack,
            )
        },
    ) { padding ->
        if (finish.uploading) {
            LxLoading(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        if (!state.active) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                finish.message?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.body1,
                        color = if (finish.recordId.isNullOrBlank()) LxRose else LxSage,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                LxButton(
                    text = stringResource(R.string.ss_records_entry),
                    onClick = {
                        viewModel.clearFinish()
                        onFinished()
                    },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            return@Scaffold
        }

        val seconds = state.elapsedSeconds(now)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    LxCard {
                        LiveTrackMap(
                            points = state.points,
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            markLast = true,
                            follow = true,
                        )
                    }
                }
                item {
                    LxCard {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%.2f".format(state.distanceKm),
                                style = MiuixTheme.textStyles.headline1,
                                fontWeight = FontWeight.Bold,
                                color = LxInk,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.ra_km),
                                style = MiuixTheme.textStyles.body2,
                                color = LxInkMuted,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Mini(stringResource(R.string.ra_speed), "%.2f".format(state.speedKmh), Modifier.weight(1f))
                            Mini(
                                stringResource(R.string.ra_duration),
                                "%02d:%02d".format(seconds / 60, seconds % 60),
                                Modifier.weight(1f),
                            )
                            Mini(
                                stringResource(R.string.ra_points, state.points.size),
                                if (state.waitingFix) stringResource(R.string.ra_wait_gps) else state.error ?: "",
                                Modifier.weight(1.4f),
                            )
                        }
                        if (state.distanceKm in 0.001..0.799) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.ra_done_short),
                                style = MiuixTheme.textStyles.footnote1,
                                color = LxRose,
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LxIconButton(onClick = { locked = !locked }) {
                            Icon(
                                imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = stringResource(R.string.ra_lock),
                                tint = LxInkMuted,
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.paused) {
                                LxButton(text = stringResource(R.string.ra_resume), onClick = viewModel::resume)
                            } else {
                                LxSecondaryButton(text = stringResource(R.string.ra_pause), onClick = viewModel::pause)
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            LxButton(text = stringResource(R.string.ra_finish), onClick = { viewModel.finishRun(onFinished) })
                        }
                    }
                }
            }
            if (locked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.background.copy(alpha = 0.55f))
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { locked = false }) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.ra_lock_hint),
                        style = MiuixTheme.textStyles.title3,
                        color = LxInk,
                    )
                }
            }
        }
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold, color = LxInk, maxLines = 1)
    }
}

/* ----------------------------------------------------------------------------------
 * 运动记录 / 明细 / 维护记录
 * -------------------------------------------------------------------------------- */

private val RECORD_TABS = listOf(R.string.sr_tab_all, R.string.sr_tab_self, R.string.sr_tab_extra, R.string.sr_tab_group)

@Composable
fun SportsRecordsScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenAppeals: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SportsRecordsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        topBar = {
            LxTopBar(
                title = stringResource(R.string.sr_title_records),
                onBack = onBack,
                actions = {
                    Text(
                        text = stringResource(R.string.sr_title_maintain),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier
                            .clickable(onClick = onOpenAppeals)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TotalsCard(uiState.totals)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RECORD_TABS.forEachIndexed { index, label ->
                    LxFilterChip(stringResource(label), uiState.tab == index) { viewModel.selectTab(index) }
                }
            }
            MonthPager(uiState.monthLabel, viewModel::shiftMonth)
            when {
                uiState.isLoading && uiState.rows.isEmpty() -> LxLoading(modifier = Modifier.fillMaxSize())
                uiState.error != null && uiState.rows.isEmpty() -> LxError(uiState.error ?: "", viewModel::reload)
                uiState.rows.isEmpty() -> LxEmpty(stringResource(R.string.sr_no_data))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(uiState.rows, key = { "${it.id}-${it.startText}" }) { row ->
                        RecordListItem(row) { onOpenDetail(row.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(totals: com.linxin.feature.sports.domain.RecordTotals?) {
    LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%.2f".format(totals?.totalMileKm ?: 0.0),
                style = MiuixTheme.textStyles.headline2.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = LxInk,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.sr_unit_km) + " " + stringResource(R.string.sr_total_caption),
                style = MiuixTheme.textStyles.body2,
                color = LxInkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat(stringResource(R.string.sr_group_class), "${totals?.groupCount ?: 0}", stringResource(R.string.sr_unit_times), Modifier.weight(1f))
            Stat(stringResource(R.string.sr_extra_class), "%.2f".format(totals?.extraMileKm ?: 0.0), stringResource(R.string.sr_unit_km), Modifier.weight(1f))
            Stat(stringResource(R.string.sr_morning_run), "%.2f".format(totals?.morningMileKm ?: 0.0), stringResource(R.string.sr_unit_km), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold, color = LxInk)
            Spacer(Modifier.width(2.dp))
            Text(text = unit, style = MiuixTheme.textStyles.footnote1, color = LxInkMuted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(text = label, style = MiuixTheme.textStyles.footnote1, color = LxInkMuted)
    }
}

@Composable
private fun MonthPager(label: String, onShift: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LxIconButton(onClick = { onShift(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.sr_month_prev), tint = LxInk)
        }
        Text(text = label, style = MiuixTheme.textStyles.body1, color = LxInk, modifier = Modifier.padding(horizontal = 20.dp))
        LxIconButton(onClick = { onShift(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.sr_month_next), tint = LxInkMuted)
        }
    }
}

@Composable
private fun RecordListItem(row: RecordRow, onClick: () -> Unit) {
    LxCard(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
                modifier = Modifier.weight(1f),
            )
            StateChip(row.state)
        }
        Spacer(Modifier.height(4.dp))
        Text(text = row.timeRange, style = MiuixTheme.textStyles.footnote1, color = LxInkMuted)
        Text(
            text = stringResource(R.string.sr_run_mile) + "%.2f".format(row.mileKm) + stringResource(R.string.sr_unit_km),
            style = MiuixTheme.textStyles.body2,
            color = LxInk,
        )
    }
}

@Composable
private fun StateChip(state: RecordState) {
    val color = when (state) {
        RecordState.VALID -> LxSage
        RecordState.INVALID -> LxRose
        else -> LxInkMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(state.label(), style = MiuixTheme.textStyles.footnote2, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SportsDetailScreen(
    onBack: () -> Unit,
    onOpenAppeals: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SportsDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val detail = uiState.detail
    Scaffold(
        modifier = modifier,
        topBar = {
            LxTopBar(
                title = stringResource(R.string.sr_title_detail),
                onBack = onBack,
                actions = {
                    Text(
                        text = stringResource(R.string.sr_title_maintain),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier
                            .clickable(onClick = onOpenAppeals)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize().padding(padding))
            detail == null -> LxError(uiState.error ?: "", viewModel::reload, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item {
                    LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = detail.titleText,
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.SemiBold,
                                color = LxInk,
                                modifier = Modifier.weight(1f),
                            )
                            StateChip(detail.state)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Stat(stringResource(R.string.sr_col_mile), "%.2f".format(detail.mileKm), "", Modifier.weight(1f))
                            Stat(stringResource(R.string.sr_col_speed), "%.2f".format(detail.speedKmh), "", Modifier.weight(1f))
                            Stat(stringResource(R.string.sr_col_duration), detail.timeText.ifBlank { "--" }, "", Modifier.weight(1f))
                        }
                    }
                }
                if (detail.remark.isNotBlank()) {
                    item {
                        LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = stringResource(R.string.sr_remark) + detail.remark,
                                style = MiuixTheme.textStyles.body2,
                                color = if (detail.showsRemark) LxRose else LxInkMuted,
                            )
                        }
                    }
                }
                if (detail.trackBd09.size >= 2) {
                    item {
                        LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = stringResource(R.string.sr_track),
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.SemiBold,
                                color = LxInk,
                            )
                            Spacer(Modifier.height(8.dp))
                            // 库里那条是百度 BD-09，我们的瓦片是高德网格，先换回 WGS-84 再交给地图
                            LiveTrackMap(
                                points = detail.trackBd09.map {
                                    val wgs = SportsCoords.bdToWgs(it.latitude, it.longitude)
                                    WgsPoint(wgs.latitude, wgs.longitude)
                                },
                                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)),
                                markLast = true,
                                follow = false,
                            )
                        }
                    }
                }
                if (detail.showsHandle && (detail.handleTime.isNotBlank() || detail.handleUser.isNotBlank())) {
                    item { InfoCard(stringResource(R.string.sr_handle_info), detail.handleTime, detail.handleUser, detail.handleRemark) }
                }
                if (detail.applyTime.isNotBlank() || detail.applyReason.isNotBlank()) {
                    item { InfoCard(stringResource(R.string.sr_apply_info), detail.applyTime, detail.applyReason, "") }
                }
                if (detail.auditTime.isNotBlank() || detail.auditUser.isNotBlank()) {
                    item {
                        LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = stringResource(R.string.sr_audit_info),
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.SemiBold,
                                color = LxInk,
                            )
                            LxDetailRow(stringResource(R.string.sr_audit_time), detail.auditTime)
                            LxDetailRow(stringResource(R.string.sr_audit_user), detail.auditUser)
                            LxDetailRow(
                                stringResource(R.string.sr_audit_opinion),
                                when (detail.auditState) {
                                    "1" -> "同意"
                                    "2" -> "驳回"
                                    "0" -> "处理中"
                                    else -> ""
                                },
                                showDivider = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, time: String, who: String, note: String) {
    LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.SemiBold, color = LxInk)
        LxDetailRow(stringResource(R.string.sr_time_label), time)
        LxDetailRow(stringResource(R.string.sr_who_label), who)
        LxDetailRow(stringResource(R.string.sr_remark_short), note, showDivider = false)
    }
}

@Composable
fun SportsAppealsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SportsAppealsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val rows = uiState.page?.rows.orEmpty()
    Scaffold(
        modifier = modifier,
        topBar = { LxTopBar(stringResource(R.string.sr_title_maintain), onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "" to stringResource(R.string.sr_tab_all),
                    "0" to stringResource(R.string.sr_state_processing),
                    "1" to stringResource(R.string.sr_audit_agree),
                    "2" to stringResource(R.string.sr_audit_reject),
                ).forEach { (code, label) ->
                    LxFilterChip(label, uiState.auditState == code) { viewModel.selectAudit(code) }
                }
            }
            MonthPager(uiState.monthLabel, viewModel::shiftMonth)
            when {
                uiState.isLoading && rows.isEmpty() -> LxLoading(modifier = Modifier.fillMaxSize())
                uiState.error != null && rows.isEmpty() -> LxError(uiState.error ?: "", viewModel::reload)
                rows.isEmpty() -> LxEmpty(stringResource(R.string.sr_no_data))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(rows, key = { "${it.id}-${it.timeText}" }) { row ->
                        LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(row.timeText, style = MiuixTheme.textStyles.body2, color = LxInk, modifier = Modifier.weight(1f))
                                Text(row.auditLabel, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
                            }
                            Text(
                                text = stringResource(R.string.sr_mile_label) + " %.2f".format(row.mileKm) +
                                    stringResource(R.string.sr_unit_km) + "    " +
                                    stringResource(R.string.sr_run_speed) + " %.2f".format(row.speedKmh) + "km/h",
                                style = MiuixTheme.textStyles.footnote1,
                                color = LxInkMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun fmt(value: Double): String = when {
    value <= 0.0 -> "—"
    value % 1.0 == 0.0 -> value.toInt().toString()
    else -> String.format(java.util.Locale.CHINA, "%.1f", value)
}
