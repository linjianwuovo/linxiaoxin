package com.linxin.navigation

import android.net.Uri

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.linxin.core.auth.SessionManager
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.feature.aiclass.ui.AiClassHomeScreen
import com.linxin.feature.aiclass.ui.AiClassCourseDetailScreen
import com.linxin.feature.aiclass.ui.AiClassScanScreen
import com.linxin.feature.aiclass.ui.AiHomeworkDetailScreen
import com.linxin.feature.about.ui.AboutScreen
import com.linxin.feature.checkin.ui.CheckinDetailScreen
import com.linxin.feature.checkin.ui.CheckinListScreen
import com.linxin.feature.credit.ui.CreditScreen
import com.linxin.feature.exam.ui.ExamScreen
import com.linxin.feature.news.ui.NewsDetailScreen
import com.linxin.feature.holiday.ui.HolidayListScreen
import com.linxin.feature.holiday.ui.HolidayRegisterScreen
import com.linxin.feature.home.ui.HomeScreen
import com.linxin.feature.labor.ui.LaborSummaryScreen
import com.linxin.feature.repair.ui.RepairDetailScreen
import com.linxin.feature.repair.ui.RepairFormScreen
import com.linxin.feature.repair.ui.RepairScreen
import com.linxin.feature.leave.ui.LeaveCreateScreen
import com.linxin.feature.leave.ui.LeaveScreen
import com.linxin.feature.messages.ui.MessagesScreen
import com.linxin.feature.card.ui.CampusCardScreen
import com.linxin.feature.login.ui.LoginScreen
import com.linxin.feature.more.ui.MoreFeaturesScreen
import com.linxin.feature.onboarding.ui.OnboardingScreen
import com.linxin.feature.sports.ui.SportsAppealsScreen
import com.linxin.feature.sports.ui.SportsDetailScreen
import com.linxin.feature.sports.ui.SportsPlannerScreen
import com.linxin.feature.sports.ui.SportsRecordsScreen
import com.linxin.feature.sports.ui.SportsRunScreen
import com.linxin.feature.sports.ui.SportsStartScreen
import com.linxin.feature.running.exercise.ui.ClubDetailScreen
import com.linxin.feature.running.exercise.ui.ExerciseCheckScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// 推入推出共用一条 spec：同一条曲线、同一个时长，只是方向反过来。
// 不再叠淡入淡出，也不再有四分之一屏那种"半推半就"的距离。
private val NAV_PUSH: FiniteAnimationSpec<IntOffset> = tween(420, easing = FastOutSlowInEasing)

@Composable
fun LinXinNavHost(
    sessionManager: SessionManager,
    navController: NavHostController = rememberNavController(),
    shortcutTarget: ShortcutTarget? = null,
    pendingDormTaskId: String? = null,
    isDormShortcutResolved: Boolean = false,
    pendingNotificationRoute: String? = null,
    /** 主题页「动效 → 侧滑返回预示」的开关 */
    onShortcutConsumed: () -> Unit = {},
) {
    // 合并 onboarded + loggedIn 两态决定起始页
    val startStateFlow = remember(sessionManager) {
        combine(sessionManager.isOnboarded, sessionManager.isLoggedIn) { onboarded, loggedIn ->
            when {
                !onboarded -> Routes.ONBOARDING
                !loggedIn -> Routes.LOGIN
                else -> Routes.HOME
            }
        }
    }
    val startRoute by startStateFlow.collectAsState(initial = null)

    val resolvedStartRoute = startRoute
    if (resolvedStartRoute == null) {
        LxLoading()
        return
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentOnShortcutConsumed by rememberUpdatedState(onShortcutConsumed)

    // shortcut 落位静态化（仿 LibChecker setCurrentItem(index, false)）：
    // 链式 navigate 期间禁用 NavHost 转场，避免 splash 后再叠加多段滑动动画
    var suppressTransitions by remember { mutableStateOf(false) }
    LaunchedEffect(suppressTransitions) {
        if (suppressTransitions) {
            delay(600)
            suppressTransitions = false
        }
    }

    LaunchedEffect(shortcutTarget, resolvedStartRoute, pendingDormTaskId, isDormShortcutResolved) {
        val target = shortcutTarget ?: return@LaunchedEffect
        if (resolvedStartRoute != Routes.HOME) return@LaunchedEffect
        if (target == ShortcutTarget.DORM_CHECKIN && !isDormShortcutResolved) return@LaunchedEffect

        suppressTransitions = true
        navController.navigate(Routes.HOME) {
            launchSingleTop = true
            popUpTo(Routes.HOME) { inclusive = false }
        }
        when (target) {
            ShortcutTarget.SCAN_CHECKIN -> {
                navController.navigate(Routes.AICLASS_HOME) { launchSingleTop = true }
                navController.navigate(Routes.AICLASS_SCAN) { launchSingleTop = true }
            }

            ShortcutTarget.DORM_CHECKIN -> {
                navController.navigate(Routes.CHECKIN_LIST) { launchSingleTop = true }
                if (pendingDormTaskId != null) {
                    navController.navigate(Routes.checkinDetail(pendingDormTaskId)) { launchSingleTop = true }
                } else {
                    Toast.makeText(context, "当前没有待签到的查寝任务", Toast.LENGTH_SHORT).show()
                }
            }
        }
        currentOnShortcutConsumed()
    }

    // 通知点击路由
    LaunchedEffect(pendingNotificationRoute) {
        val route = pendingNotificationRoute ?: return@LaunchedEffect
        if (resolvedStartRoute != Routes.HOME) return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        currentOnShortcutConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = resolvedStartRoute,
        // 二级界面整页从右边推进来（走满一屏宽），底下那页一动不动；返回就是倒放。
        enterTransition = {
            if (suppressTransitions) EnterTransition.None
            else slideInHorizontally(NAV_PUSH) { it }
        },
        exitTransition = {
            if (suppressTransitions) ExitTransition.None
            else ExitTransition.None
        },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            if (suppressTransitions) ExitTransition.None
            else slideOutHorizontally(NAV_PUSH) { it }
        },
    ) {
        composable(
            Routes.ONBOARDING,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(300)) },
        ) {
            val context = LocalContext.current
            OnboardingScreen(
                onAcknowledge = {
                    scope.launch {
                        sessionManager.markOnboarded()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                },
                onDismiss = {
                    context.findActivity()?.let { activity ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            activity.finishAndRemoveTask()
                        } else {
                            activity.finishAffinity()
                        }
                    }
                },
            )
        }

        composable(
            Routes.LOGIN,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(300)) },
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            Routes.HOME,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(200)) },
        ) {
            HomeScreen(navController = navController)
        }

        // Checkin
        composable(Routes.CHECKIN_LIST) { backStackEntry ->
            val shouldRefresh by backStackEntry.savedStateHandle
                .getStateFlow("checkin_refresh", false)
                .collectAsState()
            CheckinListScreen(
                onBack = { navController.popBackStack() },
                onTaskClick = { taskDateId ->
                    navController.navigate(Routes.checkinDetail(taskDateId))
                },
                shouldRefresh = shouldRefresh,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["checkin_refresh"] = false
                },
            )
        }
        composable(Routes.HOLIDAY_LIST) { backStackEntry ->
            val shouldRefresh by backStackEntry.savedStateHandle
                .getStateFlow("holiday_refresh", false)
                .collectAsState()
            HolidayListScreen(
                onBack = { navController.popBackStack() },
                onHolidayClick = { holidayId ->
                    navController.navigate(Routes.holidayRegister(holidayId))
                },
                shouldRefresh = shouldRefresh,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["holiday_refresh"] = false
                },
            )
        }
        composable(
            route = Routes.HOLIDAY_REGISTER,
            arguments = listOf(navArgument("holidayId") { type = NavType.StringType }),
        ) { backStackEntry ->
            HolidayRegisterScreen(
                holidayId = backStackEntry.arguments?.getString("holidayId") ?: "",
                onSubmitSuccess = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("holiday_refresh", true)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.NEWS_DETAIL,
            arguments = listOf(navArgument("newsId") { type = NavType.StringType }),
        ) {
            // newsId 由 NewsDetailViewModel 通过 SavedStateHandle 自己取
            NewsDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.CHECKIN_DETAIL,
            arguments = listOf(navArgument("taskDateId") { type = NavType.StringType }),
        ) {
            CheckinDetailScreen(
                onSubmitSuccess = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("checkin_refresh", true)
                },
                onBack = { navController.popBackStack() },
            )
        }

        // 体育·跑步（feature/sports，全部按抓包重写；旧的 feature/running 那一套已归档）
        composable(Routes.RUNNING_HOME) {
            SportsStartScreen(
                onBack = { navController.popBackStack() },
                onOpenRun = { navController.navigate(Routes.RUNNING_ACTIVE) { launchSingleTop = true } },
                onOpenRecords = { navController.navigate(Routes.SPORT_RECORDS) { launchSingleTop = true } },
                onOpenPlanner = { navController.navigate(Routes.RUNNING_ROUTE_PLANNER) { launchSingleTop = true } },
            )
        }
        composable(Routes.RUNNING_ACTIVE) {
            SportsRunScreen(
                onBack = { navController.popBackStack() },
                onFinished = { navController.navigate(Routes.SPORT_RECORDS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SPORT_RECORDS) {
            SportsRecordsScreen(
                onBack = { navController.popBackStack() },
                onOpenDetail = { checkId ->
                    navController.navigate(Routes.sportRecordDetail(checkId)) { launchSingleTop = true }
                },
                onOpenAppeals = { navController.navigate(Routes.SPORT_MAINTAIN) { launchSingleTop = true } },
            )
        }
        composable(
            route = Routes.SPORT_RECORD_DETAIL,
            arguments = listOf(navArgument("checkId") { type = NavType.StringType }),
        ) {
            SportsDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenAppeals = { navController.navigate(Routes.SPORT_MAINTAIN) { launchSingleTop = true } },
            )
        }
        composable(Routes.SPORT_MAINTAIN) {
            SportsAppealsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RUNNING_ROUTE_PLANNER) {
            SportsPlannerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RUNNING_CLUB_DETAIL) {
            ClubDetailScreen(
                onBack = { navController.popBackStack() },
                onStartCheck = { autoId, memberId ->
                    navController.navigate(Routes.runningExerciseCheck(autoId, memberId)) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Routes.RUNNING_EXERCISE_CHECK,
            arguments = listOf(
                navArgument("autoId") { type = NavType.StringType },
                navArgument("memberId") { type = NavType.StringType },
            ),
        ) {
            ExerciseCheckScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // Labor
        composable(Routes.LABOR_SUMMARY) {
            LaborSummaryScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 报修（只读）
        composable(Routes.REPAIR_LIST) {
            RepairScreen(
                onBack = { navController.popBackStack() },
                onOpenDetail = { bxdh ->
                    navController.navigate(Routes.repairDetail(bxdh)) { launchSingleTop = true }
                },
                onCreate = {
                    navController.navigate(Routes.REPAIR_FORM) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.REPAIR_FORM) {
            RepairFormScreen(
                onBack = { navController.popBackStack() },
                onSubmitted = {
                    navController.popBackStack(Routes.REPAIR_LIST, inclusive = false)
                },
            )
        }

        composable(Routes.REPAIR_DETAIL) {
            RepairDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 请假（只读）
        // 消息中心：底栏那个 tab 已经常驻一份，这一份是给通知点进来用的独立页
        composable(Routes.MESSAGES) {
            MessagesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.LEAVE_LIST) {
            LeaveScreen(
                onBack = { navController.popBackStack() },
                onCreate = {
                    navController.navigate(Routes.LEAVE_CREATE) { launchSingleTop = true }
                },
                // 查寝类待办点一下就进我们自己的查寝页，不去学校那个 H5
                onOpenCheckin = {
                    navController.navigate(Routes.CHECKIN_LIST) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.LEAVE_CREATE) {
            LeaveCreateScreen(
                onBack = { navController.popBackStack() },
                onSubmitted = {
                    navController.popBackStack(Routes.LEAVE_LIST, inclusive = false)
                },
            )
        }

        // 校园卡（只读）
        composable(Routes.CAMPUS_CARD) {
            CampusCardScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // AI Class
        composable(Routes.AICLASS_HOME) { backStackEntry ->
            val aiClassEntry = remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(Routes.AICLASS_HOME) }
                    .getOrNull() ?: backStackEntry
            }
            val aiClassViewModel: com.linxin.feature.aiclass.ui.AiClassViewModel = hiltViewModel(aiClassEntry)
            AiClassHomeScreen(
                viewModel = aiClassViewModel,
                onBack = { navController.popBackStack() },
                onOpenScan = {
                    navController.navigate(Routes.AICLASS_SCAN) {
                        launchSingleTop = true
                    }
                },
                onOpenCourseDetail = { classId ->
                    navController.navigate(Routes.aiClassDetail(classId)) {
                        launchSingleTop = true
                    }
                },
                onOpenWorkingDetail = {
                    aiClassViewModel.openWorkingRecordDetail()
                    navController.navigate(Routes.aiClassDetail("_working")) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.AICLASS_SCAN) { backStackEntry ->
            // 从 AI课堂 进入时复用其共享 VM（扫完弹回 AI课堂 看结果）；
            // 从首页直达时栈里没有 AI课堂，改用扫码页自己的 VM，结果在扫码页弹窗展示，返回键回首页。
            val aiClassEntry = remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(Routes.AICLASS_HOME) }.getOrNull()
            }
            val aiClassViewModel: com.linxin.feature.aiclass.ui.AiClassViewModel =
                if (aiClassEntry != null) hiltViewModel(aiClassEntry) else hiltViewModel(backStackEntry)
            val scanUiState by aiClassViewModel.uiState.collectAsState()
            AiClassScanScreen(
                onBack = { navController.popBackStack() },
                signResult = scanUiState.signResult,
                signSucceeded = scanUiState.signSucceeded,
                onConsumeSignResult = aiClassViewModel::consumeSignResult,
                onScanResult = { payload ->
                    aiClassViewModel.submitQrCode(payload)
                    if (aiClassEntry != null) navController.popBackStack()
                },
            )
        }
        composable(
            route = Routes.AICLASS_DETAIL,
            arguments = listOf(navArgument("classId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val aiClassEntry = remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(Routes.AICLASS_HOME) }
                    .getOrNull() ?: backStackEntry
            }
            val aiClassViewModel: com.linxin.feature.aiclass.ui.AiClassViewModel = hiltViewModel(aiClassEntry)
            AiClassCourseDetailScreen(
                classId = Uri.decode(backStackEntry.arguments?.getString("classId").orEmpty()),
                viewModel = aiClassViewModel,
                onBack = { navController.popBackStack() },
                onOpenHomeworkDetail = { cwId ->
                    val selectedCourse = aiClassViewModel.uiState.value.selectedCourse
                    val teachClassId = selectedCourse?.teachClassId.orEmpty()
                        .ifBlank { selectedCourse?.classId.orEmpty() }
                    navController.navigate(Routes.aiClassHomeworkDetail(cwId, teachClassId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        // AI Homework Detail
        composable(
            route = Routes.AICLASS_HOMEWORK_DETAIL,
            arguments = listOf(
                navArgument("cwId") { type = NavType.StringType },
                navArgument("teachClassId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            AiHomeworkDetailScreen(
                cwId = Uri.decode(backStackEntry.arguments?.getString("cwId").orEmpty()),
                teachClassId = Uri.decode(backStackEntry.arguments?.getString("teachClassId").orEmpty()),
                onBack = { navController.popBackStack() },
            )
        }

        // More Features
        composable(Routes.MORE_FEATURES) {
            MoreFeaturesScreen(
                onBack = { navController.popBackStack() },
                onNavigateLabor = {
                    navController.navigate(Routes.LABOR_SUMMARY) { launchSingleTop = true }
                },
                onNavigateRepair = {
                    navController.navigate(Routes.REPAIR_LIST) { launchSingleTop = true }
                },
                onNavigateLeave = {
                    navController.navigate(Routes.LEAVE_LIST) { launchSingleTop = true }
                },
                onNavigateCard = {
                    navController.navigate(Routes.CAMPUS_CARD) { launchSingleTop = true }
                },
                onNavigateExam = {
                    navController.navigate(Routes.EXAM_SCORES) { launchSingleTop = true }
                },
                onNavigateCredit = {
                    navController.navigate(Routes.CREDIT_OVERVIEW) { launchSingleTop = true }
                },
                onNavigateHoliday = {
                    navController.navigate(Routes.HOLIDAY_LIST) { launchSingleTop = true }
                },
            )
        }

        // Exam
        composable(Routes.EXAM_SCORES) {
            ExamScreen(onBack = { navController.popBackStack() })
        }

        // Credit
        composable(Routes.CREDIT_OVERVIEW) {
            CreditScreen(onBack = { navController.popBackStack() })
        }

        // About
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        // Theme
        composable(Routes.THEME) {
            com.linxin.feature.theme.ui.ThemeScreen(onBack = { navController.popBackStack() })
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
