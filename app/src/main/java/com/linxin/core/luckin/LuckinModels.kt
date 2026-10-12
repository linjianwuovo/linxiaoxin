package com.linxin.core.luckin

import com.google.gson.annotations.SerializedName

/**
 * 瑞幸界面共用的数据形状。
 *
 * 原来这些类挂在 LuckinMcp 里，而 LuckinMcp 是官方 AI 开放平台那个 JSON-RPC 客户端。
 * 2026-10-12 把整条链路换成瑞幸自家网关（capi）之后，MCP 客户端整个删掉了，
 * 形状留在这儿——它们本来就只是界面要显示的字段，跟用哪家接口无关。
 */
data class SubAttr(
    @SerializedName("attributeId") val attributeId: Long,
    @SerializedName("attributeName") val attributeName: String,
    @SerializedName("selected") val selected: Boolean?,
)

data class ProductAttr(
    @SerializedName("attributeId") val attributeId: Long,
    @SerializedName("attributeName") val attributeName: String,
    @SerializedName("productSubAttrs") val subAttrs: List<SubAttr>?,
)

data class Product(
    @SerializedName("productId") val productId: Long,
    @SerializedName("productName") val productName: String,
    @SerializedName("skuCode") val skuCode: String?,
    @SerializedName("pictureUrl") val pictureUrl: String?,
    @SerializedName("initialPrice") val initialPrice: Double?,
    @SerializedName("estimatePrice") val estimatePrice: Double?,
    @SerializedName("productAttrs") val productAttrs: List<ProductAttr>?,
    @SerializedName("tags") val tags: List<String>?,
)

data class Shop(
    @SerializedName("deptId") val deptId: Long,
    @SerializedName("deptName") val deptName: String,
    @SerializedName("address") val address: String?,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("workTimeStart") val workTimeStart: String?,
    @SerializedName("workTimeEnd") val workTimeEnd: String?,
    /** 距给定的坐标多少公里，服务端算好的 */
    @SerializedName("distance") val distanceKm: Double?,
    @SerializedName("number") val number: String?,
    @SerializedName("workStatus") val workStatus: String?,
    /** 城市号。预览和下单要带，给 0 瑞幸就不算价 */
    @SerializedName("cityId") val cityId: Int = 0,
)

data class PreviewLine(
    val name: String,
    val amount: Int,
    val addition: String,
    val estimatePrice: Double,
)

data class Preview(
    val shopName: String,
    /** 预计可取的毫秒时间戳，0 表示没给 */
    val aboutTime: Long,
    val totalInitialPrice: Double,
    val privilegeMoney: Double,
    val discountPrice: Double,
    val couponCodeList: List<String>,
    val lines: List<PreviewLine>,
)

/** 一条点单：商品 + 规格 + 数量 */
fun lineItem(productId: Long, skuCode: String, amount: Int): Map<String, Any?> =
    mapOf("productId" to productId, "skuCode" to skuCode, "amount" to amount)
