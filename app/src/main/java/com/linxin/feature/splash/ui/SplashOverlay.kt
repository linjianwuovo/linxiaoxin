package com.linxin.feature.splash.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.linxin.R
import com.linxin.core.designsystem.theme.LxTerra

/** 应用内开屏：背景随主题色（LocalLxAccent 经 LxTerra 解析，含深色提亮）动态渐变。 */
@Composable
fun SplashOverlay(modifier: Modifier = Modifier) {
    val accent = LxTerra
    val gradient = Brush.verticalGradient(
        colors = listOf(accent, lerp(accent, Color.Black, 0.42f)),
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.height(18.dp))
            BasicText(
                text = stringResource(R.string.ui_074),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 4.sp,
                ),
            )
        }
    }
}
