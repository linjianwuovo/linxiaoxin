package com.linxin.core.compat

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * 侧滑返回时当前页的形变量，0 = 没手势，1 = 已经拉到要返回的程度。
 *
 * 为什么要应用自己出这一层：1.3.7-beta41 在 Mi 11（HyperOS，targetSdk 35）上实测过，
 * 手势期间宿主窗口零变化（截图逐像素相同），只有松手那一刻系统才整窗缩一下 ——
 * 也就是说官方那套"把上一页预先画给你看"的机制在这个系统上不会替 Compose 内容生效，
 * 内容级转场只能自己做。
 *
 * 只做缩放 + 淡出，不做平移：底栏是常驻液态玻璃，平移会和玻璃滑块打架。
 */
@Composable
fun rememberLxBackProgress(navController: NavHostController): Float {
    val currentEntry by navController.currentBackStackEntryAsState()
    // 起始页（首页）没有上一页，这时候不给手势加动画，免得首页被缩一下又弹回去
    val canPop = currentEntry != null && navController.previousBackStackEntry != null

    var progress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = canPop) { flow: Flow<BackEventCompat> ->
        try {
            flow.collect { event -> progress = event.progress }
            navController.popBackStack()
        } catch (e: CancellationException) {
            // 用户中途松手回去：什么都不做，只把形变归零
        } finally {
            // 取消（用户中途松回去）和完成都要归零，不然页面会停在半缩的状态
            progress = 0f
        }
    }

    return if (canPop) progress else 0f
}
