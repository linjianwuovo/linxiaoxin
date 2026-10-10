package com.linxin.feature.checkin.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.linxin.core.designsystem.theme.LxCream
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.designsystem.theme.LxShapes
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkGhost
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSuccess
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.designsystem.theme.LxWarning
import com.linxin.feature.checkin.domain.CheckinDay
import com.linxin.feature.checkin.domain.CheckinTask
import com.linxin.feature.checkin.domain.MonthStatics
import com.linxin.feature.holiday.domain.HolidayHistory
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
                message = stringResource(R.string.checkin_empty),
                modifier = Modifier.padding(padding),
            )
            else -> TaskList(
                uiState = uiState,
                onTaskClick = onTaskClick,
                onLoadMore = viewModel::loadMore,
                onRetryTasks = viewModel::retry,
                onRetrySubject = viewModel::retrySubject,
                onRetrySummary = viewModel::retrySummary,
                onSelectDate = viewModel::selectDate,
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
    onSelectDate: (String?) -> Unit,
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

    val selectedDate = uiState.selectedDate
    // 点中某天但任务还没翻到：要么正在翻页，要么已经翻过上限/翻到底了
    val dayPending = selectedDate != null && !uiState.hasSelectedDay && uiState.isLoadingMore
    val dayMissing = selectedDate != null && !uiState.hasSelectedDay && !uiState.isLoadingMore

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 本月概览：统计 + 签到日历（有红点的日期可以点，点中就筛那天）
        val summaryError = uiState.summaryError
        if (summaryError != null) {
            item(key = "overview_error") {
                LxInlineErrorCard(message = summaryError, onRetry = onRetrySummary)
            }
        } else if (uiState.statics != null || uiState.days.isNotEmpty()) {
            item(key = "overview") {
                MonthOverviewCard(
                    statics = uiState.statics,
                    days = uiState.days,
                    monthLabel = uiState.monthLabel,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                )
            }
        }

        if (selectedDate != null) {
            item(key = "day_filter_bar") {
                DayFilterBar(
                    date = selectedDate,
                    pending = dayPending,
                    missing = dayMissing,
                    onClear = { onSelectDate(null) },
                )
            }
        }

        // 那天的离校/返校登记：查寝列表里没有他的任务，不代表这天什么都没发生
        if (selectedDate != null && uiState.visibleLeaves.isNotEmpty()) {
            item(key = "section_leave") {
                SectionHeader(stringResource(R.string.checkin_section_leave))
            }
            items(uiState.visibleLeaves, key = { "leave_${'$'}{it.holidayId}" }) { leaf ->
                LeaveDayCard(leaf = leaf, modifier = Modifier.animateItem())
            }
        }

        // 查寝签到 section
        if (uiState.visibleTasks.isNotEmpty() || uiState.error != null) {
            item(key = "section_checkin") {
                SectionHeader(stringResource(R.string.title_dorm_checkin))
            }
            uiState.error?.let { tasksError ->
                item(key = "checkin_error") {
                    LxInlineErrorCard(message = tasksError, onRetry = onRetryTasks)
                }
            }
            items(uiState.visibleTasks, key = { "c_${it.taskDateId}" }) { task ->
                TaskCard(
                    task = task,
                    onClick = { if (!task.isSigned) onTaskClick(task.taskDateId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        // 主题签到 section
        if (uiState.visibleSubjectTasks.isNotEmpty() || uiState.subjectError != null) {
            item(key = "section_subject") {
                SectionHeader(stringResource(R.string.checkin_section_theme))
            }
            uiState.subjectError?.let { subjectError ->
                item(key = "subject_error") {
                    LxInlineErrorCard(message = subjectError, onRetry = onRetrySubject)
                }
            }
            items(uiState.visibleSubjectTasks, key = { "s_${it.taskDateId}" }) { task ->
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
                    text = task.taskName.ifBlank { stringResource(R.string.title_dorm_checkin) },
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
    val text = statusText.ifBlank {
        if (isSigned) stringResource(R.string.checkin_state_signed)
        else stringResource(R.string.checkin_state_unsigned)
    }
    // 配色只认服务端原文：拿翻译后的 text 去比 "已签到"，英文界面下会全部落到默认档。
    val color = when {
        isSigned || statusText == "已签到" -> LxSuccess
        statusText == "未开始" || statusText == "签到中" -> LxWarning
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
    selectedDate: String?,
    onSelectDate: (String?) -> Unit,
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
                text = month?.let { stringResource(R.string.checkin_month_overview, it.year, it.monthValue) }
                ?: stringResource(R.string.checkin_overview),
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )

            statics?.let { data ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell(stringResource(R.string.checkin_stat_signed), data.signed, LxSuccess, Modifier.weight(1f))
                    StatCell(stringResource(R.string.checkin_stat_unsigned), data.notSigned, LxTerra, Modifier.weight(1f))
                    StatCell(stringResource(R.string.checkin_stat_in_progress), data.inProgress, LxWarning, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell(stringResource(R.string.checkin_stat_leave), data.leave, LxInkMuted, Modifier.weight(1f))
                    StatCell(stringResource(R.string.checkin_stat_off_campus), data.offCampus, LxInkMuted, Modifier.weight(1f))
                    StatCell(stringResource(R.string.checkin_stat_qr), data.qrCode, LxInkMuted, Modifier.weight(1f))
                }
            }

            if (month != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = LxCardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                    stringResource(R.string.checkin_wday_1), stringResource(R.string.checkin_wday_2),
                    stringResource(R.string.checkin_wday_3), stringResource(R.string.checkin_wday_4),
                    stringResource(R.string.checkin_wday_5), stringResource(R.string.checkin_wday_6),
                    stringResource(R.string.checkin_wday_7),
                ).forEach { label ->
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
                                    val date = month.atDay(dayNum)
                                    val dateKey = date.toString()
                                    TaskDayCell(
                                        day = dayNum,
                                        hasTask = dayNum in markedDays,
                                        isToday = date == today,
                                        selected = selectedDate == dateKey,
                                        onClick = {
                                            // 再点一次取消筛选；没任务的格子不给点，避免点了看半天空白
                                            if (dayNum in markedDays) {
                                                onSelectDate(if (selectedDate == dateKey) null else dateKey)
                                            }
                                        },
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
private fun TaskDayCell(
    day: Int,
    hasTask: Boolean,
    isToday: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (hasTask) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                selected -> Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(LxTerra),
                )
                isToday -> Box(
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
                    selected -> Color.White
                    hasTask -> LxInk
                    else -> LxInkMuted
                },
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        // 圆点只作"这天有任务"的标记；选中时格子已经整块反色，点就省掉，但占位保留避免跳动
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (hasTask && !selected) LxTerra else Color.Transparent),
        )
    }
}

/** 点中某天后顶在列表上方的筛选条：日期 + 翻页状态 + 取消筛选 */
@Composable
private fun DayFilterBar(
    date: String,
    pending: Boolean,
    missing: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(LxShapes.small)
            .background(LxCream)
            .border(width = 1.dp, color = LxSandDeep, shape = LxShapes.small)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (parsed != null) {
                    stringResource(R.string.checkin_day_tasks, parsed.monthValue, parsed.dayOfMonth)
                } else {
                    date
                },
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            when {
                pending -> Text(
                    text = stringResource(R.string.checkin_finding_day),
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                )
                missing -> Text(
                    text = stringResource(R.string.checkin_day_none),
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                )
            }
        }
        Text(
            text = stringResource(R.string.checkin_show_all),
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            color = LxTerra,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClear() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** 那天的离校登记卡片：只陈述区间，不提供跳转（跳转要动导航图，等需要时再加） */
@Composable
private fun LeaveDayCard(
    leaf: HolidayHistory,
    modifier: Modifier = Modifier,
) {
    LxCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.checkin_day_leave, leaf.name.ifBlank { stringResource(R.string.checkin_section_leave) }),
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.checkin_day_leave_range,
                    leaf.startDate.trim().substringBefore(' '),
                    leaf.returnStartDate.trim().substringBefore(' '),
                ),
                style = MiuixTheme.textStyles.footnote2,
                color = LxInkMuted,
            )
        }
    }
}
