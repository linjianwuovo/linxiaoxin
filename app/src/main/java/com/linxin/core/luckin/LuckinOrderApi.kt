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
    suspend fun preview(d: Draft, lines: List<JsonObject>): JsonObject =
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
    suspend fun preCreate(d: Draft, lines: List<JsonObject>, eatway: String = ""): JsonObject =
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
}
