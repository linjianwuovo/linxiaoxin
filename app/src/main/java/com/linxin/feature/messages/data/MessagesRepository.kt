package com.linxin.feature.messages.data

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
}

@Module
@InstallIn(SingletonComponent::class)
object MessagesModule {

    @Provides
    @Singleton
    fun provideMessagesApi(@MainRetrofit retrofit: Retrofit): MessagesApi =
        retrofit.create(MessagesApi::class.java)
}
