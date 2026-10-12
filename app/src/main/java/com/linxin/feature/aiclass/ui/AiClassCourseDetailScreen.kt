package com.linxin.feature.aiclass.ui

import androidx.compose.ui.res.stringResource
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDetailRow
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxSuccess
import com.linxin.feature.aiclass.domain.AiCourse
import com.linxin.feature.aiclass.domain.AiQuiz
import com.linxin.feature.aiclass.domain.displayName
import com.linxin.feature.aiclass.domain.studentCountText

@Composable
fun AiClassCourseDetailScreen(
    classId: String,
    onBack: () -> Unit,
    onOpenHomeworkDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AiClassViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(classId) {
        viewModel.openCourseDetail(classId)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_course_detail), onBack = onBack) },
    ) { padding ->
        val course = uiState.selectedCourse
        when {
            course == null && uiState.isQuizLoading -> LxLoading(modifier = Modifier.padding(padding))
            course == null -> LxError(
                message = uiState.quizError ?: stringResource(R.string.ai_no_course),
                onRetry = { viewModel.openCourseDetail(classId) },
                modifier = Modifier.padding(padding),
            )
            else -> AiClassCourseDetailContent(
                course = course,
                quizList = uiState.quizList,
                isQuizLoading = uiState.isQuizLoading,
                quizError = uiState.quizError,
                onRetryQuiz = viewModel::retryQuiz,
                homeworkList = uiState.homeworkList,
                coursewareList = uiState.coursewareList,
                isCoursewareLoading = uiState.isCoursewareLoading,
                coursewareError = uiState.coursewareError,
                microLessons = uiState.microLessons,
                microError = uiState.microError,
                studentGroups = uiState.studentGroups,
                isGroupLoading = uiState.isGroupLoading,
                groupError = uiState.groupError,
                onRetryGroup = viewModel::retryGroup,
                selectedGroupId = uiState.selectedGroupId,
                groupDiscusses = uiState.groupDiscusses,
                isGroupDiscussLoading = uiState.isGroupDiscussLoading,
                groupDiscussError = uiState.groupDiscussError,
                onGroupClick = viewModel::toggleGroupDiscusses,
                isHomeworkLoading = uiState.isHomeworkLoading,
                homeworkError = uiState.homeworkError,
                onHomeworkClick = onOpenHomeworkDetail,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun AiClassCourseDetailContent(
    course: AiCourse,
    quizList: List<AiQuiz>,
    isQuizLoading: Boolean,
    quizError: String?,
    onRetryQuiz: () -> Unit,
    homeworkList: List<com.linxin.feature.aiclass.domain.AiHomework> = emptyList(),
    coursewareList: List<com.linxin.feature.aiclass.domain.AiCourseware> = emptyList(),
    isCoursewareLoading: Boolean = false,
    coursewareError: String? = null,
    microLessons: List<com.linxin.feature.aiclass.domain.AiMicroLesson> = emptyList(),
    microError: String? = null,
    studentGroups: List<com.linxin.feature.aiclass.domain.AiStudentGroup> = emptyList(),
    isGroupLoading: Boolean = false,
    groupError: String? = null,
    onRetryGroup: () -> Unit = {},
    selectedGroupId: String? = null,
    groupDiscusses: List<com.linxin.feature.aiclass.domain.AiGroupDiscuss> = emptyList(),
    isGroupDiscussLoading: Boolean = false,
    groupDiscussError: String? = null,
    onGroupClick: (com.linxin.feature.aiclass.domain.AiStudentGroup) -> Unit = {},
    isHomeworkLoading: Boolean = false,
    homeworkError: String? = null,
    onHomeworkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "course_summary") {
            CourseSummaryCard(course = course)
        }

        item(key = "quiz_title") {
            Text(
                text = stringResource(R.string.ai_quiz),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        when {
            isQuizLoading -> {
                item(key = "quiz_loading") {
                    LxLoading()
                }
            }

            quizError != null -> {
                item(key = "quiz_error") {
                    LxError(
                        message = quizError,
                        onRetry = onRetryQuiz,
                    )
                }
            }

            quizList.isEmpty() -> {
                item(key = "quiz_empty") {
                    EmptyQuizCard()
                }
            }

            else -> {
                items(quizList, key = { quizItemKey(it) }) { quiz ->
                    QuizCard(quiz = quiz)
                }
            }
        }

        // ── 作业 section ──
        item(key = "homework_title") {
            Text(
                text = stringResource(R.string.ai_homework),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        when {
            isHomeworkLoading -> {
                item(key = "homework_loading") {
                    LxLoading()
                }
            }

            homeworkError != null -> {
                item(key = "homework_error") {
                    LxCard {
                        Text(
                            text = homeworkError,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            homeworkList.isEmpty() -> {
                item(key = "homework_empty") {
                    LxCard {
                        Text(
                            text = stringResource(R.string.ai_no_homework),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }

            else -> {
                items(homeworkList, key = { it.id }) { homework ->
                    HomeworkCard(homework = homework, onClick = { onHomeworkClick(homework.id) })
                }
            }
        }

        when {
            isCoursewareLoading -> {
                item(key = "courseware_loading") {
                    LxLoading()
                }
            }

            coursewareError != null -> {
                item(key = "courseware_error") {
                    LxCard {
                        Text(
                            text = coursewareError,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            coursewareList.isEmpty() -> {
                item(key = "courseware_empty") {
                    LxCard {
                        Text(
                            text = stringResource(R.string.ai_no_courseware),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }

            else -> {
                item(key = "courseware_header") {
                    Text(
                        text = stringResource(R.string.ai_courseware_title),
                        style = MiuixTheme.textStyles.title3,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(coursewareList, key = { "cw_" + it.id }) { courseware ->
                    LxCard {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = courseware.name,
                                style = MiuixTheme.textStyles.body1,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = courseware.fileType.uppercase() + "  /  " + courseware.createdAt,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
        when {
            microError != null -> item(key = "weike_error") {
                LxCard {
                    Text(text = microError, style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }
            microLessons.isNotEmpty() -> {
                item(key = "weike_header") {
                    Text(text = stringResource(R.string.ai_weike_title), style = MiuixTheme.textStyles.title3,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
                }
                items(microLessons, key = { "wk_" + it.id }) { lesson ->
                    LxCard {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(text = lesson.name, style = MiuixTheme.textStyles.body1)
                            if (lesson.meta.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = lesson.meta, style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
        }

        // ── 小组 section ──
        item(key = "group_title") {
            Text(
                text = stringResource(R.string.ai_group_title),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        when {
            isGroupLoading -> {
                item(key = "group_loading") {
                    LxLoading()
                }
            }

            groupError != null -> {
                item(key = "group_error") {
                    LxError(
                        message = groupError,
                        onRetry = onRetryGroup,
                    )
                }
            }

            studentGroups.isEmpty() -> {
                item(key = "group_empty") {
                    LxCard {
                        Text(
                            text = stringResource(R.string.ai_no_group),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }

            else -> {
                items(studentGroups, key = { "group_" + it.id }) { group ->
                    StudentGroupCard(
                        group = group,
                        expanded = selectedGroupId == group.id,
                        onClick = { onGroupClick(group) },
                    )
                }
            }
        }

        if (selectedGroupId != null) {
            when {
                isGroupDiscussLoading -> {
                    item(key = "group_discuss_loading") {
                        LxLoading()
                    }
                }

                groupDiscussError != null -> {
                    item(key = "group_discuss_error") {
                        LxCard {
                            Text(
                                text = groupDiscussError,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                groupDiscusses.isEmpty() -> {
                    item(key = "group_discuss_empty") {
                        LxCard {
                            Text(
                                text = stringResource(R.string.ai_no_group_discuss),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }

                else -> {
                    items(groupDiscusses, key = { "gdiscuss_" + it.id }) { discuss ->
                        GroupDiscussCard(discuss = discuss)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseSummaryCard(course: AiCourse) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = course.displayName(),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ai_detail_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            LxDetailRow(label = stringResource(R.string.ai_teacher), value = course.teacherName)
            LxDetailRow(label = stringResource(R.string.ai_students), value = course.studentCountText())
            if (course.typeName.isNotBlank()) {
                LxDetailRow(label = stringResource(R.string.ai_type), value = course.typeName)
            }
            LxDetailRow(label = stringResource(R.string.ai_course_id), value = course.courseId)
            LxDetailRow(label = stringResource(R.string.ai_teach_class_id), value = course.teachClassId, showDivider = false)
        }
    }
}

@Composable
private fun EmptyQuizCard() {
    LxCard {
        Text(
            text = stringResource(R.string.ai_no_quiz),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun QuizCard(quiz: AiQuiz) {
    LxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quiz.title.ifBlank { stringResource(R.string.ai_unnamed_quiz) },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildQuizMeta(quiz),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            QuizStatusBadge(isCommitted = quiz.isCommitted)
        }
    }
}

@Composable
private fun QuizStatusBadge(isCommitted: Boolean) {
    val color = if (isCommitted) LxSuccess else MiuixTheme.colorScheme.secondary
    val text = if (isCommitted) stringResource(R.string.ai_committed) else stringResource(R.string.ai_not_committed)

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
private fun buildQuizMeta(quiz: AiQuiz): String {
    val parts = buildList {
        when {
            quiz.publishDateTime.isNotBlank() -> add(quiz.publishDateTime)
            quiz.publishTime.isNotBlank() -> add(quiz.publishTime)
        }
        if (quiz.publishWeek.isNotBlank()) {
            add(quiz.publishWeek)
        }
        quiz.answerDurationMinutes?.takeIf { it > 0 }?.let { add(stringResource(R.string.ai_minutes, it)) }
    }
    return parts.joinToString(" · ").ifBlank { stringResource(R.string.ai_fif_quiz) }
}

@Composable
private fun StudentGroupCard(
    group: com.linxin.feature.aiclass.domain.AiStudentGroup,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    LxCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name.ifBlank { stringResource(R.string.ai_unnamed_group) },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildGroupMeta(group = group, expanded = expanded),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            GroupRoleBadge(group = group)
        }
    }
}

@Composable
private fun GroupRoleBadge(group: com.linxin.feature.aiclass.domain.AiStudentGroup) {
    val text = when {
        !group.roleKnown -> stringResource(R.string.ai_group_role_unknown)
        group.isLeader -> stringResource(R.string.ai_group_role_leader)
        else -> stringResource(R.string.ai_group_role_member)
    }
    val color = if (group.roleKnown && group.isLeader) LxSuccess else MiuixTheme.colorScheme.secondary

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
private fun buildGroupMeta(group: com.linxin.feature.aiclass.domain.AiStudentGroup, expanded: Boolean): String {
    val parts = buildList {
        add(
            if (group.memberCount > 0) stringResource(R.string.ai_group_members, group.memberCount)
            else stringResource(R.string.ai_group_members_unknown),
        )
        // 活动名和自己组名不是一条的时候，把活动名也带上，免得看着像凭空冒出来的组
        if (group.activityName.isNotBlank() && group.activityName != group.name) {
            add(group.activityName)
        }
        val pending = listOfNotNull(
            group.pendingHomework?.let { stringResource(R.string.ai_group_pending_homework, it) },
            group.pendingDiscuss?.let { stringResource(R.string.ai_group_pending_discuss, it) },
            group.pendingVote?.let { stringResource(R.string.ai_group_pending_vote, it) },
        )
        if (pending.isNotEmpty()) add(pending.joinToString(" "))
        add(stringResource(if (expanded) R.string.ai_group_collapse else R.string.ai_group_expand))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun GroupDiscussCard(discuss: com.linxin.feature.aiclass.domain.AiGroupDiscuss) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = discuss.title.ifBlank { stringResource(R.string.ai_unnamed_group_discuss) },
                style = MiuixTheme.textStyles.body1,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (discuss.meta.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = discuss.meta,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

private fun quizItemKey(quiz: AiQuiz): String {
    return quiz.id.ifBlank { "${quiz.title}|${quiz.publishDateTime}|${quiz.publishTime}|${quiz.publishWeek}" }
}

@Composable
private fun HomeworkCard(
    homework: com.linxin.feature.aiclass.domain.AiHomework,
    onClick: () -> Unit,
) {
    LxCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = homework.title.ifBlank { stringResource(R.string.ai_unnamed_hw) },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.ai_deadline, homework.endTime),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            HomeworkStatusBadge(homework)
        }
    }
}

@Composable
private fun HomeworkStatusBadge(homework: com.linxin.feature.aiclass.domain.AiHomework) {
    val color = when {
        homework.score.isNotBlank() && homework.score != "null" -> LxSuccess
        homework.jobState == "2" -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.secondary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = homework.statusText,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
