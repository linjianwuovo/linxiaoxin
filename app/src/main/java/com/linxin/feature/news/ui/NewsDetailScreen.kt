package com.linxin.feature.news.ui

import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.network.ApiConstants
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 公告详情。正文是校方 ueditor 产出的富文本 HTML，没有原生渲染组件，
 * 所以这里用 WebView 承载 —— 全 app 唯一一处 WebView。
 */
@Composable
fun NewsDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewsDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val article = uiState.article

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_news_detail), onBack = onBack) },
    ) { padding ->
        val box = Modifier.padding(padding)
        when {
            uiState.isLoading -> LxLoading(modifier = box.fillMaxSize())
            uiState.error != null -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::retry,
                modifier = box.fillMaxSize(),
            )
            article != null -> Column(modifier = box.fillMaxSize()) {
                Text(
                    text = article.title,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                )
                Text(
                    text = listOf(article.publisher, article.publishTime)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 12.dp,
                    ),
                )
                HorizontalDivider(
                    color = MiuixTheme.colorScheme.dividerLine,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                // key(html)：正文变了就重建 WebView，而不是每次重组都重新加载
                key(article.html) {
                    NewsWebView(
                        html = article.html,
                        textColorArgb = MiuixTheme.colorScheme.onSurface.toArgb(),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NewsWebView(
    html: String,
    textColorArgb: Int,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0)
                // 正文只是排版，不需要脚本；关掉 JS 顺带挡掉富文本里可能夹带的脚本
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                isVerticalScrollBarEnabled = false
                loadDataWithBaseURL(
                    ApiConstants.BASE_MAIN + "/",
                    page(html, textColorArgb),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

/** 图片按宽度缩放，正文颜色跟随主题，深色下不会出现白底黑字 */
private fun page(html: String, textColorArgb: Int): String {
    val hex = String.format("%06X", textColorArgb and 0xFFFFFF)
    return buildString {
        append("<style>")
        append("body{margin:0;color:#").append(hex).append(";font-size:15px;line-height:1.75;}")
        append("img{max-width:100%;height:auto;}")
        append("p{margin:0 0 12px;}")
        append("table{max-width:100%;}")
        append("</style>")
        append("<body>").append(html).append("</body>")
    }
}
