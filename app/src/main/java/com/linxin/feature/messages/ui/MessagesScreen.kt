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
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSand
import com.linxin.core.designsystem.theme.LxSuccess
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.messages.MessagePrefs
import com.linxin.feature.messages.data.AppMessage
import com.linxin.feature.messages.data.MessagesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    /** 一键已读正在跑，按钮置灰用 */
    val isMarkingAllRead: Boolean = false,
    /** 一键已读成功一次就置真，界面弹个提示后清掉 */
    val markAllReadDone: Boolean = false,
    /** 一键已读标上了几条、几条没成，提示条按这两个数说话 */
    val markAllReadMarked: Int = 0,
    val markAllReadFailed: Int = 0,
    /** 标记类操作失败的原文，一条未读没标上也要说清楚 */
    val actionError: String? = null,
) {
    /**
     * 服务端 readFlag 之外再叠一层本地已读。
     * 留着它有两个原因：`readPushMessage.do` 可能因为离线或登录态过期而没写成，界面不该因此
     * 退回未读；另外之前只在本地记过的那批 id 还在 DataStore 里。
     */
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

    /** 已经成功写给服务端的 id，避免同一条重复发 `readPushMessage.do` */
    private val markedOnServer = mutableSetOf<String>()

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
        if (id.isBlank()) return
        val state = _uiState.value
        val alreadyReadByServer = state.items.any { it.id == id && it.read }
        if (id !in state.readIds && !alreadyReadByServer) {
            _uiState.update { it.copy(readIds = it.readIds + id) }
            viewModelScope.launch { prefs.markRead(id) }
        }
        // 服务端已经是已读的不用再发；这条发失败要说出来，别默默算了
        if (alreadyReadByServer || id in markedOnServer) return
        markedOnServer += id
        viewModelScope.launch {
            repository.markRead(id).onFailure { e ->
                markedOnServer -= id
                _uiState.update { it.copy(actionError = e.message ?: "这条没标记上") }
            }
        }
    }

    /**
     * 一键已读：把服务端还记着未读的消息逐条发 `readPushMessage.do`（仓库里说了为什么不用
     * 厂商那个 batch 接口）。跑完重新拉一次列表，让服务端的 readFlag 落地。
     */
    fun markAllRead() {
        if (_uiState.value.isMarkingAllRead) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMarkingAllRead = true, actionError = null) }
            repository.markAllRead().fold(
                onSuccess = { outcome ->
                    markedOnServer.clear()
                    _uiState.update {
                        it.copy(
                            isMarkingAllRead = false,
                            markAllReadDone = true,
                            markAllReadMarked = outcome.marked,
                            markAllReadFailed = outcome.failed,
                        )
                    }
                    load()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isMarkingAllRead = false, actionError = e.message ?: "一键已读没成功")
                    }
                },
            )
        }
    }

    /** 提示弹过一次就清掉，不然重组时会重复弹 */
    fun clearActionFeedback() {
        _uiState.update { it.copy(markAllReadDone = false, actionError = null) }
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
    var confirmReadAll by remember { mutableStateOf(false) }
    val marked = uiState.markAllReadMarked
    val failed = uiState.markAllReadFailed
    val readAllDoneText = when {
        failed > 0 -> stringResource(R.string.msg_mark_all_read_partial, marked, failed)
        marked == 0 -> stringResource(R.string.msg_mark_all_read_nothing)
        else -> stringResource(R.string.msg_mark_all_read_done, marked)
    }
    // 提示不弹层：这页底下常驻着底栏，弹层压在上面，黑乎乎一块也难看，直接写在筛选行下面
    val notice = uiState.actionError ?: readAllDoneText.takeIf { uiState.markAllReadDone }
    val noticeIsError = uiState.actionError != null
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3_000)
            viewModel.clearActionFeedback()
        }
    }

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
                Spacer(modifier = Modifier.weight(1f))
                // 安小信把这个入口藏在「消息」标题的点击上（它代码里那个函数拼成了 onReadPromiss），
                // 我们放这儿，字面写清楚它干什么
                Text(
                    text = stringResource(
                        if (uiState.isMarkingAllRead) R.string.msg_mark_all_read_busy else R.string.msg_mark_all_read,
                    ),
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.SemiBold,
                    color = if (uiState.isMarkingAllRead) LxInkMuted else LxTerra,
                    modifier = Modifier
                        .clickable(enabled = !uiState.isMarkingAllRead) { confirmReadAll = true }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            if (notice != null) {
                Text(
                    text = notice,
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.SemiBold,
                    color = if (noticeIsError) LxTerra else LxSuccess,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp),
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

            if (confirmReadAll) {
                LxDialog(
                    title = stringResource(R.string.msg_mark_all_read_title),
                    message = stringResource(R.string.msg_mark_all_read_body),
                    confirmText = stringResource(R.string.action_confirm),
                    dismissText = stringResource(R.string.action_cancel),
                    onConfirm = {
                        confirmReadAll = false
                        viewModel.markAllRead()
                    },
                    onDismiss = { confirmReadAll = false },
                    onDismissRequest = { confirmReadAll = false },
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
