package com.linxin.feature.news.domain

/** 公告列表项 */
data class NewsItem(
    val id: String,
    val title: String,
    val publisher: String,
    val publishTime: String,
    val coverUrl: String?,
)

/** 公告正文，html 是校方 ueditor 产出的富文本 */
data class NewsArticle(
    val id: String,
    val title: String,
    val publisher: String,
    val publishTime: String,
    val html: String,
)

data class NewsPageResult(
    val items: List<NewsItem>,
    val hasNext: Boolean,
)
