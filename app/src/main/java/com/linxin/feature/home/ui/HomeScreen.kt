package com.linxin.feature.home.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.linxin.R
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.feature.messages.ui.MessagesScreen
import com.linxin.feature.news.ui.NewsScreen
import com.linxin.feature.schedule.ui.ScheduleScreen
import com.linxin.feature.theme.ui.ThemeViewModel
import com.linxin.navigation.Routes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val tabs = listOf(
    LxTab(R.string.tab_home, Icons.Outlined.Home),
    LxTab(R.string.tab_schedule, Icons.Outlined.CalendarMonth),
    LxTab(R.string.tab_messages, Icons.Outlined.Forum),
    LxTab(R.string.tab_news, Icons.Outlined.Campaign),
    LxTab(R.string.tab_profile, Icons.Outlined.Person),
)

@Composable
fun HomeScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val hazeState = remember { HazeState() }
    // 液态玻璃取样源：整页内容录进 GraphicsLayer，底栏的 blur/lens 折射它。
    // 这是用户提供的液态玻璃 demo（Kyant0 Backdrop 官方用法）的接法。
    val glassBackdrop = rememberLayerBackdrop()
    val themeSettings by themeViewModel.settings.collectAsState()
    val pagerState = rememberPagerState(initialPage = selectedTab) { tabs.size }
    val scope = rememberCoroutineScope()

    // 滑动翻页时同步高亮底栏
    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    fun goToTab(index: Int) {
        selectedTab = index
        scope.launch { pagerState.animateScrollToPage(index) }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // 内容层：左右滑动切页；既是玻璃(LIQUID)的采样源，也是毛玻璃(FROSTED)的采样源
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(glassBackdrop)
                .hazeSource(hazeState),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> HomeDashboard(
                        viewModel = homeViewModel,
                        navController = navController,
                        onTabSelected = { goToTab(it) },
                    )
                    1 -> ScheduleScreen()
                    2 -> MessagesScreen()
                    3 -> NewsScreen(
                        onNewsClick = { newsId ->
                            navController.navigate(Routes.newsDetail(newsId))
                        },
                    )
                    4 -> ProfileScreen(
                        onNavigateCheckin = {
                            navController.navigate(Routes.CHECKIN_LIST) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateAiClass = {
                            navController.navigate(Routes.AICLASS_HOME) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateMore = {
                            navController.navigate(Routes.MORE_FEATURES) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateRouteSimulation = {
                            navController.navigate(Routes.RUNNING_ROUTE_SETTINGS) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateAbout = {
                            navController.navigate(Routes.ABOUT) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateTheme = {
                            navController.navigate(Routes.THEME) {
                                launchSingleTop = true
                            }
                        },
                        onLogout = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                    )
                }
            }
            // 氛围光必须画在 pager 之上才会被 layerBackdrop 采到：
            // 之前它排在 pager 下面，被不透明页面挡掉，玻璃无彩色可折就成了实心白。
            // 只有一层平滑渐变，不含任何控件，也不吃手势。
            AmbientBottomGlow(modifier = Modifier.align(Alignment.BottomCenter))
        }

        // 悬浮液态玻璃底栏（Kyant0 Backdrop 官方用法，照搬 demo 实现）
        LxBottomBar(
            selectedIndex = selectedTab,
            onSelected = { goToTab(it) },
            material = themeSettings.barMaterial,
            backdrop = glassBackdrop,
            hazeState = hazeState,
            glassBlur = themeSettings.glassBlur,
            glassRefraction = themeSettings.glassRefraction,
            glassDistortion = themeSettings.glassDistortion,
            glassDispersion = themeSettings.glassDispersion,
            tabs = tabs,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun AmbientBottomGlow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, LxTerra.copy(alpha = 0.06f), LxTerra.copy(alpha = 0.16f)),
                ),
            ),
    )
}
