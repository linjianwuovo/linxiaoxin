package com.linxin.feature.messages.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSand
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.messages.MessagePrefs
import com.linxin.feature.messages.data.AppMessage
import com.linxin.feature.messages.data.MessagesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class MessagesUiState(
    val items: List<AppMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val onlyUnread: Boolean = false,
    val readIds: Set<String> = emptySet(),
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
) {
    /** 服务端 readFlag 之外，再叠一层本地已读（门户没有标记已读的接口） */
    val shown: List<AppMessage>
        get() {
            val merged = items.map { if (it.read || it.id in readIds) it.copy(read = true) else it }
            return if (onlyUnread) merged.filterNot { it.read } else merged
        }

    val localUnread: Int
        get() = shown.count { !it.read }

    /** 顶栏未读数：按列表里的 readFlag 叠本地已读算，不用 notReadCount.do（它对这批消息恒回 0） */
    val mergedUnread: Int
        get() = items.count { !it.read && it.id !in readIds }
}

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val repository: MessagesRepository,
    private val prefs: MessagePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val readIds = prefs.readIds()
            repository.getMessages(page = 1).fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            items = page.items,
                            isLoading = false,
                            error = null,
                            hasMore = page.hasNext,
                            currentPage = 1,
                            readIds = readIds,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "消息加载失败", readIds = readIds)
                    }
                },
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = state.currentPage + 1
            repository.getMessages(page = nextPage).fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            items = it.items + page.items,
                            isLoadingMore = false,
                            hasMore = page.hasNext,
                            currentPage = nextPage,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                },
            )
        }
    }

    fun setOnlyUnread(value: Boolean) {
        _uiState.update { it.copy(onlyUnread = value) }
    }

    fun markRead(id: String) {
        if (id.isBlank() || id in _uiState.value.readIds) return
        viewModelScope.launch {
            prefs.markRead(id)
            _uiState.update { it.copy(readIds = it.readIds + id) }
        }
    }
}

/**
 * 底栏「消息」页：门户消息中心（`msgCenter/mesDetailListNew.do`）。
 * 和其它 pager 页一样自己带 Scaffold 但不带 topBar。
 */
@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = stringResource(R.string.tab_messages),
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
                color = LxInk,
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 6.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LxFilterChip(
                    label = stringResource(R.string.msg_filter_all),
                    selected = !uiState.onlyUnread,
                    onClick = { viewModel.setOnlyUnread(false) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                LxFilterChip(
                    label = stringResource(R.string.msg_filter_unread),
                    selected = uiState.onlyUnread,
                    onClick = { viewModel.setOnlyUnread(true) },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.msg_unread_count, uiState.mergedUnread),
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                )
            }

            when {
                uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize())
                uiState.error != null && uiState.items.isEmpty() -> LxError(
                    message = uiState.error!!,
                    onRetry = viewModel::load,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.shown.isEmpty() -> LxEmpty(
                    message = stringResource(
                        if (uiState.onlyUnread) R.string.msg_all_read else R.string.msg_empty,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> MessageList(
                    items = uiState.shown,
                    isLoadingMore = uiState.isLoadingMore,
                    onLoadMore = viewModel::loadMore,
                    onRead = viewModel::markRead,
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    items: List<AppMessage>,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onRead: (String) -> Unit,
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
        items(items, key = { it.id }) { message ->
            MessageCard(message = message, onClick = { onRead(message.id) }, modifier = Modifier.animateItem())
        }
        if (isLoadingMore) {
            item(key = "messages_loading_more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: AppMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 未读时点一下先标记已读并展开正文；再点收起。门户没有"读消息"的详情页，正文就在列表里。
    var expanded by remember(message.id) { mutableStateOf(false) }

    LxCard(onClick = {
        if (!expanded) onClick()
        expanded = !expanded
    }, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (message.read) androidx.compose.ui.graphics.Color.Transparent else LxTerra),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.title.ifBlank { stringResource(R.string.tab_messages) },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = if (message.read) FontWeight.Normal else FontWeight.SemiBold,
                    color = LxInk,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.content,
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = listOf(message.typeName, message.sendDate.substringBefore(' '))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
