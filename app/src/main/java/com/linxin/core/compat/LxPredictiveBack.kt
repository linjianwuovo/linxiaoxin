package com.linxin.core.compat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * 侧滑返回时当前页的形变状态：[progress] 0 = 没手势，1 = 已经拉到该返回的程度；
 * [modifier] 挂在整页容器上，负责监听左边缘的横滑。设置里「动效 → 侧滑返回预示」关掉
 * 就整条不挂。
 *
 * 为什么这一层要应用自己出：Mi 11（HyperOS，targetSdk 35）实测，系统只把"该返回了"这一下
 * 派发给应用，手势中途零进度 —— 补了 android:enableOnBackInvokedCallback="true" 之后
 * 再测，贴边划和内容区划的"系统派发进度峰值"都还是 0.00，一次事件都没给。
 * 而注册 androidx 的 PredictiveBackHandler 反而会让 HyperOS 连原来那一下返回转场都收走
 * （1.3.7-beta42 装机实测：所有返回动画消失、也没有预示）。所以完全不碰系统的分发，
 * 只在左边缘自己收横滑。
 *
 * 管不到的那一段：贴着屏幕最边（约 0~20dp）起的滑动被 HyperOS 自己吃掉，它出它自己的动画、
 * 它自己返回，应用开关拦不住。实测对照（查寝页划 900px）：开关开 → x=90 会返回、
 * x=6 也返回；开关关 → x=90 不返回、x=6 照样返回。
 *
 * 只做缩放 + 淡出 + 少量左移，不做大幅度平移：底栏是常驻液态玻璃，横向挪多了会和玻璃滑块打架。
 *
 * [blurProgress] 比 [progress] 多带一段"返回之后"：见下面 rememberLxBackMotion 里 popAnim 的注释。
 */
class LxBackMotion internal constructor(
    val progress: Float,
    val blurProgress: Float,
    val modifier: Modifier,
)

// 44dp 而不是 28dp：真贴屏幕最边那十来 dp 被系统边缘手势抢走（米11 实测 x=6 接不到、
// x=90 才接得到），热区给宽一点，拇指落在里面一点也能触发我们这层，不然真机上根本看不见效果。
private const val EDGE_DP = 44f

/** 形变走满所需的滑动距离 */
private const val TRAVEL_DP = 266f

/** 真返回所需的滑动距离：米11 边界实测 400px/700px 不返回、820px/900px 返回 */
private const val COMMIT_DP = 243f

/**
 * 预示期间当前页的最大模糊半径（dp），实际按进度平方给（见 NavGraph）。
 * 16~20dp 这一档是 ColorOS 那个仓里唯一两处模糊实测在用的量（顶栏 Haze 16dp，注释写明 30dp 中低端机掉帧）；
 * 用的是 androidx 的 `Modifier.blur`（Apache-2.0），Android 12 以下自动不生效，不用自己判版本。
 */
const val LX_BACK_BLUR_MAX_DP = 20f

/**
 * 返回完成之后模糊收回的时长和起点。
 *
 * 为什么要这一段：米11 实测（慢滑 + 连拍 + 相邻像素亮度差）我们这层的模糊是真的在起作用，
 * 内容区从 1.669 掉到 0.261，但真实手指快滑只在峰值停两三帧就 pop 了，而 pop 之后画的是下一页，
 * 模糊已经跟着 drag 清零 —— 所以看上去"没有模糊"。ColorOS/AppShare 那套是两页都在合成，
 * 底下那页天生带模糊；我们只有一页，那就让上一页出场时带一把模糊再收干净，
 * 手势返回、点返回箭头、系统手势都算（挂在回退栈上，少了一页就触发）。
 */
private const val POP_BLUR_MS = 320
private const val POP_BLUR_FROM = 0.85f

@Composable
fun rememberLxBackMotion(navController: NavHostController, enabled: Boolean = true): LxBackMotion {
    val currentEntry by navController.currentBackStackEntryAsState()
    // 起始页没有上一页时不挂：不然首页会被缩一下又弹回去，
    // 而且首页底下滑页要横向拖动，绝不能跟它抢。
    val canPop = enabled && currentEntry != null && navController.previousBackStackEntry != null

    // 拖动过程中只能写普通状态：awaitEachGesture 是 restricted 作用域，
    // 里面连 Animatable.snapTo 这种挂起函数都不许调。回弹才用 Animatable。
    var drag by remember { mutableFloatStateOf(0f) }
    val settleAnim = remember { Animatable(0f) }
    val popAnim = remember { Animatable(0f) }
    // 手势提交那一刻手里停在第几档，留给回退栈监听当模糊起点。
    // 写成普通 var 会被中途重组冲掉，所以照样放成 state。
    var commitFrom by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val edgeModifier = if (!canPop) {
        Modifier
    } else {
        Modifier.pointerInput(canPop) {
            val slop = viewConfiguration.touchSlop
            // 这两个是普通 lambda，所以能在 restricted 作用域里被调用；挂起动作都塞进 launch。
            val settle = {
                val from = drag
                drag = 0f
                scope.launch {
                    settleAnim.snapTo(from)
                    settleAnim.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                }
            }
            // 真返回了：形变立刻清零，别把缩放带到下一页；模糊那段交给回退栈监听接着收
            val finish = {
                commitFrom = drag
                drag = 0f
                scope.launch { settleAnim.snapTo(0f) }
            }
            awaitEachGesture {
                val edge = EDGE_DP.dp.toPx()
                val travel = TRAVEL_DP.dp.toPx().coerceAtLeast(1f)
                val commitAt = COMMIT_DP.dp.toPx()
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

    // 回退栈少了一页 = 这次是"返回"，不管它是我们的手势、页面上的返回箭头还是系统手势派发的，
    // 都给刚露出来的这一页一把从糊到清的过渡。
    // 这里只看设置开关、不看 canPop：划回首页那一刻 previousBackStackEntry 已经是 null，
    // 用 canPop 卡住的话最常用那一下（详情页返回根页）反而没有模糊。
    // navigation 2.9 把回退栈监听删掉了，只剩 destination 监听，而它分不清前进还是后退，
    // 所以盯 currentBackStack 的长度：变短就是返回。
    LaunchedEffect(navController, enabled) {
        if (!enabled) return@LaunchedEffect
        var lastSize = navController.currentBackStack.value.size
        snapshotFlow { navController.currentBackStack.value.size }.collect { size ->
            if (size < lastSize) {
                val from = max(commitFrom, POP_BLUR_FROM)
                commitFrom = 0f
                popAnim.snapTo(from)
                popAnim.animateTo(0f, tween(POP_BLUR_MS, easing = FastOutSlowInEasing))
            }
            lastSize = size
        }
    }

    val progress = if (drag > settleAnim.value) drag else settleAnim.value
    val blurProgress = if (!enabled) 0f else max(progress, popAnim.value)
    return LxBackMotion(
        progress = if (canPop) progress else 0f,
        blurProgress = blurProgress,
        modifier = edgeModifier,
    )
}
