package com.linxin.feature.news.data

/**
 * zhxy-new-scps 下 news 这组接口的响应。
 *
 * 坑：这套接口的 `code` 返回 **null**，成功与否只看 `flag`。
 * 不能套用 dorm/sports 那边 `code in ("0","200") && flag != false` 的判据，
 * 否则每个成功响应都会被当成失败。
 */
data class NewsTypeResponse(
    val flag: Boolean?,
    val msg: String?,
    val data: List<NewsTypeRow>?,
)

data class NewsTypeRow(
    val typeNum: String?,
    val type: String?,
)

data class NewsListResponse(
    val flag: Boolean?,
    val msg: String?,
    val data: NewsPageRow?,
)

data class NewsPageRow(
    val currentPage: Int?,
    val pageSize: Int?,
    val totalPage: Int?,
    val hasNextPage: Boolean?,
    val data: List<NewsRow>?,
)

data class NewsRow(
    val id: String?,
    /** 标题（biaoti） */
    val bt: String?,
    /** 发布单位 */
    val publishPerson: String?,
    val publishTime: String?,
    val image1: String?,
)

data class NewsDetailResponse(
    val flag: Boolean?,
    val msg: String?,
    val data: NewsDetailRow?,
)

data class NewsDetailRow(
    val id: String?,
    val bt: String?,
    /** ueditor 生成的富文本 HTML，图片多为站内相对路径 */
    val content: String?,
    val publishPerson: String?,
    val publishTime: String?,
)
