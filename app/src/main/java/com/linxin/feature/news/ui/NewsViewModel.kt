package com.linxin.feature.news.ui

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.feature.news.data.NewsRepository
import com.linxin.feature.news.domain.NewsArticle
import com.linxin.feature.news.domain.NewsItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class NewsUiState(
    val items: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
    // 搜索：我们抓过的公告请求里没有关键字字段，安小信有没有服务端搜索尚未取证，
    // 所以先按"翻页扫完 + 本地匹配"实现，等拿到原 App 的搜索请求再决定要不要换成服务端。
    val query: String = "",
    val isScanning: Boolean = false,
    val scanFinished: Boolean = false,
    val scanCapped: Boolean = false,
    val scanCap: Int = NEWS_SCAN_PAGE_CAP * NEWS_PAGE_SIZE,
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState

    private var scanJob: Job? = null

    init {
        load()
    }

    fun load() {
        scanJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    isScanning = false,
                    scanFinished = false,
                    scanCapped = false,
                )
            }
            withNetworkRetry { repository.getNoticeList(page = 1) }.fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            items = page.items,
                            hasMore = page.hasNext,
                            isLoading = false,
                            currentPage = 1,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "公告加载失败")
                    }
                },
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.isScanning || !state.hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = state.currentPage + 1
            withNetworkRetry { repository.getNoticeList(page = nextPage) }.fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            items = it.items + page.items,
                            hasMore = page.hasNext,
                            isLoadingMore = false,
                            currentPage = nextPage,
                        )
                    }
                },
                onFailure = {
                    // 翻页失败只停住，不清掉已经加载出来的列表
                    _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                },
            )
        }
    }

    fun retry() = load()

    /**
     * 输入搜索词。接口不收标题关键字，所以能匹配的范围 = 已经翻下来的页；
     * 词非空且还没扫完时，后台把剩下的页补完，一次给全结果，不做"边加载边蹦"。
     */
    fun setQuery(text: String) {
        _uiState.update { it.copy(query = text) }
        scanJob?.cancel()
        if (text.isBlank()) return
        val s = _uiState.value
        if (s.isLoading || s.scanFinished) return
        scanJob = viewModelScope.launch {
            delay(SCAN_DEBOUNCE_MS)
            if (_uiState.value.query != text) return@launch
            scan(s.currentPage)
        }
    }

    /** 从 startPage+1 开始把栏目翻到底（最多 SCAN_PAGE_CAP 页），期间可被新输入取消。 */
    private suspend fun scan(startPage: Int) {
        _uiState.update { it.copy(isScanning = true) }
        var page = startPage + 1
        var capped = false
        try {
            while (true) {
                val cur = _uiState.value
                if (!cur.hasMore) break
                if (page > NEWS_SCAN_PAGE_CAP) { capped = true; break }
                val next = withNetworkRetry { repository.getNoticeList(page = page) }.getOrNull()
                if (next == null) {
                    // 扫描途中某页失败：停住但保留已翻到的结果，别把列表清空
                    _uiState.update { it.copy(hasMore = false) }
                    break
                }
                _uiState.update {
                    it.copy(
                        items = it.items + next.items,
                        hasMore = next.hasNext,
                        currentPage = page,
                    )
                }
                if (!next.hasNext) break
                page++
            }
        } finally {
            val finished = !_uiState.value.hasMore
            _uiState.update {
                it.copy(
                    isScanning = false,
                    scanCapped = capped,
                    scanFinished = !capped && finished,
                )
            }
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }

    /** 只对连接类失败退避重试；服务端业务错误（非法访问 / 500）不重 */
    private suspend fun <T> withNetworkRetry(block: suspend () -> Result<T>): Result<T> {
        var last = block()
        for (backoff in RETRY_BACKOFF_MS) {
            if (last.exceptionOrNull()?.cause !is IOException) return last
            delay(backoff)
            last = block()
        }
        return last
    }

    private companion object {
        val RETRY_BACKOFF_MS = longArrayOf(600L, 1800L)

        /** 停 300ms 再扫，避免每敲一个字都从头翻页 */
        const val SCAN_DEBOUNCE_MS = 300L
    }
}

/** 每页 10 条、最多翻 30 页（300 条）封顶：接口协议保持原 App 的 pageSize，不靠加大页量偷懒 */
private const val NEWS_PAGE_SIZE = 10
private const val NEWS_SCAN_PAGE_CAP = 30

data class NewsDetailUiState(
    val article: NewsArticle? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class NewsDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NewsRepository,
) : ViewModel() {

    private val newsId: String = savedStateHandle.get<String>("newsId").orEmpty()

    private val _uiState = MutableStateFlow(NewsDetailUiState())
    val uiState: StateFlow<NewsDetailUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // 公告接口在门户域名下，同样不复用连接，偶发握手失败值得自动补一次
            var result = repository.getDetail(newsId)
            for (backoff in RETRY_BACKOFF_MS) {
                if (result.exceptionOrNull()?.cause !is IOException) break
                delay(backoff)
                result = repository.getDetail(newsId)
            }
            result.fold(
                onSuccess = { article ->
                    _uiState.update { it.copy(article = article, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "公告加载失败")
                    }
                },
            )
        }
    }

    fun retry() = load()

    private companion object {
        val RETRY_BACKOFF_MS = longArrayOf(600L, 1800L)
    }
}
