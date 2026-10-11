package com.linxin.core.luckin

import com.google.gson.Gson
import com.google.gson.JsonElement
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

    // ---- 下面三个是写操作。界面每一步都要他自己点确认，我们只把瑞幸返回的支付凭据显示出来，绝不代付。----

    /** 购物车里的一件：官方 previewOrder/createOrder 都只认这三个字段 */
    fun lineItem(productId: Long, skuCode: String, amount: Int): Map<String, Any?> =
        mapOf("productId" to productId, "skuCode" to skuCode, "amount" to amount)

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

    /** 订单预览：只读，不建单、不占库存，用来看原价/优惠/实付/取餐时间 */
    suspend fun previewOrder(token: String, deptId: Long, items: List<Map<String, Any?>>): Preview {
        val data = callTool(token, "previewOrder", mapOf(
            "deptId" to deptId, "productList" to items,
        )).obj("data") ?: throw LuckinApiError(-1, "预览返回是空的")
        val lines = data.arr("productInfoList").map { el ->
            val o = el.asJsonObject
            PreviewLine(
                name = o.str("name").orEmpty(),
                amount = o.num("amount")?.toInt() ?: 1,
                addition = o.str("additionDesc").orEmpty(),
                estimatePrice = o.num("estimateTotalPrice") ?: o.num("estimatePrice") ?: 0.0,
            )
        }
        return Preview(
            shopName = data.obj("shopInfo")?.str("deptName").orEmpty(),
            aboutTime = data.num("aboutTime")?.toLong() ?: 0L,
            totalInitialPrice = data.num("totalInitialPrice") ?: 0.0,
            privilegeMoney = data.num("privilegeMoney") ?: 0.0,
            discountPrice = data.num("discountPrice") ?: 0.0,
            couponCodeList = data.arr("couponCodeList").mapNotNull { it.str2() },
            lines = lines,
        )
    }

    /**
     * 创建订单。返回瑞幸给的 `data` 原样：订单号和支付凭据的字段名没在我这儿写死，
     * 因为还没真跑过一发——界面按已知键取，取不出来的整段显示，不吞信息。
     */
    suspend fun createOrder(
        token: String,
        deptId: Long,
        items: List<Map<String, Any?>>,
        longitude: Double,
        latitude: Double,
        couponCodeList: List<String> = emptyList(),
        remark: String = "",
    ): JsonObject {
        val args = mutableMapOf<String, Any?>(
            "deptId" to deptId,
            "productList" to items,
            "longitude" to longitude,
            "latitude" to latitude,
        )
        if (couponCodeList.isNotEmpty()) args["couponCodeList"] = couponCodeList
        if (remark.isNotBlank()) args["remark"] = remark
        val env = callTool(token, "createOrder", args)
        return env.obj("data") ?: JsonObject()
    }

    /** 取消订单：下错了拿这一发收回去，同样是写操作 */
    suspend fun cancelOrder(token: String, orderId: String): String {
        val env = callTool(token, "cancelOrder", mapOf("orderId" to orderId))
        return env.get("msg")?.asString ?: "已取消"
    }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): List<JsonElement> {
        val el = get(key) ?: return emptyList()
        if (!el.isJsonArray) return emptyList()
        val a = el.asJsonArray
        return List(a.size()) { a.get(it) }
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }

    private fun JsonObject.num(key: String): Double? =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asDouble }.getOrNull() }

    private fun JsonElement.str2(): String? =
        takeIf { !isJsonNull }?.let { runCatching { it.asString }.getOrNull() }
}
