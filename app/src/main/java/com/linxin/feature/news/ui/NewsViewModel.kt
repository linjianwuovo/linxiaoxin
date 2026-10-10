package com.linxin.feature.news.ui

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.core.locale.AppText
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
    // 搜索：照抄安小信的实现 —— 公告搜索走的不是 news/getNewsList.do，
    // 而是门户全局搜索 appService/homeQuery.do（body: name=关键词, type=3），
    // 服务端返回 contactsVo / newsVo / serviceVo 三段，我们只取 newsVo。结果整段来自服务端。
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<NewsItem>? = null,
    val searchError: String? = null,
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState

    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        searchJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    isSearching = false,
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
        if (state.isLoading || state.isLoadingMore || state.query.isNotBlank() || !state.hasMore) return

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
     * 输入搜索词：300ms 去抖后打一次门户全局搜索（安小信就是这么做的），
     * 结果整段替换，不边打字边蹦半成品。
     */
    fun setQuery(text: String) {
        searchJob?.cancel()
        if (text.isBlank()) {
            _uiState.update {
                it.copy(query = text, isSearching = false, searchResults = null, searchError = null)
            }
            return
        }
        _uiState.update { it.copy(query = text, searchError = null) }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (_uiState.value.query != text) return@launch
            searchNow()
        }
    }

    /** 用当前词立即搜一次；重试按钮也走这里 */
    fun searchNow() {
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            withNetworkRetry { repository.search(q) }.fold(
                onSuccess = { rows ->
                    // 回来时词已经变了就不落这一批，避免旧结果盖新结果
                    if (_uiState.value.query.trim() == q) {
                        _uiState.update {
                            it.copy(isSearching = false, searchResults = rows, searchError = null)
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = null,
                            searchError = e.message ?: AppText.str(R.string.news_search_failed),
                        )
                    }
                },
            )
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
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

        /** 停 300ms 再发请求，避免每敲一个字打一次门户搜索 */
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

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
