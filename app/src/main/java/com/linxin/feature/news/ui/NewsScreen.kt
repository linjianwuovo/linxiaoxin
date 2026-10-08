package com.linxin.feature.news.ui

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSand
import com.linxin.feature.news.domain.NewsItem
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底栏「公告」页：校方门户资讯里的「通知公告」一个分类。
 * 作为首页 pager 的一页，所以自己带 Scaffold 但不带 topBar。
 */
@Composable
fun NewsScreen(
    onNewsClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = stringResource(R.string.ui_035),
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
                color = LxInk,
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 6.dp),
            )

            val error = uiState.error
            when {
                uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize())
                error != null && uiState.items.isEmpty() -> LxError(
                    message = error,
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.items.isEmpty() -> LxEmpty(
                    message = stringResource(R.string.ui_036),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> NewsList(
                    items = uiState.items,
                    isLoadingMore = uiState.isLoadingMore,
                    onLoadMore = viewModel::loadMore,
                    onNewsClick = onNewsClick,
                )
            }
        }
    }
}

@Composable
private fun NewsList(
    items: List<NewsItem>,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onNewsClick: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            NewsCard(
                item = item,
                onClick = { onNewsClick(item.id) },
                modifier = Modifier.animateItem(),
            )
        }
        if (isLoadingMore) {
            item(key = "news_loading_more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsCard(
    item: NewsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LxCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "公告" },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = LxInk,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append(item.publisher)
                        if (item.publishTime.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(item.publishTime.substringBefore(' '))
                        }
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val cover = item.coverUrl
            if (cover != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(LxSand),
                ) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
