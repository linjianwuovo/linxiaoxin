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
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
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
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

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
