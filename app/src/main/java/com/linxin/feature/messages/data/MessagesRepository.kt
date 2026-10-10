package com.linxin.feature.messages.data

import com.linxin.core.network.failureReason
import com.linxin.core.network.netFail
import com.linxin.core.auth.TokenManager
import com.linxin.core.network.MainRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit

@Singleton
class MessagesRepository @Inject constructor(
    private val api: MessagesApi,
    private val tokenManager: TokenManager,
) {
    suspend fun getMessages(page: Int = 1, pageSize: Int = 10): Result<MessagePage> {
        return try {
            val userCode = tokenManager.getUserCode().orEmpty()
            val response = api.getMessages(
                currentPage = page,
                pageSize = pageSize,
                accessToken = tokenManager.getAccessToken().orEmpty(),
                xh = userCode,
                userCode = userCode,
            )
            if (response.flag != true) {
                return Result.failure(Exception(response.msg ?: "获取消息失败"))
            }
            val rows = response.data?.data.orEmpty()
            Result.success(
                MessagePage(
                    items = rows.mapNotNull { row ->
                        val id = row.id ?: return@mapNotNull null
                        AppMessage(
                            id = id,
                            title = row.title.orEmpty().trim(),
                            content = row.content.orEmpty().trim(),
                            typeName = row.contentTypeName.orEmpty().trim(),
                            sendDate = row.sendDate.orEmpty().trim(),
                            read = row.readFlag == "1",
                            url = row.url?.takeIf { it.isNotBlank() },
                        )
                    },
                    hasNext = response.data?.hasNextPage ?: (rows.size >= pageSize),
                    totalRecords = response.data?.totalRecords,
                ),
            )
        } catch (e: Exception) {
            Result.failure(netFail(e, "获取消息失败"))
        }
    }

    suspend fun unreadCount(): Result<Int> {
        return try {
            val userCode = tokenManager.getUserCode().orEmpty()
            val response = api.getUnreadCount(
                accessToken = tokenManager.getAccessToken().orEmpty(),
                xh = userCode,
                userCode = userCode,
            )
            if (response.flag != true) {
                return Result.failure(Exception(response.msg ?: "获取未读数失败"))
            }
            Result.success(response.data?.count ?: 0)
        } catch (e: Exception) {
            Result.failure(netFail(e, "获取未读数失败"))
        }
    }

    /**
     * 一键已读：把列表里服务端还记着未读的消息，逐条发 `readPushMessage.do`。
     *
     * 不用厂商那个 `batchReadPushMessage.do`：真机验过它回 `flag:true` 却不动这批消息的
     * `readFlag`（它管的是「待阅流程」），拿它当一键已读等于骗人。逐条那个是真的写成：
     * 点一条，杀进程重进未读数就少一条。
     *
     * 列表是分页的，所以先把每页的未读 id 捞全再发，最多 [MARK_ALL_MAX_PAGES] 页兜个底。
     */
    suspend fun markAllRead(): Result<MarkAllReadOutcome> {
        val unread = ArrayList<String>()
        var page = 1
        while (page <= MARK_ALL_MAX_PAGES) {
            val list = getMessages(page = page)
            if (list.isFailure) {
                return Result.failure(list.exceptionOrNull() ?: Exception("消息列表没拿到"))
            }
            val items = list.getOrThrow().items
            unread += items.filterNot { it.read }.map { it.id }
            if (!list.getOrThrow().hasNext) break
            page++
        }
        var marked = 0
        var failed = 0
        var firstError: String? = null
        unread.distinct().forEach { id ->
            val one = markRead(id)
            if (one.isSuccess) {
                marked++
            } else {
                failed++
                if (firstError == null) firstError = one.exceptionOrNull()?.message
            }
        }
        if (marked == 0 && failed > 0) {
            return Result.failure(Exception(firstError ?: "一条都没标上"))
        }
        return Result.success(MarkAllReadOutcome(marked = marked, failed = failed))
    }

    /** 单条已读。列表行点进去时和安小信一样发这个请求，`id` 用原始那串。 */
    suspend fun markRead(id: String): Result<Unit> {
        return try {
            val userCode = tokenManager.getUserCode().orEmpty()
            val response = api.markRead(
                id = id,
                accessToken = tokenManager.getAccessToken().orEmpty(),
                xh = userCode,
                userCode = userCode,
            )
            if (response.flag != true) {
                return Result.failure(
                    Exception(failureReason(response.msg, response.message, fallback = "这条没标记上")),
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(netFail(e, "这条没标记上"))
        }
    }

    private companion object {
        /** 未读 id 最多翻这么多页，防着服务端 `hasNextPage` 抽风停不下来 */
        const val MARK_ALL_MAX_PAGES = 20
    }
}

@Module
@InstallIn(SingletonComponent::class)
object MessagesModule {

    @Provides
    @Singleton
    fun provideMessagesApi(@MainRetrofit retrofit: Retrofit): MessagesApi =
        retrofit.create(MessagesApi::class.java)
}
