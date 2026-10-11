package com.linxin.core.luckin

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 瑞幸自家网关（capi）的只读客户端。
 *
 * 官方 AI 开放平台那八个 MCP 工具里没有"按门店列整张菜单"，只有吃自然语言的搜索，
 * 所以点进门店只能给一个空搜索框——那是接口的天花板，不是我漏做。
 * 瑞幸 H5 用的同一个网关把菜单和商详做成了**匿名可读**：不要登录、不要 token、
 * JS 请求层里也没有 sign/appSign 之类的签名，参数就一个 `queryParamsStr`（URL 编码的 JSON）。
 *
 * 2026-10-12 匿名实测：`product/menu` 回 15 个分类 155 个商品（只有 id 和价格，没有名字），
 * `product/detail` 回名字/图/价格（0.2~0.3s 一发），所以名字按分类懒加载补。
 * 购物车、订单预览、下单这些仍然要登录——那部分继续走官方 MCP，不在这条路上。
 *
 * 只读。这里不发任何写请求。
 */
object LuckinWeb {

    private const val BASE = "https://m.lkcoffee.com/capi/"

    class WebApiError(override val message: String) : Exception(message)

    data class MenuItem(
        val productId: Long,
        val initialPrice: Double,
        /** 菜单里的直降价，-1 表示没有直降 */
        val discountPrice: Double,
        val recommendTop: Boolean,
    )

    data class MenuKind(
        val kindId: String,
        val kindName: String,
        val kindDesc: String,
        val items: List<MenuItem>,
    )

    data class ItemBrief(
        val productId: Long,
        val name: String,
        val pictureUrl: String?,
        val initialPrice: Double,
        val discountPrice: Double,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private fun JsonArray?.orEmpty(): List<JsonElement> =
        if (this == null) emptyList() else List(size()) { get(it) }

    /** 网关里很多字段是 JSON 的 null，Gson 的 asDouble/asBoolean 撞上 JsonNull 会抛，一律走这两个 */
    private fun JsonObject.num(key: String): Double? =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asDouble }.getOrNull()

    private fun JsonObject.flag(key: String): Boolean? =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()

    private fun JsonElement?.asStringOrNull(): String? {
        if (this == null || isJsonNull) return null
        runCatching { asJsonObject.get("url")?.takeIf { !it.isJsonNull }?.asString }.getOrNull()?.let { return it }
        return runCatching { asString }.getOrNull()
    }

    private suspend fun get(path: String, params: Map<String, Any?>): JsonObject =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(gson.toJson(params), "UTF-8")
            val req = Request.Builder()
                .url(BASE + path + "?queryParamsStr=" + q)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw WebApiError("瑞幸网关 HTTP ${resp.code}")
                val o = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
                    ?: throw WebApiError("瑞幸网关返回不是 JSON")
                val status = o.get("status")?.asString ?: "?"
                if (status != "SUCCESS") {
                    val msg = o.get("msg")?.asString ?: status
                    throw WebApiError(if (status == "NOT_LOGIN") "要登录：$msg" else msg)
                }
                o
            }
        }

    /** 整张分类菜单：人气Top / 当季新品 / 生椰家族 …… 只有 id 和价格 */
    /** 门店此刻开不开：菜单那一发里就带着 shopInfo.isWork，顺手记下来，不再多请求 */
    private val openCache = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()

    fun isOpen(deptId: Long): Boolean? = openCache[deptId]

    suspend fun menu(deptId: Long): List<MenuKind> {
        val content = get("resource/core/v3/product/menu", mapOf("deptId" to deptId))
            .getAsJsonObject("content")
            ?: throw WebApiError("菜单是空的")
        content.getAsJsonObject("shopInfo")?.num("isWork")?.let { openCache[deptId] = it == 1.0 }
        return content.getAsJsonArray("commodityList").orEmpty().map { el ->
            val k = el.asJsonObject
            val items = k.getAsJsonArray("twoProductList").orEmpty()
                .flatMap { two -> two.asJsonObject.getAsJsonArray("productList").orEmpty() }
                .map { p ->
                    val po = p.asJsonObject
                    val pr = po.getAsJsonObject("productPrice")
                    MenuItem(
                        productId = po.get("productId").asStringOrNull()?.toLongOrNull() ?: 0L,
                        initialPrice = pr?.num("initialPrice") ?: 0.0,
                        discountPrice = pr?.num("discountPrice") ?: -1.0,
                        recommendTop = po.flag("recommendTop") ?: false,
                    )
                }
                .filter { it.productId != 0L }
                .distinctBy { it.productId }
            MenuKind(
                kindId = k.get("kindId").asStringOrNull() ?: "",
                kindName = k.get("kindName").asStringOrNull() ?: "",
                kindDesc = k.get("kindDesc").asStringOrNull() ?: "",
                items = items,
            )
        }.filter { it.kindName.isNotBlank() && it.items.isNotEmpty() }
    }

    /** 单个商品的名字/图/价：菜单里没给名字，按分类展开时补这几发 */
    suspend fun itemBrief(deptId: Long, productId: Long): ItemBrief {
        val c = get(
            "resource/core/v1/product/detail",
            mapOf("deptId" to deptId, "productId" to productId.toString()),
        ).getAsJsonObject("content") ?: throw WebApiError("商品详情是空的")
        val pics = c.getAsJsonArray("pictureUrlList").orEmpty().firstNotNullOfOrNull { it.asStringOrNull() }
        return ItemBrief(
            productId = productId,
            name = c.get("name").asStringOrNull() ?: "",
            pictureUrl = pics ?: c.get("defaultPicUrl").asStringOrNull(),
            initialPrice = c.num("initialPrice") ?: 0.0,
            discountPrice = c.num("discountPrice") ?: -1.0,
        )
    }

    /** 一颗规格胶囊：属于哪个组、叫什么 */
    data class SpecGroup(
        val groupId: String,
        val groupName: String,
        val values: List<String>,
    )

    /**
     * 规格表：组顺序 + 每组的取值 + 「值组合 → skuCode」全表。
     * 选规格不调任何接口，纯查这张表；表里没有的组合就是不可选。
     */
    data class Spec(
        val productId: Long,
        val name: String,
        val pictureUrl: String?,
        val groups: List<SpecGroup>,
        val skus: Map<Map<String, String>, String>,
        val defaultChoice: Map<String, String>,
        val initialPrice: Double,
        val discountPrice: Double,
    ) {
        fun skuFor(choice: Map<String, String>): String? = skus[choice]

        /** 把某一组换成 value 之后，还剩不等于零个可选组合 */
        fun canPick(groupId: String, value: String, choice: Map<String, String>): Boolean {
            val probe = choice.toMutableMap()
            probe[groupId] = value
            return skus.keys.any { sku -> probe.all { (k, v) -> sku[k] == v } }
        }
    }

    /** 商详：名字、图、规格组和整张 sku 表都在这发里，匿名可读 */
    suspend fun spec(deptId: Long, productId: Long): Spec {
        val c = get(
            "resource/core/v1/product/detail",
            mapOf("deptId" to deptId, "productId" to productId.toString()),
        ).getAsJsonObject("content") ?: throw WebApiError("商详是空的")

        val groupNames = c.getAsJsonObject("saleAttrGroup") ?: JsonObject()
        val groupValues = c.getAsJsonObject("saleAttrGroupValues") ?: JsonObject()
        val order = c.getAsJsonArray("saleAttrGroupSeq").orEmpty().mapNotNull { it.asStringOrNull() }
        val groups = (if (order.isEmpty()) groupNames.keySet().toList() else order).map { gid ->
            SpecGroup(
                groupId = gid,
                groupName = groupNames.get(gid).asStringOrNull() ?: gid,
                values = groupValues.getAsJsonArray(gid).orEmpty().mapNotNull { it.asStringOrNull() },
            )
        }.filter { it.values.isNotEmpty() }

        val skus = HashMap<Map<String, String>, String>()
        c.getAsJsonArray("skuList").orEmpty().forEach { el ->
            val o = el.asJsonObject
            val code = o.get("skuCode").asStringOrNull() ?: return@forEach
            val vals = o.getAsJsonObject("skuSaleAttrValue") ?: return@forEach
            val map = vals.keySet().associateWith { k ->
                vals.getAsJsonArray(k).orEmpty().firstNotNullOfOrNull { it.asStringOrNull() } ?: ""
            }.filterValues { it.isNotBlank() }
            if (map.size == groups.size) skus[map] = code
        }

        val defaultCode = c.get("spuDefaultSkuCode").asStringOrNull() ?: c.get("skuCode").asStringOrNull()
        val defaultChoice = skus.entries.firstOrNull { it.value == defaultCode }?.key
            ?: groups.associate { it.groupId to it.values.first() }

        val pics = c.getAsJsonArray("pictureUrlList").orEmpty().firstNotNullOfOrNull { it.asStringOrNull() }
        return Spec(
            productId = productId,
            name = c.get("name").asStringOrNull() ?: "",
            pictureUrl = pics ?: c.get("defaultPicUrl").asStringOrNull(),
            groups = groups,
            skus = skus,
            defaultChoice = defaultChoice,
            initialPrice = c.num("initialPrice") ?: 0.0,
            discountPrice = c.num("discountPrice") ?: -1.0,
        )
    }

    /** 菜单一次一发、按门店缓存十分钟；名字缓存跟着进程走 */
    private const val TTL_MS = 10 * 60_000L
    private val menuCache = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long, List<MenuKind>>>()
    private val briefCache = java.util.concurrent.ConcurrentHashMap<Long, ItemBrief>()

    private fun key(deptId: Long, productId: Long) = deptId * 100_000_000L + productId

    suspend fun menuCached(deptId: Long): List<MenuKind> {
        menuCache[deptId]?.let { (at, v) -> if (System.currentTimeMillis() - at < TTL_MS) return v }
        val fresh = menu(deptId)
        if (fresh.isNotEmpty()) menuCache[deptId] = System.currentTimeMillis() to fresh
        return fresh
    }

    suspend fun itemBriefCached(deptId: Long, productId: Long): ItemBrief {
        val k = key(deptId, productId)
        briefCache[k]?.let { return it }
        val fresh = itemBrief(deptId, productId)
        if (fresh.name.isNotBlank()) briefCache[k] = fresh
        return fresh
    }
}
