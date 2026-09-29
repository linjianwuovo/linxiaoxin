package com.linxin.feature.theme.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Text
import com.linxin.core.designsystem.theme.LxCard
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.settings.DEFAULT_ACCENT_ARGB
import kotlin.math.roundToInt

private fun hsvColor(h: Float, s: Float, v: Float): Color =
    Color(AndroidColor.HSVToColor(floatArrayOf(h, s, v)))

private val hueStops: List<Color> =
    (0..360 step 30).map { hsvColor(it.toFloat(), 1f, 1f) }

/**
 * 完整取色器（HSV）：上方饱和度/明度二维面板 + 下方色相条 + 预览。
 * 点按或拖动均可，确认后回传 ARGB Int。
 */
@Composable
fun ColorPickerDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initHsv = remember(initial) {
        FloatArray(3).also { AndroidColor.colorToHSV(initial, it) }
    }
    val hueState = remember { mutableFloatStateOf(initHsv[0]) }
    val satState = remember { mutableFloatStateOf(if (initHsv[1] <= 0f) 1f else initHsv[1]) }
    val valState = remember { mutableFloatStateOf(if (initHsv[2] <= 0f) 1f else initHsv[2]) }
    val hue = hueState.floatValue
    val sat = satState.floatValue
    val value = valState.floatValue

    val currentArgb = remember(hue, sat, value) {
        AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
    }
    val current = Color(currentArgb)
    val hex = remember(currentArgb) { String.format("#%06X", currentArgb and 0xFFFFFF) }

    val density = LocalDensity.current
    val thumbR = with(density) { 10.dp.toPx() }.roundToInt()

    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var hueSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(LxCard)
                .padding(20.dp),
        ) {
            Text(
                text = "选择主题色",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "影响全站主色调、首页底部氛围光等",
                fontSize = 12.sp,
                color = LxInkMuted,
            )
            Spacer(Modifier.height(18.dp))

            // ── 饱和度 / 明度 二维面板 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Color.White, hsvColor(hue, 1f, 1f))))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    .onSizeChanged { padSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            if (size.width > 0 && size.height > 0) {
                                satState.floatValue = (off.x / size.width).coerceIn(0f, 1f)
                                valState.floatValue = 1f - (off.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { off ->
                                if (size.width > 0 && size.height > 0) {
                                    satState.floatValue = (off.x / size.width).coerceIn(0f, 1f)
                                    valState.floatValue = 1f - (off.y / size.height).coerceIn(0f, 1f)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (size.width > 0 && size.height > 0) {
                                    satState.floatValue =
                                        (change.position.x / size.width).coerceIn(0f, 1f)
                                    valState.floatValue =
                                        1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                }
                            },
                        )
                    },
            ) {
                if (padSize != IntSize.Zero) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (sat * padSize.width).roundToInt() - thumbR,
                                    ((1f - value) * padSize.height).roundToInt() - thumbR,
                                )
                            }
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(current, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── 色相条 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.horizontalGradient(hueStops))
                    .onSizeChanged { hueSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            if (size.width > 0) {
                                hueState.floatValue = (off.x / size.width * 360f).coerceIn(0f, 360f)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { off ->
                                if (size.width > 0) {
                                    hueState.floatValue =
                                        (off.x / size.width * 360f).coerceIn(0f, 360f)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (size.width > 0) {
                                    hueState.floatValue =
                                        (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                                }
                            },
                        )
                    },
            ) {
                if (hueSize != IntSize.Zero) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (hue / 360f * hueSize.width).roundToInt() - thumbR,
                                    (hueSize.height / 2) - thumbR,
                                )
                            }
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(hsvColor(hue, 1f, 1f), CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── 预览 + hex ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(current)
                        .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = hex,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = LxInk,
                    )
                    Text(
                        text = "拖动上方色块与色相条取色",
                        fontSize = 11.sp,
                        color = LxInkMuted,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── 操作按钮 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DialogButton(text = "默认蓝", modifier = Modifier.weight(1f)) {
                    val d = FloatArray(3)
                        .also { AndroidColor.colorToHSV(DEFAULT_ACCENT_ARGB, it) }
                    hueState.floatValue = d[0]
                    satState.floatValue = d[1]
                    valState.floatValue = d[2]
                }
                DialogButton(text = "取消", modifier = Modifier.weight(1f), onClick = onDismiss)
                DialogButton(
                    text = "确定",
                    modifier = Modifier.weight(1f),
                    filled = true,
                    fill = current,
                ) { onConfirm(currentArgb) }
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    fill: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    val bg = if (filled) fill else LxInkMuted.copy(alpha = 0.10f)
    val fg = if (filled) Color.White else LxInk
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}
