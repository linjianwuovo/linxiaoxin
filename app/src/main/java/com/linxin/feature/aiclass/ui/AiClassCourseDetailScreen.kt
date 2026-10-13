package com.linxin.feature.aiclass.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

// 一学期的直录播能有一二十节，全平铺会把课件和小组挤到看不见，先只露这几节
private const val LIVE_RECORD_PREVIEW_COUNT = 6

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
                liveRecords = uiState.liveRecords,
                isLiveLoading = uiState.isLiveLoading,
                liveError = uiState.liveError,
                onRetryLive = viewModel::retryLiveRecords,
                onLiveClick = viewModel::openLiveRecord,
                onCoursewareClick = viewModel::openCourseware,
                isHomeworkLoading = uiState.isHomeworkLoading,
                homeworkError = uiState.homeworkError,
                onHomeworkClick = onOpenHomeworkDetail,
                modifier = Modifier.padding(padding),
            )
        }
    }

    // 点开课件就在这一页上层看，不推新页面也不离开应用
    if (uiState.isOpeningCourseware || uiState.openedCourseware != null || uiState.openCoursewareError != null) {
        CoursewareViewerDialog(
            title = uiState.openingCourseware?.name.orEmpty(),
            isLoading = uiState.isOpeningCourseware,
            detail = uiState.openedCourseware,
            error = uiState.openCoursewareError,
            onRetry = viewModel::retryOpenCourseware,
            onDismiss = viewModel::closeCourseware,
        )
    }

    // 点开录播同理：换到地址就在这一页上层放
    if (uiState.isOpeningLive || uiState.liveVideos.isNotEmpty() || uiState.openLiveError != null) {
        LivePlayerDialog(
            title = uiState.openingLiveTitle,
            videos = uiState.liveVideos,
            selectedIndex = uiState.selectedVideoIndex,
            isLoading = uiState.isOpeningLive,
            error = uiState.openLiveError,
            onSelect = viewModel::selectLiveVideo,
            onRetry = {
                uiState.liveRecords
                    .firstOrNull { it.title == uiState.openingLiveTitle }
                    ?.let(viewModel::openLiveRecord)
            },
            onDismiss = viewModel::closeLiveRecord,
        )
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
    liveRecords: List<com.linxin.feature.aiclass.domain.AiLiveRecord> = emptyList(),
    isLiveLoading: Boolean = false,
    liveError: String? = null,
    onRetryLive: () -> Unit = {},
    // 这两个不给默认值：上一轮 onLiveClick 忘了传，卡片照样画得出来、点下去没反应也不报错，
    // 屏幕上就成了「录播点不进去」。让编译器替我把这条线盯住。
    onLiveClick: (com.linxin.feature.aiclass.domain.AiLiveRecord) -> Unit,
    onCoursewareClick: (com.linxin.feature.aiclass.domain.AiCourseware) -> Unit,
    isHomeworkLoading: Boolean = false,
    homeworkError: String? = null,
    onHomeworkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var liveExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "course_summary") {
            CourseSummaryCard(course = course)
        }

        // ── 直录播 section ──（安小信自己这一排 tab 里「直录播」排第一个，所以放最前面）
        item(key = "live_title") {
            Text(
                text = stringResource(R.string.ai_live_title),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        when {
            isLiveLoading -> {
                item(key = "live_loading") {
                    LxLoading()
                }
            }

            liveError != null -> {
                item(key = "live_error") {
                    LxError(
                        message = liveError,
                        onRetry = onRetryLive,
                    )
                }
            }

            liveRecords.isEmpty() -> {
                item(key = "live_empty") {
                    LxCard {
                        Text(
                            text = stringResource(R.string.ai_no_live_record),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }

            else -> {
                val shown = if (liveExpanded) liveRecords else liveRecords.take(LIVE_RECORD_PREVIEW_COUNT)
                // 不给 key：同一门课每周都可能出现，服务端那个 id 撞了的话整页会崩在重复 key 上
                items(shown) { record ->
                    LiveRecordCard(record = record, onClick = { onLiveClick(record) })
                }
                if (liveRecords.size > LIVE_RECORD_PREVIEW_COUNT) {
                    item(key = "live_toggle") {
                        LxCard(onClick = { liveExpanded = !liveExpanded }) {
                            Text(
                                text = if (liveExpanded) {
                                    stringResource(R.string.ai_live_collapse)
                                } else {
                                    stringResource(R.string.ai_live_show_all, liveRecords.size)
                                },
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
            }
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
                    LxCard(onClick = { onCoursewareClick(courseware) }) {
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
                            // 点开要的那两个 id 列表不一定给，缺了就是打不开，提前说清楚
                            if (courseware.resourceId.isBlank() || courseware.coursewareId.isBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.ai_courseware_no_id),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }
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

/**
 * 直录播的一行。只有学校标了「回放已生成」(liveStatus=3) 的才给点，
 * 有资源号但还没挂上来的标成「处理中」并且不给按 —— 之前用「有没有 resourceId」判，
 * 把处理中的也标成可回放，点下去只拿到一句「未获取到录播课信息」，等于自己骗自己。
 */
@Composable
private fun LiveRecordCard(
    record: com.linxin.feature.aiclass.domain.AiLiveRecord,
    onClick: () -> Unit,
) {
    val meta = listOf(record.whenText, record.roomText).filter { it.isNotBlank() }.joinToString(" · ")
    LxCard(onClick = if (record.isReplayReady) onClick else null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title.ifBlank { stringResource(R.string.ai_unnamed_live) },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = meta,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            LiveStatusBadge(record = record)
        }
    }
}

/**
 * 状态只有三档，全部来自屏幕上对得上的事实：课还没上 → 未开始；上过了且服务端留了可看的东西 → 可回放；
 * 上过了但什么都没留 → 没有回放。
 * 没有「直播中」这一档是有原因的：屏幕上验过整学期每一条的 isNowPlay 都是真，
 * 那个键说的是「这是直播课」不是「现在正在播」，用它判就会把两个月后的课标成正在播。
 * 等哪天真在一节直播当天看到别的信号，再补这一档。
 */
@Composable
private fun LiveStatusBadge(record: com.linxin.feature.aiclass.domain.AiLiveRecord) {
    val text = when {
        record.isUpcoming -> stringResource(R.string.ai_live_upcoming)
        record.isReplayReady -> stringResource(R.string.ai_live_replay)
        record.canPlay -> stringResource(R.string.ai_live_processing)
        else -> stringResource(R.string.ai_live_none)
    }
    val color = when {
        record.isReplayReady -> LxSuccess
        record.isUpcoming || !record.canPlay -> MiuixTheme.colorScheme.onSurfaceVariantSummary
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

/**
 * 点开课件之后的全屏查看器。
 * 逐页图是学校自己渲染好的 PNG（实测一节 pptx 75 张），这里就一条条滚，
 * 不跳安小信、不跳浏览器，也不去碰那条 Office 在线预览地址。
 */
@Composable
private fun CoursewareViewerDialog(
    title: String,
    isLoading: Boolean,
    detail: com.linxin.feature.aiclass.domain.AiCoursewareDetail?,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Scaffold(
            containerColor = MiuixTheme.colorScheme.background,
            topBar = {
                LxTopBar(
                    title = title.ifBlank { stringResource(R.string.ai_courseware_viewer) },
                    onBack = onDismiss,
                )
            },
        ) { padding ->
            when {
                isLoading -> LxLoading(modifier = Modifier.padding(padding))

                detail == null -> LxError(
                    message = error ?: stringResource(R.string.ai_courseware_open_failed),
                    onRetry = onRetry,
                    modifier = Modifier.padding(padding),
                )

                detail.pages.isNotEmpty() -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    itemsIndexed(detail.pages) { index, url ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CoursewarePageImage(url = url)
                            Text(
                                text = stringResource(R.string.ai_courseware_page_of, index + 1, detail.pages.size),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                else -> Text(
                    text = stringResource(R.string.ai_courseware_no_pages),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(padding)
                        .padding(20.dp),
                )
            }
        }
    }
}

/**
 * 录播播放器。地址是资源中心当场签名的 minio mp4 直链，用系统自带的 VideoView 放
 * （工程里没有 media3，而这个就是普通渐进式 mp4，Range 请求播放器自己会发）。
 * 多个机位（教师机位 / HDMI）在上面给一排小签切换，切了就换地址重开。
 * 播放失败不猜原因，直接把系统给的两个码摆出来，下次插线拿这个对日志。
 */
@Composable
private fun LivePlayerDialog(
    title: String,
    videos: List<com.linxin.feature.aiclass.domain.AiRecordedVideo>,
    selectedIndex: Int,
    isLoading: Boolean,
    error: String?,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                LxTopBar(
                    title = title.ifBlank { stringResource(R.string.ai_live_player) },
                    onBack = onDismiss,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (videos.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        videos.forEachIndexed { index, video ->
                            val selected = index == selectedIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) LxSuccess.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.12f),
                                    )
                                    .clickable { onSelect(index) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = video.name,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoading -> LxLoading()

                        videos.isEmpty() -> Text(
                            text = error ?: stringResource(R.string.ai_live_no_video),
                            style = MiuixTheme.textStyles.body2,
                            color = Color.White,
                            modifier = Modifier.padding(24.dp),
                        )

                        else -> {
                            val video = videos[selectedIndex.coerceIn(0, videos.lastIndex)]
                            var failed by remember(video.url) { mutableStateOf(false) }
                            var errorCode by remember(video.url) { mutableStateOf("") }
                            if (failed) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.ai_live_play_failed, errorCode),
                                        style = MiuixTheme.textStyles.body2,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.ai_live_play_retry),
                                        style = MiuixTheme.textStyles.body2,
                                        color = LxSuccess,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = onRetry)
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            } else {
                                AndroidView(
                                    factory = { context ->
                                        VideoView(context).apply {
                                            keepScreenOn = true
                                            setVideoURI(Uri.parse(video.url))
                                            setMediaController(MediaController(context))
                                            setOnPreparedListener { start() }
                                            setOnErrorListener { _, what, extra ->
                                                failed = true
                                                errorCode = "$what/$extra"
                                                true
                                            }
                                        }
                                    },
                                    onRelease = { it.stopPlayback() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
