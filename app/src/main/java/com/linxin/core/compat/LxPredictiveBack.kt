package com.linxin.core.compat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 侧滑返回时当前页的形变状态：[progress] 0 = 没手势，1 = 已经拉到该返回的程度；
 * [modifier] 挂在整页容器上，负责监听左边缘的横滑。
 *
 * 为什么这一层要应用自己出：Mi 11（HyperOS，targetSdk 35）实测，系统只把"该返回了"
 * 这一下派发给应用，手势中途零进度（真手指侧滑连拍 7 帧，卡片左边界钉在 x=70 不动），
 * 官方那套"预先画好上一页给你看"在 Compose 内容上根本不生效。
 * 而 androidx 的 PredictiveBackHandler 一注册，HyperOS 连原来那一下返回转场都不给了
 * （1.3.7-beta42 装机实测：所有返回动画消失，也没有预示）—— 所以这里完全不碰系统的分发，
 * 只在左边缘 28dp 内自己收手势。
 *
 * 只做缩放 + 淡出，不做平移：底栏是常驻液态玻璃，平移会和玻璃滑块打架。
 */
class LxBackMotion internal constructor(val progress: Float, val modifier: Modifier)

@Composable
fun rememberLxBackMotion(navController: NavHostController): LxBackMotion {
    val currentEntry by navController.currentBackStackEntryAsState()
    // 起始页没有上一页，这时候整条手势都不挂：不然首页会被缩一下又弹回去，
    // 而且首页底下滑页要横向拖动，绝不能跟它抢。
    val canPop = currentEntry != null && navController.previousBackStackEntry != null

    // 拖动过程中只能写普通状态：awaitEachGesture 是 restricted 作用域，
    // 里面连 Animatable.snapTo 这种挂起函数都不许调。回弹才用 Animatable。
    var drag by remember { mutableFloatStateOf(0f) }
    val settleAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val edgeModifier = if (!canPop) {
        Modifier
    } else {
        Modifier.pointerInput(canPop) {
            val edge = 28.dp.toPx()
            val slop = viewConfiguration.touchSlop
            val width = size.width.toFloat().coerceAtLeast(1f)
            // 形变走满的行程：60% 屏宽（Mi 11 ≈ 266dp）。
            // 之前拿整屏宽当分母，手指自然一划只走到 40% 形变，看着就是"划一点动画就没了"。
            val travel = width * 0.60f
            // 提交返回的距离：划过 55% 屏宽（≈ 243dp）才真返回，没到就弹回去。
            // 用户两轮反馈都是"太短/太小"，所以从 28% → 40% → 55% 往上抬；
            // 边界实测：400px(28%) 不返回、700px(49%) 不返回、820px(57%) 返回。
            val commitAt = width * 0.55f
            // 这两个是普通 lambda，所以可以在 restricted 作用域里被调用；
            // 真正的挂起动作都塞进 scope.launch 里。
            val settle = {
                val from = drag
                drag = 0f
                scope.launch {
                    settleAnim.snapTo(from)
                    settleAnim.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                }
            }
            // 真返回了：形变立刻清零，别把缩放带到下一页
            val finish = {
                drag = 0f
                scope.launch { settleAnim.snapTo(0f) }
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.position.x > edge) return@awaitEachGesture   // 不在边缘，整条手势让给页面
                var dx = 0f
                var dy = 0f
                var claimed = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    dx += change.position.x - change.previousPosition.x
                    dy += change.position.y - change.previousPosition.y
                    if (!claimed) {
                        if (!change.pressed) break
                        if (abs(dx) < slop && abs(dy) < slop) continue
                        // 只有明显是竖着划（纵向超过横向两倍）才让给列表，斜着划照常接管
                        if (abs(dy) > abs(dx) * 2f) break
                        claimed = true
                    }
                    change.consume()
                    drag = (dx / travel).coerceIn(0f, 1f)
                    if (!change.pressed) {
                        if (dx > commitAt) {
                            finish()
                            navController.popBackStack()
                        } else {
                            settle()
                        }
                        break
                    }
                }
            }
        }
    }

    val progress = if (drag > settleAnim.value) drag else settleAnim.value
    return LxBackMotion(if (canPop) progress else 0f, edgeModifier)
}
