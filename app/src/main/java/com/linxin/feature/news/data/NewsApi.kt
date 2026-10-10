package com.linxin.feature.news.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * 校园资讯接口，与安小信「资讯」页同源（in.aiit.edu.cn/zhxy-new-scps）。
 * 走门户通用鉴权：AuthInterceptor 的默认分支会往 FormBody 里追加
 * access_token / _userCode / userId / _userName / _userType / appId，
 * 抓包确认请求体里的字段就是这一套，所以这里只声明业务参数。
 */
interface NewsApi {

    @FormUrlEncoded
    @POST("news/getNewsTypeList.do")
    suspend fun getNewsTypeList(
        @Field("_") dummy: String = "",
    ): NewsTypeResponse

    @FormUrlEncoded
    @POST("news/getNewsList.do")
    suspend fun getNewsList(
        @Field("type") typeNum: String,
        @Field("currentPage") currentPage: Int,
        @Field("pageSize") pageSize: Int,
        @Field("isActivity") isActivity: String = "0",
    ): NewsListResponse

    @FormUrlEncoded
    @POST("news/getNewsDetail.do")
    suspend fun getNewsDetail(
        @Field("id") id: String,
    ): NewsDetailResponse

    /**
     * 公告搜索。字段名与取值照抄安小信：`name` = 关键词、`type` = "3"，
     * 通用鉴权字段由 AuthInterceptor 的默认分支补上，和它 addCommonParams 那套一致。
     */
    @FormUrlEncoded
    @POST("appService/homeQuery.do")
    suspend fun searchHome(
        @Field("name") keyword: String,
        @Field("type") type: String = "3",
    ): HomeQueryResponse
}
