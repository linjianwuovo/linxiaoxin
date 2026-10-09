package com.linxin.feature.messages.data

/** `mesDetailListNew.do` 原样响应：外层 flag/code/msg/data，data 是分页壳，列表在 data.data。 */
data class MessageListResponse(
    val flag: Boolean?,
    val code: String?,
    val msg: String?,
    val data: MessagePageRow?,
)

data class MessagePageRow(
    val currentPage: Int?,
    val pageSize: Int?,
    val totalPage: Int?,
    val totalRecords: Int?,
    val hasNextPage: Boolean?,
    val hasPrePage: Boolean?,
    val data: List<MessageRow>?,
)

data class MessageRow(
    val id: String?,
    val title: String?,
    val content: String?,
    val url: String?,
    val contentTypeCode: String?,
    val contentTypeName: String?,
    val systemCode: String?,
    val createUser: String?,
    val sendDate: String?,
    /** "1" = 已读。服务端有标记已读的接口（`readPushMessage.do`），本地那层只是补充记忆 */
    val readFlag: String?,
    // 抓包原样里还有 createDate，但它是 {year,month,...} 对象不是字符串，界面只用 sendDate，这里不接
)

/**
 * `batchReadPushMessage.do` / `readPushMessage.do` 的外层。
 * 厂商代码里判成败只看 `flag`，失败提示它读的是 `message`（列表接口那份才是 `msg`），
 * 两个都接下来，取到哪个用哪个。
 */
data class MessageFlagResponse(
    val flag: Boolean?,
    val code: String?,
    val msg: String?,
    val message: String?,
)

data class UnreadCountResponse(
    val flag: Boolean?,
    val code: String?,
    val msg: String?,
    val data: UnreadCountRow?,
)

data class UnreadCountRow(
    val date: String?,
    val count: Int?,
)

/** 一键已读的结果：标上了几条、几条没成，界面按这两个数说话。 */
data class MarkAllReadOutcome(
    val marked: Int,
    val failed: Int,
)

/** 界面用的一页消息。 */
data class MessagePage(
    val items: List<AppMessage>,
    val hasNext: Boolean,
    val totalRecords: Int?,
)

/** 界面用的一条消息，已经去掉空值。 */
data class AppMessage(
    val id: String,
    val title: String,
    val content: String,
    val typeName: String,
    val sendDate: String,
    val read: Boolean,
    val url: String?,
)
