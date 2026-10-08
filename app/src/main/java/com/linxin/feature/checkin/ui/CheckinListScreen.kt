package com.linxin.feature.checkin.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxInlineErrorCard
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxCardBorder
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkGhost
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSuccess
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.designsystem.theme.LxWarning
import com.linxin.feature.checkin.domain.CheckinDay
import com.linxin.feature.checkin.domain.CheckinTask
import com.linxin.feature.checkin.domain.MonthStatics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CheckinListScreen(
    onBack: () -> Unit,
    onTaskClick: (taskDateId: String) -> Unit,
    shouldRefresh: Boolean,
    onRefreshConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val error = uiState.error

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_dorm_checkin), onBack = onBack) },
    ) { padding ->
        val tasksDead = error != null &&
            uiState.subjectTasks.isEmpty() && uiState.statics == null && uiState.days.isEmpty()
        when {
            uiState.isPreparing -> LxLoading(modifier = Modifier.padding(padding))
            tasksDead -> LxError(
                message = error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            uiState.hasNothing -> LxEmpty(
                message = "暂无签到任务",
                modifier = Modifier.padding(padding),
            )
            else -> TaskList(
                uiState = uiState,
                onTaskClick = onTaskClick,
                onLoadMore = viewModel::loadMore,
                onRetryTasks = viewModel::retry,
                onRetrySubject = viewModel::retrySubject,
                onRetrySummary = viewModel::retrySummary,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun TaskList(
    uiState: CheckinUiState,
    onTaskClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryTasks: () -> Unit,
    onRetrySubject: () -> Unit,
    onRetrySummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3 && uiState.hasMore && !uiState.isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 本月概览：统计 + 签到日历
        val summaryError = uiState.summaryError
        if (summaryError != null) {
            item(key = "overview_error") {
                LxInlineErrorCard(message = summaryError, onRetry = onRetrySummary)
            }
        } else if (uiState.statics != null || uiState.days.isNotEmpty()) {
            item(key = "overview") {
                MonthOverviewCard(statics = uiState.statics, days = uiState.days, monthLabel = uiState.monthLabel)
            }
        }

        // 查寝签到 section
        if (uiState.tasks.isNotEmpty() || uiState.error != null) {
            item(key = "section_checkin") {
                SectionHeader("查寝签到")
            }
            uiState.error?.let { tasksError ->
                item(key = "checkin_error") {
                    LxInlineErrorCard(message = tasksError, onRetry = onRetryTasks)
                }
            }
            items(uiState.tasks, key = { "c_${it.taskDateId}" }) { task ->
                TaskCard(
                    task = task,
                    onClick = { if (!task.isSigned) onTaskClick(task.taskDateId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        // 主题签到 section
        if (uiState.subjectTasks.isNotEmpty() || uiState.subjectError != null) {
            item(key = "section_subject") {
                SectionHeader("主题签到")
            }
            uiState.subjectError?.let { subjectError ->
                item(key = "subject_error") {
                    LxInlineErrorCard(message = subjectError, onRetry = onRetrySubject)
                }
            }
            items(uiState.subjectTasks, key = { "s_${it.taskDateId}" }) { task ->
                TaskCard(
                    task = task,
                    onClick = { if (!task.isSigned) onTaskClick(task.taskDateId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        // 节假日登记与历史登记已挪到独立的「节假日离返校」页（Routes.HOLIDAY_LIST），
        // 和安小信一致：查寝和节假日是学工应用里两个互不相干的入口。

        if (uiState.isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: CheckinTask,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LxCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.taskName.ifBlank { "查寝签到" },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = task.startTime,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (task.endTime.isNotBlank()) {
                        Text(
                            text = "~ ${task.endTime}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 签到状态标记
            StatusBadge(isSigned = task.isSigned, statusText = task.statusText)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MiuixTheme.textStyles.button,
        fontWeight = FontWeight.SemiBold,
        color = LxInkMuted,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun StatusBadge(isSigned: Boolean, statusText: String = "") {
    // 服务端原文优先：未开始 ≠ 未签到，混成一档会误导
    val text = statusText.ifBlank { if (isSigned) "已签到" else "未签到" }
    val color = when {
        isSigned || text == "已签到" -> LxSuccess
        text == "未开始" || text == "签到中" -> LxWarning
        else -> MiuixTheme.colorScheme.secondary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun MonthOverviewCard(
    statics: MonthStatics?,
    days: List<CheckinDay>,
    monthLabel: String,
    modifier: Modifier = Modifier,
) {
    val month = remember(monthLabel) {
        runCatching { YearMonth.parse(monthLabel) }.getOrNull()
    }
    val today = remember { LocalDate.now() }
    val markedDays = remember(days, month) {
        if (month == null) emptySet()
        else days.mapNotNull { day ->
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
            if (day.taskNum > 0 && date != null && YearMonth.from(date) == month) date.dayOfMonth else null
        }.toSet()
    }

    LxCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = month?.let { "${it.year} 年 ${it.monthValue} 月签到概览" } ?: "本月签到概览",
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )

            statics?.let { data ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("已签", data.signed, LxSuccess, Modifier.weight(1f))
                    StatCell("未签", data.notSigned, LxTerra, Modifier.weight(1f))
                    StatCell("签到中", data.inProgress, LxWarning, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("请假", data.leave, LxInkMuted, Modifier.weight(1f))
                    StatCell("离校", data.offCampus, LxInkMuted, Modifier.weight(1f))
                    StatCell("扫码", data.qrCode, LxInkMuted, Modifier.weight(1f))
                }
            }

            if (month != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = LxCardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MiuixTheme.textStyles.footnote2,
                            color = LxInkMuted,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                // 与节假日选择器一致：周一起排
                val startOffset = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
                val daysInMonth = month.lengthOfMonth()
                val totalCells = startOffset + daysInMonth
                val rows = (totalCells + 6) / 7

                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val dayNum = row * 7 + col - startOffset + 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (dayNum in 1..daysInMonth) {
                                    TaskDayCell(
                                        day = dayNum,
                                        hasTask = dayNum in markedDays,
                                        isToday = month.atDay(dayNum) == today,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) color else LxInkGhost,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = LxInkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TaskDayCell(day: Int, hasTask: Boolean, isToday: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)),
                )
            }
            Text(
                text = day.toString(),
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = if (isToday || hasTask) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    hasTask -> LxInk
                    else -> LxInkMuted
                },
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (hasTask) LxTerra else Color.Transparent),
        )
    }
}
