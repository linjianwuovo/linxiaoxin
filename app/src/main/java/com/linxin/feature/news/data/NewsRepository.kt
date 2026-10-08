package com.linxin.feature.news.data

import com.linxin.core.network.MainRetrofit
import com.linxin.feature.news.domain.NewsArticle
import com.linxin.feature.news.domain.NewsItem
import com.linxin.feature.news.domain.NewsPageResult
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import retrofit2.HttpException
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「通知公告」数据源。只做这一个分类，所以对外不暴露 typeNum。
 */
@Singleton
class NewsRepository @Inject constructor(
    private val api: NewsApi,
) {
    // 分类 id 是库里的 uuid，进程内取一次就够；不写死进代码是防校方重建栏目后失效
    @Volatile
    private var noticeTypeNum: String? = null

    suspend fun getNoticeList(page: Int, pageSize: Int = 10): Result<NewsPageResult> {
        val typeNum = noticeTypeNum().getOrElse { return Result.failure(it) }
        return try {
            val response = api.getNewsList(
                typeNum = typeNum,
                currentPage = page,
                pageSize = pageSize,
            )
            if (response.flag != true) {
                return Result.failure(Exception(response.msg ?: "获取公告列表失败"))
            }
            val rows = response.data?.data.orEmpty()
            Result.success(
                NewsPageResult(
                    items = newsItemsFromRows(rows),
                    hasNext = response.data?.hasNextPage ?: (rows.size >= pageSize),
                ),
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e), e))
        }
    }

    /**
     * 公告搜索：走门户全局搜索 `appService/homeQuery.do`，只取 `newsVo` 那段。
     * 这条接口没有分页概念，服务端一次把匹配到的公告给回。
     */
    suspend fun search(query: String): Result<List<NewsItem>> {
        val q = query.trim()
        if (q.isEmpty()) return Result.success(emptyList())
        return try {
            val response = api.searchHome(q)
            if (response.flag != true) {
                return Result.failure(Exception(response.msg ?: "搜索公告失败"))
            }
            Result.success(newsItemsFromRows(response.data?.newsVo.orEmpty()))
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e), e))
        }
    }

    suspend fun getDetail(id: String): Result<NewsArticle> {
        return try {
            val response = api.getNewsDetail(id)
            if (response.flag != true) {
                return Result.failure(Exception(response.msg ?: "获取公告详情失败"))
            }
            val data = response.data
                ?: return Result.failure(Exception("公告详情没有返回内容"))
            Result.success(
                NewsArticle(
                    id = data.id ?: id,
                    title = data.bt.orEmpty().trim(),
                    publisher = data.publishPerson.orEmpty().trim(),
                    publishTime = data.publishTime.orEmpty().trim(),
                    html = data.content.orEmpty(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e), e))
        }
    }

    private suspend fun noticeTypeNum(): Result<String> {
        noticeTypeNum?.let { return Result.success(it) }
        return try {
            val response = api.getNewsTypeList()
            if (response.flag != true) {
                Result.failure(Exception(response.msg ?: "获取公告分类失败"))
            } else {
                val hit = response.data
                    ?.firstOrNull { it.type?.contains(NOTICE_TYPE_NAME) == true }
                    ?.typeNum
                    ?.takeIf { it.isNotBlank() }
                if (hit == null) {
                    Result.failure(Exception("接口里没有「$NOTICE_TYPE_NAME」这个分类"))
                } else {
                    noticeTypeNum = hit
                    Result.success(hit)
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError(e), e))
        }
    }

    private fun mapError(error: Exception): String = when (error) {
        is HttpException -> when {
            error.code() == 401 -> "登录已失效，请重新登录"
            error.code() >= 500 -> "公告接口暂时异常，请稍后重试"
            else -> "公告加载失败（HTTP ${error.code()}）"
        }

        is IOException -> "网络异常，请检查连接后重试"
        else -> error.message ?: "公告加载失败"
    }

    private companion object {
        const val NOTICE_TYPE_NAME = "通知公告"
    }
}

/**
 * 列表与搜索共用的行 → 领域对象映射。
 * 搜索接口（homeQuery 的 newsVo）比列表多给 `content` / `contentExceptPic` / `urlList`，
 * 这里先不收 —— 点进去还是走 `news/getNewsDetail.do` 拿正文，与安小信一致。
 */
internal fun newsItemsFromRows(rows: List<NewsRow?>): List<NewsItem> =
    rows.mapNotNull { row ->
        val id = row?.id ?: return@mapNotNull null
        NewsItem(
            id = id,
            title = row.bt.orEmpty().trim(),
            publisher = row.publishPerson.orEmpty().trim(),
            publishTime = row.publishTime.orEmpty().trim(),
            coverUrl = row.image1?.takeIf { it.isNotBlank() },
        )
    }

@Module
@InstallIn(SingletonComponent::class)
object NewsModule {

    @Provides
    @Singleton
    fun provideNewsApi(@MainRetrofit retrofit: Retrofit): NewsApi =
        retrofit.create(NewsApi::class.java)
}
