package com.linxin.feature.exam.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults

import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.RLg
import com.linxin.feature.exam.domain.ExamScore

@Composable
fun ExamScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = "考试成绩", onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.scores.isEmpty() -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            else -> ExamContent(
                uiState = uiState,
                onYearSelected = viewModel::onYearSelected,
                onSemesterSelected = viewModel::onSemesterSelected,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ExamContent(
    uiState: ExamUiState,
    onYearSelected: (String) -> Unit,
    onSemesterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val semesters = remember { listOf("1" to "第一学期", "2" to "第二学期") }
    val yearOptions = uiState.schoolYears.map { it.value to it.display }
    // null = 没弹；"year"/"semester" = 正在选哪个
    var picking by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "selectors") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SelectorField(
                        value = yearOptions.firstOrNull { it.first == uiState.selectedYear }?.second
                            ?: uiState.selectedYear,
                        onClick = { picking = PICK_YEAR },
                        modifier = Modifier.weight(1f),
                    )
                    SelectorField(
                        value = semesters.firstOrNull { it.first == uiState.selectedSemester }?.second
                            ?: "第${uiState.selectedSemester}学期",
                        onClick = { picking = PICK_SEMESTER },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (uiState.isScoresLoading) {
                item(key = "loading") { LxLoading() }
            } else if (uiState.scores.isEmpty()) {
                item(key = "empty") {
                    LxCard {
                        Text(
                            text = "该学期暂无成绩记录",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            } else {
                items(uiState.scores, key = { it.courseCode + it.courseName }) { score ->
                    ScoreCard(score)
                }

                item(key = "summary") {
                    SummaryCard(scores = uiState.scores)
                }
            }
        }

        picking?.let { which ->
            val isYear = which == PICK_YEAR
            val options = if (isYear) yearOptions else semesters
            if (options.isNotEmpty()) {
                OverlayBottomSheet(
                    show = true,
                    title = if (isYear) "选择学年" else "选择学期",
                    onDismissRequest = { picking = null },
                    backgroundColor = MiuixTheme.colorScheme.surface,
                    cornerRadius = RLg,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                    ) {
                        val current = if (isYear) uiState.selectedYear else uiState.selectedSemester
                        options.forEach { (value, label) ->
                            val selected = value == current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(RLg))
                                    .clickable {
                                        picking = null
                                        if (isYear) onYearSelected(value) else onSemesterSelected(value)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MiuixTheme.textStyles.body1,
                                    color = if (selected) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
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

private const val PICK_YEAR = "year"
private const val PICK_SEMESTER = "semester"

/** 只读展示框：外观沿用 Miuix TextField，点它交给覆盖层，避免 TextField 抢焦点 */
@Composable
private fun SelectorField(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            colors = TextFieldDefaults.textFieldColors(backgroundColor = MiuixTheme.colorScheme.surface),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun ScoreCard(score: ExamScore) {
    LxCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = score.courseName,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = score.score,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScoreMetaText("学分: ${score.credit}")
                ScoreMetaText("绩点: ${score.gpa}")
                ScoreMetaText(score.category)
            }
        }
    }
}

@Composable
private fun ScoreMetaText(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun SummaryCard(scores: List<ExamScore>) {
    val totalCredit = scores.sumOf { it.credit.toDoubleOrNull() ?: 0.0 }
    val weightedGpa = scores.sumOf {
        val credit = it.credit.toDoubleOrNull() ?: 0.0
        val gpa = it.gpa.toDoubleOrNull() ?: 0.0
        credit * gpa
    }
    val avgGpa = if (totalCredit > 0) weightedGpa / totalCredit else 0.0

    LxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.1f".format(totalCredit),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "总学分",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.2f".format(avgGpa),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = "平均绩点",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
