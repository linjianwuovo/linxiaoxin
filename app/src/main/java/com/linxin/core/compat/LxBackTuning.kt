package com.linxin.core.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.linxin.core.designsystem.theme.LxInk

/**
 * 侧滑返回的调参面板用的共享参数。临时件：调好之后这些数会写死进
 * rememberLxBackMotion / NavGraph，这个文件和那张卡片一起删掉。
 *
 * 用 Compose 状态而不是普通字段：滑条一改，正在显示的形变和下一条手势都能立刻拿到新值。
 */
object LxBackTuning {
    /** 左边缘热区宽度（dp）：起手的触点要落在这里面才算这条手势 */
    val edgeDp = mutableFloatStateOf(28f)

    /** 真返回所需的滑动距离，单位 dp（主旋钮） */
    val commitDp = mutableFloatStateOf(243f)

    /** 形变走满所需的滑动距离，单位 dp */
    val travelDp = mutableFloatStateOf(266f)

    /**
     * 拉满时当前页缩到多少。AppShare 的 scaleOut 实测就是 0.85（SLIDE_SCALE/SCALE_FADE/SCALE
     * 三档都落在 0.85 上），不是 Material 惯称的 0.9。
     */
    val scaleTo = mutableFloatStateOf(0.85f)

    /** 拉满时当前页淡到多少 */
    val alphaTo = mutableFloatStateOf(0.75f)

    /** 拉满时当前页往左走多少屏宽。AppShare 的 IOS_STYLE 出场是整屏位移，但它同时画两页 */
    val slideFraction = mutableFloatStateOf(0.08f)

    /** 松手回弹时长（ms）。AppShare 全局默认 300ms + FastOutSlowIn */
    val settleDurationMs = mutableFloatStateOf(300f)

    /**
     * 探针用：要不要注册系统的 PredictiveBackHandler。
     * Mi 11（HyperOS）实测：补了 enableOnBackInvokedCallback=true 之后，贴边划和内容区划
     * 两种情况"系统派发进度峰值"都是 0.00 —— 这台机器一次进度都不给，注册它只会把
     * 原来的返回动画吃掉，所以默认关。面板上留着开关，换台机器可以再验一次。
     */
    val useSystemHandler = mutableStateOf(false)

    /** 系统这条手势这一路给过来的进度，0.00 就说明这台机器压根不派发 */
    val systemProgress = mutableFloatStateOf(0f)

    /** 峰值：返回之后面板会消失，所以留一个不清零的最大值，回来读它 */
    val systemProgressMax = mutableFloatStateOf(0f)

    /** 系统一旦派发过进度，就把我们自己的边缘手势关掉，免得两边各弹一次 */
    val systemOwnsGesture = mutableStateOf(false)
}

@Composable
fun LxBackTuningCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF6D8))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = "返回手势调参（临时，正式版不带）",
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            color = LxInk,
        )
        TuneSlider("触发返回的滑动距离", LxBackTuning.commitDp, 40f, 400f, "dp")
        TuneSlider("形变走满的滑动距离", LxBackTuning.travelDp, 40f, 440f, "dp")
        TuneSlider("拉满时缩到", LxBackTuning.scaleTo, 0.70f, 1.00f, "raw")
        TuneSlider("拉满时淡到", LxBackTuning.alphaTo, 0.40f, 1.00f, "raw")
        TuneSlider("拉满时左移屏宽", LxBackTuning.slideFraction, 0f, 0.35f, "percent")
        TuneSlider("松手回弹时长", LxBackTuning.settleDurationMs, 120f, 600f, "ms")
        TuneSlider("左边缘热区", LxBackTuning.edgeDp, 8f, 72f, "dp")
        Text(
            text = "触发 %.0fdp · 走满 %.0fdp · 缩到 %.2f · 淡到 %.2f · 左移 %.0f%% · 回弹 %.0fms · 热区 %.0fdp"
                .format(
                    LxBackTuning.commitDp.floatValue,
                    LxBackTuning.travelDp.floatValue,
                    LxBackTuning.scaleTo.floatValue,
                    LxBackTuning.alphaTo.floatValue,
                    LxBackTuning.slideFraction.floatValue * 100f,
                    LxBackTuning.settleDurationMs.floatValue,
                    LxBackTuning.edgeDp.floatValue,
                ),
            style = MiuixTheme.textStyles.footnote2,
            color = LxInk,
        )
        // 探针：侧滑一次，这个数动就说明系统真的把进度派给了应用；一直 0.00 就是这台机器不给
        Text(
            text = "系统派发进度 %.2f（峰值 %.2f）　·　注册系统回调：%s（点这行切换）".format(
                LxBackTuning.systemProgress.floatValue,
                LxBackTuning.systemProgressMax.floatValue,
                if (LxBackTuning.useSystemHandler.value) "开" else "关",
            ),
            style = MiuixTheme.textStyles.footnote2,
            color = LxInk,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { LxBackTuning.useSystemHandler.value = !LxBackTuning.useSystemHandler.value }
                .padding(vertical = 6.dp),
        )
    }
}

/** 自己搓的滑条：应用主题只有 Miuix，没有 material3 的 MaterialTheme，用不了 SliderDefaults */
@Composable
private fun TuneSlider(
    label: String,
    state: MutableFloatState,
    from: Float,
    to: Float,
    kind: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = LxInk,
            modifier = Modifier.width(128.dp),
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .pointerInput(from, to) {
                    awaitEachGesture {
                        // 只认落在自己轨道里、且没被别人吃掉的那根手指：
                        // 不然父容器（边缘返回那条 pointerInput）消费过的事件会串进来，
                        // 把滑条当成被拖到最左边。
                        val down = awaitFirstDown(requireUnconsumed = true)
                        if (down.position.x < 0f || down.position.x > size.width) return@awaitEachGesture
                        val id = down.id
                        fun place(x: Float) {
                            val f = (x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            state.floatValue = from + (to - from) * f
                        }
                        place(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == id } ?: break
                            place(change.position.x)
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                },
        ) {
            val frac = ((state.floatValue - from) / (to - from)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x22000000)),
            )
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF2F6BFF)),
            )
            Box(
                Modifier
                    .size(16.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * frac - 8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0x33000000), CircleShape),
            )
        }
        Text(
            text = when (kind) {
                "percent" -> "%.0f%%".format(state.floatValue * 100f)
                "dp" -> "%.0fdp".format(state.floatValue)
                "ms" -> "%.0fms".format(state.floatValue)
                else -> "%.2f".format(state.floatValue)
            },
            style = MiuixTheme.textStyles.footnote2,
            color = LxInk,
            modifier = Modifier.width(56.dp),
        )
    }
}
