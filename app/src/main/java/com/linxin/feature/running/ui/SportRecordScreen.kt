package com.linxin.feature.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDetailRow
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxIconButton
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxSage
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxRose
import com.linxin.core.location.CoordinateConverter
import com.linxin.core.map.LiveTrackMap
import com.linxin.core.map.prepareOsmdroid
import com.linxin.feature.running.domain.RunRecordRow
import com.linxin.feature.running.domain.RunRecordState
import com.linxin.feature.running.domain.TrackPoint
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 官方底部那四个页签：queryType 就是它的序号 */
private val RECORD_TABS = listOf(
    R.string.sr_tab_all,
    R.string.sr_tab_self,
    R.string.sr_tab_extra,
    R.string.sr_tab_group,
)

@Composable
fun SportRecordScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenMaintain: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SportRecordViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

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
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenMaintain)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TotalHeader(state.totals)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RECORD_TABS.forEachIndexed { index, label ->
                    LxFilterChip(
                        label = stringResource(label),
                        selected = state.tab == index,
                        onClick = { viewModel.selectTab(index) },
                    )
                }
            }
            MonthPager(
                label = state.monthLabel,
                canGoNext = false,
                onPrev = { viewModel.shiftMonth(-1) },
                onNext = { viewModel.shiftMonth(1) },
            )
            when {
                state.isLoading && state.rows.isEmpty() -> LxLoading(modifier = Modifier.fillMaxSize())
                state.error != null && state.rows.isEmpty() -> LxError(
                    message = state.error ?: "",
                    onRetry = viewModel::reload,
                )
                state.rows.isEmpty() -> LxEmpty(message = stringResource(R.string.sr_no_data))
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.rows, key = { "${it.id}-${it.startText}" }) { row ->
                        RecordRow(row = row, onClick = { onOpenDetail(row.id) })
                    }
                    item {
                        Text(
                            text = stringResource(R.string.sr_bottom_line),
                            style = MiuixTheme.textStyles.footnote2,
                            color = LxInkMuted,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalHeader(totals: com.linxin.feature.running.domain.RunTotals?) {
    LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%.2f".format(totals?.totalMileKm ?: 0.0),
                fontWeight = FontWeight.Bold,
                color = LxInk,
                style = MiuixTheme.textStyles.headline2.copy(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.sr_unit_km),
                style = MiuixTheme.textStyles.body2,
                color = LxInkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.sr_total_caption),
                style = MiuixTheme.textStyles.body2,
                color = LxInkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell(
                label = stringResource(R.string.sr_group_class),
                value = "${totals?.groupCount ?: 0}",
                unit = stringResource(R.string.sr_unit_times),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(R.string.sr_extra_class),
                value = "%.2f".format(totals?.extraMileKm ?: 0.0),
                unit = stringResource(R.string.sr_unit_km),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(R.string.sr_morning_run),
                value = "%.2f".format(totals?.morningMileKm ?: 0.0),
                unit = stringResource(R.string.sr_unit_km),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = unit,
                style = MiuixTheme.textStyles.footnote1,
                color = LxInkMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = LxInkMuted,
        )
    }
}

@Composable
private fun MonthPager(label: String, canGoNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LxIconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.sr_month_prev),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = LxInk,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LxIconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.sr_month_next),
                tint = if (canGoNext) MiuixTheme.colorScheme.onSurface else LxInkMuted,
            )
        }
    }
}

@Composable
private fun RecordRow(row: RunRecordRow, onClick: () -> Unit) {
    LxCard(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name.ifBlank { stringResource(R.string.sr_running) },
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
                modifier = Modifier.weight(1f),
            )
            RunStateChip(row.state)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = row.timeRangeText,
            style = MiuixTheme.textStyles.footnote1,
            color = LxInkMuted,
        )
        Spacer(Modifier.height(4.dp))
        val mile = "%.2f".format(row.mileKm).trimEnd('0').trimEnd('.')
        Text(
            text = stringResource(R.string.sr_run_mile) + mile + stringResource(R.string.sr_unit_km) +
                if (row.coefficient.isNotBlank() && row.coefficient != "1") " * ${row.coefficient}" else "",
            style = MiuixTheme.textStyles.body2,
            color = LxInk,
        )
    }
}

@Composable
fun RunStateChip(state: RunRecordState) {
    val (color, label) = when (state) {
        RunRecordState.VALID -> LxSage to stringResource(R.string.sr_state_valid)
        RunRecordState.INVALID -> LxRose to stringResource(R.string.sr_state_invalid)
        RunRecordState.PROCESSING -> LxInkMuted to stringResource(R.string.sr_state_processing)
        RunRecordState.UNKNOWN -> LxInkMuted to stringResource(R.string.sr_state_unknown)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = label, style = MiuixTheme.textStyles.footnote2, color = color, fontWeight = FontWeight.Medium)
    }
}

/** 运动明细：官方那一屏的字段顺序，一个不多一个不少 */
@Composable
fun RunDetailScreen(
    onBack: () -> Unit,
    onOpenMaintain: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val detail = state.detail

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
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenMaintain)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LxLoading(modifier = Modifier.fillMaxSize().padding(padding))
            detail == null -> LxError(
                message = state.error ?: stringResource(R.string.sr_detail_failed),
                onRetry = viewModel::reload,
                modifier = Modifier.padding(padding),
            )
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
                            RunStateChip(detail.state)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatCell(
                                label = stringResource(R.string.sr_col_mile),
                                value = "%.0f".format(detail.mileKm),
                                unit = "",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = stringResource(R.string.sr_col_speed),
                                value = "%.2f".format(detail.speedKmh),
                                unit = "",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = stringResource(R.string.sr_col_duration),
                                value = detail.durationText.ifBlank { "--" },
                                unit = "",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (detail.showsRemark || detail.remark.isNotBlank()) {
                    item {
                        LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = stringResource(R.string.sr_remark) + detail.remark,
                                style = MiuixTheme.textStyles.body2,
                                color = LxInk,
                            )
                        }
                    }
                }
                if (detail.trackPoints.size >= 2) {
                    item {
                        LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = stringResource(R.string.sr_track),
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.SemiBold,
                                color = LxInk,
                            )
                            Spacer(Modifier.height(8.dp))
                            // 库里存的是百度 BD-09，我们那套瓦片是高德 GCJ-02，画之前得换一次系
                            LiveTrackMap(
                                points = detail.trackPoints.map {
                                    // 库里 BD-09 → GCJ-02 → WGS-84，LiveTrackMap 收的是 WGS-84，它自己再换回 GCJ 画
                                    val gcj = CoordinateConverter.bd09ToGcj02(it.latitude, it.longitude)
                                    val wgs = CoordinateConverter.gcj02ToWgs84(gcj.latitude, gcj.longitude)
                                    TrackPoint(wgs.latitude, wgs.longitude)
                                },
                                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)),
                                markLast = true,
                                follow = false,
                            )
                            Text(
                                text = "${detail.trackPoints.size} 个点",
                                style = MiuixTheme.textStyles.footnote2,
                                color = LxInkMuted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                if (detail.showsHandled && (detail.handledAt.isNotBlank() || detail.handledBy.isNotBlank())) {
                    item { SectionCard(stringResource(R.string.sr_handle_info), detail.handledAt, detail.handledBy, detail.handleRemark) }
                }
                if (detail.appliedAt.isNotBlank() || detail.applyReason.isNotBlank()) {
                    item { SectionCard(stringResource(R.string.sr_apply_info), detail.appliedAt, detail.applyReason, "") }
                }
                if (detail.auditedAt.isNotBlank() || detail.auditor.isNotBlank() || detail.auditOpinion.isNotBlank()) {
                    item {
                        LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = stringResource(R.string.sr_audit_info),
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.SemiBold,
                                color = LxInk,
                            )
                            Spacer(Modifier.height(4.dp))
                            LxDetailRow(label = stringResource(R.string.sr_audit_time), value = detail.auditedAt)
                            LxDetailRow(label = stringResource(R.string.sr_audit_user), value = detail.auditor)
                            LxDetailRow(label = stringResource(R.string.sr_audit_opinion), value = detail.auditOpinion)
                            LxDetailRow(label = stringResource(R.string.sr_remark_short), value = detail.auditRemark, showDivider = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, time: String, who: String, note: String) {
    LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            color = LxInk,
        )
        Spacer(Modifier.height(4.dp))
        LxDetailRow(label = stringResource(R.string.sr_time_label), value = time)
        LxDetailRow(label = stringResource(R.string.sr_who_label), value = who)
        LxDetailRow(label = stringResource(R.string.sr_remark_short), value = note, showDivider = false)
    }
}

/** 考勤维护记录 */
@Composable
fun MaintainRecordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MaintainRecordViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val rows = state.page?.rows.orEmpty()

    Scaffold(
        modifier = modifier,
        topBar = { LxTopBar(title = stringResource(R.string.sr_title_maintain), onBack = onBack) },
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
                    LxFilterChip(
                        label = label,
                        selected = state.auditState == code,
                        onClick = { viewModel.selectAudit(code) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    0 to stringResource(R.string.sr_tab_self),
                    1 to stringResource(R.string.sr_group_class),
                    2 to stringResource(R.string.sr_extra_class),
                ).forEach { (index, label) ->
                    LxFilterChip(
                        label = label,
                        selected = state.tab == index,
                        onClick = { viewModel.selectTab(index) },
                    )
                }
            }
            MonthPager(
                label = state.monthLabel,
                canGoNext = false,
                onPrev = { viewModel.shiftMonth(-1) },
                onNext = { viewModel.shiftMonth(1) },
            )
            when {
                state.isLoading && rows.isEmpty() -> LxLoading(modifier = Modifier.fillMaxSize())
                state.error != null && rows.isEmpty() -> LxError(
                    message = state.error ?: "",
                    onRetry = viewModel::reload,
                )
                rows.isEmpty() -> LxEmpty(message = stringResource(R.string.sr_no_data))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(rows, key = { "${it.id}-${it.timeText}" }) { row ->
                        LxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = row.timeText,
                                    style = MiuixTheme.textStyles.body2,
                                    color = LxInk,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            (if (row.auditState == "1") LxSage else if (row.auditState == "2") LxRose else LxInkMuted)
                                                .copy(alpha = 0.12f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = row.auditLabel,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = if (row.auditState == "1") LxSage else if (row.auditState == "2") LxRose else LxInkMuted,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.sr_mile_label) + " ${"%.2f".format(row.mileKm)}${stringResource(R.string.sr_unit_km)}" +
                                    "    " + stringResource(R.string.sr_run_speed) + " ${"%.2f".format(row.speedKmh)}km/h",
                                style = MiuixTheme.textStyles.footnote1,
                                color = LxInkMuted,
                            )
                            if (row.reason.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.sr_apply_reason) + row.reason,
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
}

