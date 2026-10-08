package com.linxin.feature.news.ui

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.theme.LxCream
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkFaint
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSand
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.designsystem.theme.LxShapes
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.feature.news.domain.NewsItem
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底栏「公告」页：校方门户资讯里的「通知公告」一个分类。
 * 作为首页 pager 的一页，所以自己带 Scaffold 但不带 topBar。
 *
 * 搜索实现是从安小信自己的 dex 里核对来的：`NewsSearchActivity.loadSearchList` 打的是
 * 门户全局搜索 `appService/homeQuery.do`（`name` = 关键词、`type` = "3"），
 * 服务端一次返回匹配的公告，取响应里的 `newsVo` 段 —— 不是把列表拉下来本地过滤，
 * 也不是给 `news/getNewsList.do` 编一个不存在的关键字字段。
 */
@Composable
fun NewsScreen(
    onNewsClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val query = uiState.query.trim()
    val searching = query.isNotEmpty()
    val shown = if (searching) uiState.searchResults.orEmpty() else uiState.items

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

            NewsSearchBar(
                value = uiState.query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.news_search_hint),
                clearDescription = stringResource(R.string.news_search_clear),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            )

            val listError = uiState.error
            val searchError = uiState.searchError
            when {
                searching && searchError != null -> LxError(
                    message = searchError,
                    onRetry = viewModel::searchNow,
                    modifier = Modifier.fillMaxSize(),
                )
                // 服务端整段返回，所以搜索中只显示一个进度，不边打字边蹦半成品
                searching && (uiState.isSearching || uiState.searchResults == null) -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.news_searching),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LxInkMuted,
                    )
                }
                searching && shown.isEmpty() -> LxEmpty(
                    message = stringResource(R.string.news_no_match, query),
                    modifier = Modifier.fillMaxSize(),
                )
                searching -> SearchResults(
                    count = shown.size,
                    items = shown,
                    onNewsClick = onNewsClick,
                )
                uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize())
                listError != null && uiState.items.isEmpty() -> LxError(
                    message = listError,
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

/** 搜索结果：顶部一条"找到 N 条"，下面是和列表页同款卡片。 */
@Composable
private fun SearchResults(
    count: Int,
    items: List<NewsItem>,
    onNewsClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.news_match_count, count),
            style = MiuixTheme.textStyles.footnote2,
            color = LxInkMuted,
        )
    }
    NewsList(
        items = items,
        isLoadingMore = false,
        onLoadMore = { },
        onNewsClick = onNewsClick,
    )
}

/** 胶囊形搜索框：左放大镜 + 输入 + 右清除，配色沿用 LxTextField 那套暖色 token。 */
@Composable
private fun NewsSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
) {
    val textStyle = MiuixTheme.textStyles.body1.merge(
        TextStyle(color = LxInk),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(LxShapes.small)
            .background(LxCream)
            .border(width = 1.dp, color = LxSandDeep, shape = LxShapes.small)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = LxInkMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MiuixTheme.textStyles.body1,
                    color = LxInkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(LxTerra),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
            )
        }
        if (value.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = clearDescription,
                tint = LxInkMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onValueChange("") },
            )
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
                    text = item.title.ifBlank { stringResource(R.string.ui_035) },
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
