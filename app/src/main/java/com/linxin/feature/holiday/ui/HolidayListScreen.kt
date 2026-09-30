package com.linxin.feature.holiday.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxInlineErrorCard
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSuccess
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.designsystem.theme.LxWarning
import com.linxin.feature.holiday.domain.HolidayHistory
import com.linxin.feature.holiday.domain.HolidayTask
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 节假日离返校：对齐安小信的页内结构，「去登记」和「历史登记」两个标签。
 * 之前这两块是塞在查寝列表里的分组。
 */
@Composable
fun HolidayListScreen(
    onBack: () -> Unit,
    onHolidayClick: (holidayId: String) -> Unit,
    shouldRefresh: Boolean,
    onRefreshConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HolidayListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.loadAll()
            onRefreshConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = "节假日离返校", onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                tabs = listOf("去登记", "历史登记"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            when {
                uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize())
                selectedTab == 0 -> RegisterTab(
                    tasks = uiState.tasks,
                    error = uiState.error,
                    onRetry = viewModel::loadAll,
                    onHolidayClick = onHolidayClick,
                )
                else -> HistoryTab(
                    history = uiState.history,
                    error = uiState.historyError,
                    onRetry = viewModel::loadAll,
                )
            }
        }
    }
}

@Composable
private fun RegisterTab(
    tasks: List<HolidayTask>,
    error: String?,
    onRetry: () -> Unit,
    onHolidayClick: (String) -> Unit,
) {
    when {
        error != null -> LxError(
            message = error,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        tasks.isEmpty() -> LxEmpty(
            message = "当前没有需要登记的节假日",
            modifier = Modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tasks, key = { "h_${it.holidayId}" }) { task ->
                HolidayTaskCard(
                    task = task,
                    onClick = { onHolidayClick(task.holidayId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<HolidayHistory>,
    error: String?,
    onRetry: () -> Unit,
) {
    when {
        history.isEmpty() && error != null -> LxError(
            message = error,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        history.isEmpty() -> LxEmpty(
            message = "还没有历史登记记录",
            modifier = Modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) {
                item(key = "history_error") {
                    LxInlineErrorCard(message = error, onRetry = onRetry)
                }
            }
            items(history, key = { "hh_${it.holidayId}" }) { record ->
                HistoryCard(record = record, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
fun HolidayTaskCard(
    task: HolidayTask,
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
                    text = task.name.ifBlank { "节假日登记" },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = task.registerStartDate,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (task.registerEndDate.isNotBlank()) {
                        Text(
                            text = "~ ${task.registerEndDate}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            HolidayStatusBadge(isRegistered = task.isRegistered)
        }
    }
}

@Composable
fun HistoryCard(record: HolidayHistory, modifier: Modifier = Modifier) {
    LxCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name.ifBlank { "节假日登记" },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val period = listOf(record.startDate, record.returnStartDate)
                    .filter { it.isNotBlank() }
                    .joinToString(" ~ ")
                if (period.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = period,
                        style = MiuixTheme.textStyles.footnote2,
                        color = LxInkMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = if (record.returnStartDate.isBlank()) "待返校" else "已完成",
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.SemiBold,
                color = if (record.returnStartDate.isBlank()) LxWarning else LxInkMuted,
            )
        }
    }
}

@Composable
private fun HolidayStatusBadge(isRegistered: Boolean) {
    val color = if (isRegistered) LxSuccess else LxTerra
    val text = if (isRegistered) "已登记" else "待登记"

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
