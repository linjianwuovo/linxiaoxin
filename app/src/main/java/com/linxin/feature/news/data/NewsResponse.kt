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

/**
 * 门户全局搜索 `appService/homeQuery.do` —— 安小信的公告搜索走的就是这条，
 * 不是 `news/getNewsList.do`。依据是它 APK 里 `classes.dex` 的调用链：
 * `NewsSearchActivity.loadSearchList` → `RemoteDataSource.querySingleHome(ctx, 关键词, "3", cb)`，
 * 方法体里就是 `map.put("name", 关键词)` + `map.put("type", "3")` + `addCommonParams` →
 * POST `https://in.aiit.edu.cn/zhxy-new-scps/appService/homeQuery.do`。
 *
 * 一次返回三段：`contactsVo`（通讯录）/ `newsVo`（公告）/ `serviceVo`（服务），我们只取 newsVo。
 * newsVo 的条目字段与列表接口的 `data.data` 同名（`id/bt/publishPerson/publishTime/image1`），所以复用 [NewsRow]。
 */
data class HomeQueryResponse(
    val flag: Boolean?,
    val msg: String?,
    val data: HomeQueryRow?,
)

data class HomeQueryRow(
    val newsVo: List<NewsRow>?,
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
