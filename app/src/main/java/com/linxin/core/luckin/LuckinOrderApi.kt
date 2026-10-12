package com.linxin.core.luckin

import com.google.gson.JsonObject

/**
 * 瑞幸 capi 的下单和支付层，全部走 [LuckinAuth] 那个带登录态的客户端。
 *
 * 为什么要这一层：官方 MCP 建的单在瑞幸自己的订单列表里查不到，是两个渠道，
 * 所以「下单用 MCP、付款用 capi」拼不起来。要付款停在安大信里，整条链路都得换成 capi。
 *
 * 路径和字段抄自瑞幸私有目录里的明文 JS bundle（`miniapp_core` 的接口注册表和
 * buildOrderPreviewReqParams），只取字段名和流程，代码没搬。
 *
 * 还没落实的一点：create 的请求体在 JS 里是 `data: s(t)`——把 preCreate 回来的那一坨
 * 加工后原样发回去。静态看不到商品条目那几个字段的最终拼法，得拿一次真 preCreate 响应
 * 才能定死。所以这里 create 的入参设计成「preCreate 回来的对象」，不做任何猜测性改名。
 */
object LuckinOrderApi {

    private const val PREVIEW = "resource/core/v2/order/preview"
    private const val PRE_CREATE = "resource/core/v1/order/preCreate"
    private const val CREATE = "resource/core/v1/order/create"
    private const val PAY_LIST = "resource/core/v2/pay/list"
    private const val TO_PAY = "resource/core/v2/pay/topay"
    private const val QUICK_PAY = "resource/core/v2/pay/quickPay"
    private const val DETAIL = "resource/core/v1/order/detail"
    private const val CANCEL = "resource/m/order/cancel"

    /** 点单条目：安大信这边只管把门店、经纬度和这几样递下去 */
    data class Draft(
        val deptId: Long,
        val longitude: Double,
        val latitude: Double,
        val cityId: Int = 0,
        /** 1 自取 / 2 外送，界面上现在只有自取 */
        val eatway: Int = 1,
    )

    /**
     * 预览：算价用，回 aboutTime / canOrder / befCouponDiscount / 各种折扣。
     *
     * 2026-10-12 实测定死的两点：商品列表的字段名是 `productList`（`comboList` 不认），
     * 而且**不能带 `delivery`**——带任何值都回「delivery值不正确」，整条不传才过。
     */
    suspend fun preview(d: Draft, lines: List<Map<String, Any?>>): JsonObject =
        LuckinAuth.postApi(PREVIEW, base(d).plus(mapOf(
            "productList" to lines,
            "couponCodeList" to emptyList<String>(),
            "activityPlanKeysList" to emptyList<String>(),
            "useDiscount" to 1,
            "submitOf500" to 0,
            "isFirst" to 0,
            "payCardChecked" to "0",
            "channel" to "GCJ-02",
        )))

    /**
     * 预下单。回的对象就是要交给 [create] 的那一坨，这里不改字段名。
     * 实测这一层对 `eatway` 的取值另有要求（传 1 回「eatway值不正确」），还没定死，
     * 所以下面把 eatway 单独留成参数，不跟着 Draft 走。
     */
    suspend fun preCreate(d: Draft, lines: List<Map<String, Any?>>, eatway: String = ""): JsonObject =
        LuckinAuth.postApi(PRE_CREATE, mapOf(
            "deptId" to d.deptId,
            "cityId" to d.cityId,
            "longitude" to d.longitude,
            "latitude" to d.latitude,
            "channel" to "GCJ-02",
            "eatway" to eatway,
            "productList" to lines,
        ))

    /** 正式下单：把 preCreate 回来的对象原样发回去，嵌套对象按 JSON 字符串递 */
    suspend fun create(payload: JsonObject): JsonObject {
        val flat = LinkedHashMap<String, Any?>()
        payload.entrySet().forEach { (k, v) ->
            flat[k] = if (v.isJsonPrimitive) v.asString else v.toString()
        }
        return LuckinAuth.postApi(CREATE, flat)
    }

    /**
     * 把 capi 预览的返回摊成界面上那张卡。字段按 10-12 实测响应取的，
     * 认不出来的一律给 0，不猜。
     */
    suspend fun previewCard(d: Draft, lines: List<Map<String, Any?>>): LuckinMcp.Preview {
        val j = preview(d, lines)
        val c = j.obj("content") ?: throw LuckinAuth.ApiError("瑞幸没回订单内容")
        val items = c.arr("productList").ifEmpty { c.arr("comboList") }
        return LuckinMcp.Preview(
            shopName = c.str("deptName") ?: c.str("shopName") ?: "",
            aboutTime = parseClock(c.str("aboutTime")),
            totalInitialPrice = c.num("initialPrice") ?: c.num("befCouponDiscount") ?: 0.0,
            privilegeMoney = c.num("privilegeMoney") ?: 0.0,
            discountPrice = c.num("discountPrice") ?: c.num("totalPrice") ?: 0.0,
            couponCodeList = emptyList(),
            lines = items.map { o ->
                val it = o.asJsonObject
                LuckinMcp.PreviewLine(
                    name = it.str("name") ?: "",
                    amount = it.num("amount")?.toInt() ?: 1,
                    addition = it.str("skuSaleAttrValue") ?: "",
                    estimatePrice = it.num("initUnitPrice") ?: it.num("estimatePrice") ?: 0.0,
                )
            },
        )
    }

    /** "17:10" 这种当天时刻换成毫秒时间戳；给不了就 0 */
    private fun parseClock(text: String?): Long {
        if (text.isNullOrBlank() || !text.contains(':')) return 0L
        return runCatching {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, text.substringBefore(':').toInt())
            cal.set(java.util.Calendar.MINUTE, text.substringAfter(':').toInt())
            cal.set(java.util.Calendar.SECOND, 0)
            cal.timeInMillis
        }.getOrDefault(0L)
    }

    /** 这一单能用哪些支付方式 */
    suspend fun payList(orderId: String): JsonObject =
        LuckinAuth.postApi(PAY_LIST, mapOf(
            "orderId" to orderId,
            "payType" to "",
            "source" to 3,
        ))

    /** 去支付：拿收银台参数。payType 用 payList 回的那个值 */
    suspend fun topay(orderId: String, payType: String, busType: Int, d: Draft): JsonObject =
        LuckinAuth.postApi(TO_PAY, mapOf(
            "orderId" to orderId,
            "payType" to payType,
            "busType" to busType,
            "longitude" to d.longitude.toString(),
            "latitude" to d.latitude.toString(),
            "did" to "",
            "blackbox" to "",
            "ip" to "",
            "successUrl" to "",
            "thirdPayDiscountTag" to "",
        ))

    /** 余额秒付。余额为 0 时服务端会拒，这里不猜它的错误码 */
    suspend fun quickPay(orderId: String, payTypeId: String, d: Draft): JsonObject =
        LuckinAuth.postApi(QUICK_PAY, mapOf(
            "orderId" to orderId,
            "payTypeId" to payTypeId,
            "longitude" to d.longitude.toString(),
            "latitude" to d.latitude.toString(),
            "blackbox" to "",
            "did" to "",
        ))

    suspend fun detail(orderId: String): JsonObject =
        LuckinAuth.getApi(DETAIL, mapOf("orderId" to orderId))

    suspend fun cancel(orderId: String): JsonObject =
        LuckinAuth.postApi(CANCEL, mapOf("orderId" to orderId))

    private fun base(d: Draft): Map<String, Any?> = mapOf(
        "deptId" to d.deptId,
        "longitude" to d.longitude,
        "latitude" to d.latitude,
        "cityId" to d.cityId,
        "eatway" to d.eatway,
    )

    // Gson 的 null 字段一律走这几个，别直接 asDouble/asString
    private fun com.google.gson.JsonObject.obj(k: String) =
        runCatching { get(k)?.takeIf { it.isJsonObject }?.asJsonObject }.getOrNull()

    private fun com.google.gson.JsonObject.str(k: String) =
        runCatching { get(k)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()

    private fun com.google.gson.JsonObject.num(k: String) =
        runCatching { get(k)?.takeIf { !it.isJsonNull }?.asDouble }.getOrNull()

    private fun com.google.gson.JsonObject.arr(k: String): List<com.google.gson.JsonElement> =
        runCatching { get(k)?.takeIf { it.isJsonArray }?.asJsonArray }
            .getOrNull()?.let { a -> List(a.size()) { a.get(it) } } ?: emptyList()
}
