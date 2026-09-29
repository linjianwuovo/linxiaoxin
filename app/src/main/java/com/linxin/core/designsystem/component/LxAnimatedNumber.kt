package com.linxin.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

@Composable
fun LxAnimatedNumber(
    targetValue: Double,
    modifier: Modifier = Modifier,
    format: String = "%.1f",
    style: TextStyle = MiuixTheme.textStyles.headline1,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.Unspecified,
    durationMillis: Int = 800,
) {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = tween(durationMillis),
        label = "number_anim",
    )

    Text(
        text = String.format(Locale.CHINA, format, animatedValue.toDouble()),
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
    )
}
