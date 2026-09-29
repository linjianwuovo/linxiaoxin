package com.linxin.feature.aiclass.ui
import com.linxin.core.designsystem.theme.LxShapes

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.automirrored.filled.Login
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxFloatingActionButton
import com.linxin.core.designsystem.component.LxProgressIndicator
import com.linxin.core.designsystem.component.LxTextField
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxSage
import com.linxin.core.designsystem.theme.LxSageSoft
import com.linxin.feature.aiclass.domain.AiCourse
import com.linxin.feature.aiclass.domain.displayName
import com.linxin.feature.aiclass.domain.studentCountText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiClassHomeScreen(
    onBack: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenCourseDetail: (classId: String) -> Unit,
    onOpenWorkingDetail: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AiClassViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val error = uiState.error
    val signResult = uiState.signResult
    val snackbarHostState = remember { SnackbarHostState() }
    var showSignResultSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(signResult) {
        showSignResultSheet = signResult != null
    }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = "AI课堂", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isLoading && error == null) {
                LxFloatingActionButton(
                    onClick = onOpenScan,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "扫码签到",
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { padding ->
        when {
            uiState.isLoading || uiState.isSsoInProgress -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LxProgressIndicator()
                        if (uiState.isSsoInProgress) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在连接 AI课堂...",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }

            error != null -> LxError(
                message = error,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )

            else -> AiClassContent(
                uiState = uiState,
                onSubmitSignCode = viewModel::submitSignCode,
                onOpenCourseDetail = onOpenCourseDetail,
                onOpenWorkingDetail = onOpenWorkingDetail,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showSignResultSheet && signResult != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showSignResultSheet = false
                viewModel.consumeSignResult()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MiuixTheme.colorScheme.surface,
            shape = LxShapes.medium,
        ) {
            SignResultSheet(
                message = signResult,
                onConfirm = {
                    showSignResultSheet = false
                    viewModel.consumeSignResult()
                },
            )
        }
    }
}

@Composable
private fun SignResultSheet(
    message: String,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(LxSageSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = LxSage,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "签到结果",
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        LxButton(
            text = "知道了",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AiClassContent(
    uiState: AiClassUiState,
    onSubmitSignCode: (String) -> Unit,
    onOpenCourseDetail: (String) -> Unit,
    onOpenWorkingDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 当前课堂状态
        uiState.workingRecord?.let { record ->
            item(key = "working") {
                WorkingClassCard(record = record, onClick = onOpenWorkingDetail)
            }
        }

        // 快捷签到
        item(key = "sign_in") {
            SignInCard(
                isSigningIn = uiState.isSigningIn,
                hasWorkingClass = uiState.workingRecord != null,
                onSubmit = onSubmitSignCode,
            )
        }

        // 课程列表标题
        if (uiState.courses.isNotEmpty()) {
            item(key = "courses_title") {
                Text(
                    text = "我的课程",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }

        // 课程列表
        items(uiState.courses, key = { it.stableId }) { course ->
            CourseCard(
                course = course,
                onClick = { onOpenCourseDetail(course.stableId) },
            )
        }
    }
}

@Composable
private fun WorkingClassCard(
    record: com.linxin.feature.aiclass.domain.AiWorkingRecord,
    onClick: () -> Unit,
) {
    LxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Class,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "正在上课",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = record.courseName,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
            )
            if (record.courseItemName.isNotBlank()) {
                Text(
                    text = record.courseItemName,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun SignInCard(
    isSigningIn: Boolean,
    hasWorkingClass: Boolean,
    onSubmit: (String) -> Unit,
) {
    var signCode by rememberSaveable { mutableStateOf("") }

    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "数字码签到",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LxTextField(
                value = signCode,
                onValueChange = { if (it.length <= 6) signCode = it },
                label = "输入6位签到码",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (signCode.length == 6 && !isSigningIn) onSubmit(signCode)
                    },
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            LxButton(
                text = if (isSigningIn) "签到中..." else "签到",
                onClick = { onSubmit(signCode) },
                enabled = signCode.length == 6 && !isSigningIn && hasWorkingClass,
            )

            if (!hasWorkingClass) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前没有正在进行的课堂",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: AiCourse,
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
                    text = course.displayName(),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${course.teacherName} · ${course.studentCountText()}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看课程详情",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
