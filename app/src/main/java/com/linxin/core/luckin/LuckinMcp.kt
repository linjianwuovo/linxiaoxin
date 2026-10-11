package com.linxin.core.luckin

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 瑞幸官方 AI 开放平台的 MCP 客户端（Streamable HTTP + JSON-RPC 2.0）。
 *
 * 端点 `https://gwmcp.lkcoffee.com/order/user/mcp`，鉴权是 `Authorization: Bearer <个人 token>`，
 * token 在 open.lkcoffee.com 用手机号登录后自己复制，约 30 天有效、跟瑞幸账号会话绑定。
 * 服务端无状态：initialize 不返回 Mcp-Session-Id，所以每次 tools/call 都是独立一发。
 *
 * 这一层只做只读的三件事：查门店、搜商品、看商品详情。下单/取消（createOrder / cancelOrder）
 * 是真花钱的写操作，这里刻意不做，等他逐项批了再加。
 *
 * 工具名和字段全部来自 2026-10-11 用真 token 跑通的 tools/list 与实调用，取证在仓外临时目录，
 * 不随包发布。
 */
object LuckinMcp {

    private const val ENDPOINT = "https://gwmcp.lkcoffee.com/order/user/mcp"
    private const val PROTOCOL_VERSION = "2025-06-18"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** 平台返回的 envelope：`{code, msg, data, success}`，code!=0 或 success=false 都当失败 */
    class LuckinApiError(val code: Int, override val message: String) : Exception(message)

    /** token 没配或配错了，界面据此提示去设置 */
    class NotConfigured : Exception("瑞幸 token 未配置")

    data class Shop(
        @SerializedName("deptId") val deptId: Long,
        @SerializedName("deptName") val deptName: String,
        @SerializedName("address") val address: String?,
        @SerializedName("longitude") val longitude: Double,
        @SerializedName("latitude") val latitude: Double,
        @SerializedName("workTimeStart") val workTimeStart: String?,
        @SerializedName("workTimeEnd") val workTimeEnd: String?,
        /** 距你给的坐标多少公里，平台算好的 */
        @SerializedName("distance") val distanceKm: Double?,
        @SerializedName("number") val number: String?,
        @SerializedName("workStatus") val workStatus: String?,
    )

    data class SubAttr(
        @SerializedName("attributeId") val attributeId: Long,
        @SerializedName("attributeName") val attributeName: String,
        @SerializedName("selected") val selected: Boolean?,
        @SerializedName("price") val price: Double?,
        @SerializedName("canSelected") val canSelected: Int?,
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** 一次 tools/call，返回解包后的 envelope（`{code,msg,data,success}`） */
    private suspend fun callTool(token: String, tool: String, args: Map<String, Any?>): JsonObject =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) throw NotConfigured()
            val body = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", 1)
                addProperty("method", "tools/call")
                add("params", JsonObject().apply {
                    addProperty("name", tool)
                    add("arguments", gson.toJsonTree(args).asJsonObject)
                })
            }
            val req = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json, text/event-stream")
                .post(body.toString().toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LuckinApiError(resp.code, "瑞幸接口 HTTP ${resp.code}")
                val rpc = runCatching { gson.fromJson(text, JsonObject::class.java) }
                    .getOrNull() ?: throw LuckinApiError(-1, "瑞幸接口返回不是 JSON")
                rpc.getAsJsonObject("error")?.let {
                    throw LuckinApiError(it.get("code")?.asInt ?: -1, it.get("message")?.asString ?: "MCP 调用失败")
                }
                val content = rpc.getAsJsonObject("result")?.getAsJsonArray("content")
                    ?: throw LuckinApiError(-1, "瑞幸返回里没有 content")
                val env = gson.fromJson(content[0].asJsonObject.get("text").asString, JsonObject::class.java)
                val ok = env.get("success")?.asBoolean ?: false
                val code = env.get("code")?.asInt ?: -1
                if (!ok || code != 0) throw LuckinApiError(code, env.get("msg")?.asString ?: "瑞幸返回失败")
                env
            }
        }

    private suspend fun callToolDataArray(token: String, tool: String, args: Map<String, Any?>) =
        callTool(token, tool, args).getAsJsonArray("data")

    /**
     * 附近门店。平台只吃经纬度，没有城市名搜索；deptName 传空串即"按距离给一批"。
     * 实测一次回 8 家，按 distance 升序。
     */
    suspend fun queryShops(token: String, longitude: Double, latitude: Double, deptName: String = ""): List<Shop> =
        callToolDataArray(token, "queryShopList", mapOf(
            "longitude" to longitude, "latitude" to latitude, "deptName" to deptName,
        )).map { gson.fromJson(it, Shop::class.java) }

    /** 在指定门店里按自然语言搜商品，query 就是用户原话，实测"生椰拿铁"能回 2 条 */
    suspend fun searchProducts(token: String, deptId: Long, query: String): List<Product> =
        callToolDataArray(token, "searchProductForMcp", mapOf(
            "deptId" to deptId, "query" to query,
        )).map { gson.fromJson(it, Product::class.java) }

    /** 商品详情：六组属性（温度/冰量/奶浓度/咖啡浓度/糖度/加料）都在这，切规格要用 */
    suspend fun productDetail(token: String, deptId: Long, productId: Long): Product {
        val env = callTool(token, "queryProductDetailInfo", mapOf(
            "deptId" to deptId, "productId" to productId,
        ))
        val data = env.get("data")
        val one = if (data.isJsonArray) data.asJsonArray[0] else data
        return gson.fromJson(one, Product::class.java)
    }
}
