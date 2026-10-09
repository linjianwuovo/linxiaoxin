package com.linxin.feature.card.data

import com.linxin.core.auth.TokenManager
import com.linxin.core.network.failureReason
import com.linxin.core.network.ApiConstants
import com.linxin.core.network.CardRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 校园卡数据层：换会话、查余额和流水，加上四个写操作（充值下单、挂失、改查询密码、解绑）。
 *
 * 会话是进程内一次性的（`sessionReady`）；一旦某个调用发现被踢回登录，就 `resetSession()` 再换一次。
 * 只有读操作走 `readWithRetry` 自动补一次 —— 动钱动凭证的那几个一律不自动重试，
 * 重复执行的风险比"点一次重试"的麻烦大。
 */
@Singleton
class CampusCardRepository @Inject constructor(
    private val api: CampusCardApi,
    private val tokenManager: TokenManager,
    @CardCookieJar private val cookieStore: CardCookieStore,
) {

    @Volatile
    private var sessionReady = false

    /**
     * 换会话必须串行：余额和流水两个请求是并行发的，两条链同时跑时后一次的 code
     * 会把前一次换来的会话顶掉，服务端就回"会话失效或前置异常"。
     */
    private val sessionMutex = Mutex()

    fun resetSession() {
        sessionReady = false
    }

    /**
     * 提前把会话换好。这条链要串行打四跳、跨 hub / open / ecardh5 三个域名，每跳都要重新
     * TLS 握手（真机 logcat 量到 260 / 740 / 2000 / 720 ms 这个量级），等用户点进校园卡
     * 再开始换就得干等好几秒。首页数据加载完后在后台跑一次，出错不抛（用户可能根本不进这页）。
     */
    suspend fun warmUp() {
        runCatching { ensureSession() }
    }

    private suspend fun ensureSession() {
        if (sessionReady) return
        sessionMutex.withLock {
            if (sessionReady) return@withLock
            ensureSessionLocked()
        }
    }

    private suspend fun ensureSessionLocked() {
        val creds = tokenManager.snapshot()
        val token = creds.accessToken.orEmpty()
        val xh = creds.userCode.orEmpty()
        val name = creds.userName.orEmpty()
        val userType = creds.userType ?: "1"
        if (token.isBlank() || xh.isBlank()) error("还没登录，换不出一卡通会话")

        val hub = ApiConstants.BASE_WANXIAO_HUB
        // 第 1 步：只为拿 hub 的 cookie，返回的那段 HTML 本身不用解析
        val bootstrap = api.openBootstrap(
            "$hub/bsacs/light.action?flag=${ApiConstants.ECARD_FLAG}" +
                "&ecardFunc=index&access_token=$token" +
                "&_userCode=$xh&code=$xh&userCode=$xh" +
                "&_userName=${URLEncoder.encode(name, "UTF-8")}" +
                "&_userType=$userType&appId=${ApiConstants.APP_ID}" +
                "&returnFromIscToAppFunc=ReturnDefault",
        )
        if (!bootstrap.isSuccessful) throw Exception("一卡通引导页打不开（${bootstrap.code()}）")
        bootstrap.body()?.close()

        // 第 2 步：userData 就是抓包里那串 JSON，键的顺序也照它 H5 里写好的样子来
        val userData = JSONObject()
            .put("access_token", token)
            .put("returnFromIscToAppFunc", "ReturnDefault")
            .put("ecardFunc", "index")
            .put("flag", ApiConstants.ECARD_FLAG)
            .put("code", xh)
            .put("_userName", name)
            .put("_userType", userType)
            .put("appId", ApiConstants.APP_ID)
            .put("_userCode", xh)
            .put("userCode", xh)
            .toString()
        val redirect = api.redirect("$hub/bsacs/redirect.action", userData)
        if (redirect.error == true) {
            // 服务端两个键都可能给原因，优先用它原话，别只丢一句"跳转失败"
            val reason = failureReason(redirect.message, redirect.message_, fallback = "一卡通账号跳转失败")
            throw Exception(reason ?: "一卡通账号跳转失败")
        }
        val authorizeUrl = redirect.url ?: throw Exception("一卡通没返回跳转地址")

        // 第 3 步：跟着 302 走完，ecardh5 的会话 cookie 就在这一步种下
        val authorize = api.followAuthorize(authorizeUrl)
        if (!authorize.isSuccessful) throw Exception("一卡通授权跳转失败（${authorize.code()}）")
        authorize.body()?.close()
        sessionReady = true
    }

    /**
     * 读操作失败时自动重换一次会话再打一遍。
     *
     * 起因：真机上出现过"第一次进页面报错、点重试就好"，而 13:35 抓到过服务端原文
     * `javax.net.ssl.SSLException: Connection reset`（hub 自己连学校 SSO 时被重置）。
     * 这种瞬时失败不该甩给用户去点重试。写操作（挂失/改密/解绑）故意不走这里——
     * 自动重试可能重复执行，那几次必须人手动点。
     */
    private suspend fun <T> readWithRetry(block: suspend () -> Result<T>): Result<T> {
        val first = block()
        if (first.isSuccess) return first
        sessionReady = false
        rsaPublicKey = null
        userInfoRow = null
        return block()
    }

    suspend fun balance(): Result<CardBalance> = readWithRetry { balanceOnce() }

    private suspend fun balanceOnce(): Result<CardBalance> {
        return try {
            ensureSession()
            val resp = api.baseInfo()
            if (resp.result_ != true) {
                sessionReady = false
                return Result.failure(Exception(failureReason(resp.message_, resp.message, fallback = "校园卡没返回余额，服务端也没给原因")))
            }
            val row = resp.data ?: return Result.failure(Exception("校园卡余额数据为空"))
            Result.success(
                CardBalance(
                    mainFare = row.main_fare ?: 0.0,
                    subsidyFare = row.subsidy_fare ?: 0.0,
                    fundFare = row.fund_fare ?: 0.0,
                    totalText = row.my_fare.orEmpty().trim(),
                    status = row.status ?: 0,
                    validUntil = row.nouseDate.orEmpty().trim(),
                ),
            )
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "校园卡加载失败", e))
        }
    }

    /**
     * 本月流水。第一次抓到的是空账本，2026-10-09 在电脑上把整条链跑通后拿到了非空返回，
     * 行字段（`accdscrp`/`amount`/`businessopdt`/`term_name`/`orderno`/`type`…）就是那次的内容。
     */
    suspend fun monthTrades(beginDate: String, endDate: String): Result<List<CardTrade>> =
        readWithRetry { monthTradesOnce(beginDate, endDate) }

    private suspend fun monthTradesOnce(beginDate: String, endDate: String): Result<List<CardTrade>> {
        return try {
            ensureSession()
            val resp = api.trades(
                beginIndex = 0,
                pageSize = 20,
                type = "-1",
                beginDate = beginDate,
                endDate = endDate,
            )
            if (resp.result_ != true) {
                sessionReady = false
                return Result.failure(Exception(failureReason(resp.message_, resp.message, fallback = "交易明细没取到，服务端也没给原因")))
            }
            Result.success(
                resp.data?.data.orEmpty().map { row ->
                    CardTrade(
                        time = row.businessopdt.orEmpty().trim(),
                        title = (row.accdscrp ?: row.description).orEmpty().trim(),
                        place = row.term_name.orEmpty().trim(),
                        amount = row.amount ?: 0.0,
                    )
                },
            )
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "交易明细加载失败", e))
        }
    }

    // ─── 写操作：挂失 / 改密 / 解绑 ───
    // 加密方式读它 H5 的 axios 拦截器确认：jsencrypt 的 setPublicKey/encrypt，
    // 即 RSA/ECB/PKCS1Padding + base64；公钥来自 gotowhere=userInfo 的 data.data.rsaPublicKey。
    // 拦截器只加密 password（改密是 oldpwd/newpwd），不会自动加验证码之类的字段。

    private var rsaPublicKey: String? = null

    /** `gotowhere=userInfo` 整份响应缓存一份，充值页要用的限额/预设和写操作要用的公钥都来自它 */
    private var userInfoRow: CardUserInfoRow? = null

    /**
     * 收银台是 app 内 WebView 打开的，WebView 的 CookieManager 和 okhttp 那份存储不互通，
     * 所以把当前能发给这个地址的会话 cookie 交出去，由界面种进 WebView；
     * 不种的话服务端认为没登录，收银台直接白屏或跳回登录。
     */
    fun payCookiesFor(url: String): List<Pair<String, String>> = cookieStore.forUrl(url)

    private suspend fun userInfoOnce(): CardUserInfoRow {
        userInfoRow?.let { return it }
        ensureSession()
        val resp = api.userInfo()
        val row = resp.data
        if (resp.result_ != true || row == null) {
            throw Exception(failureReason(resp.message_, resp.message, fallback = "拿不到一卡通的卡配置"))
        }
        userInfoRow = row
        return row
    }

    private suspend fun publicKey(): String {
        rsaPublicKey?.let { return it }
        val key = userInfoOnce().rsaPublicKey
        if (key.isNullOrBlank()) throw Exception("拿不到一卡通加密公钥")
        rsaPublicKey = key
        return key
    }

    private fun encrypt(plain: String, pem: String): String {
        val body = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val spec = java.security.spec.X509EncodedKeySpec(
            android.util.Base64.decode(body, android.util.Base64.DEFAULT),
        )
        val key = java.security.KeyFactory.getInstance("RSA").generatePublic(spec)
        val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        return android.util.Base64.encodeToString(cipher.doFinal(plain.toByteArray()), android.util.Base64.NO_WRAP)
    }

    /** 挂失。挂失后这张卡就不能消费了，界面那边必须二次确认。 */
    suspend fun lostCard(password: String): Result<String> = write {
        api.lostCard(encrypt(password, publicKey()))
    }

    /** 改查询密码。 */
    suspend fun modifyPassword(oldPassword: String, newPassword: String): Result<String> = write {
        val pem = publicKey()
        api.modifyPassword(encrypt(oldPassword, pem), encrypt(newPassword, pem))
    }

    /** 解绑。成功判据是第二级 `data.result_code == 0`。 */
    suspend fun unbind(password: String): Result<String> = write {
        api.unbind(encrypt(password, publicKey()))
    }

    private suspend fun write(call: suspend () -> CardWriteResponse): Result<String> {
        return try {
            ensureSession()
            val resp = call()
            if (resp.code_ != 0 && resp.result_ != true) {
                return Result.failure(Exception(failureReason(resp.message_, resp.message, fallback = "一卡通操作失败，服务端没给原因")))
            }
            val data = resp.data
            if (data?.result_code != null && data.result_code != 0) {
                return Result.failure(Exception(failureReason(data.message, resp.message_, resp.message, fallback = "一卡通操作失败，服务端没给原因")))
            }
            Result.success(data?.reBindUrl.orEmpty())
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "一卡通操作失败", e))
        }
    }

    // ─── 充值 ───
    // 协议出处：它 H5 的 rc-pay / chunk 17、40、57 和 index 里的请求层（2026-10-09 逐行读过）。
    // 抄下来的几条关键事实：
    // ① 请求层是 `qs.stringify`，body 只有 `gotowhere` + 调用点自己给的那几个键，不注入任何全局身份字段；
    //    RSA 只用在 `XYK_UNBIND_ENCRYPT` / `XYK_MODIFY_PASSWORD_ENCRYPT` / `XYK_LOST_CARD_ENCRYPT` /
    //    `VCARD_ACTIVATE_ENCRYPT` 这几个上 —— 充值全程明文；
    // ② `pay` 的 body 是 `{opfare, gateway_id, gateway_type, return_url}`，
    //    `opfare` 就是它 `parseFloat` 出来的**元**，整份 JS 里没有 *100 / toFixed / 除 100；
    //    `return_url` 在非微信环境是 `isWeixin() && openid` 求值出的布尔 false，原样发成 "false"；
    //    微信里没 openid 时这个键被 qs 丢掉，我们永远在普通浏览器环境，所以固定发 "false"；
    // ③ 成功判据是 `code_==0 || result_`（它请求层就这么判），业务数据在 `data.data`：
    //    收银台地址 `data.data.request_content`、单号 `data.data.jourorderno`（全小写，不是 orderNo）；
    // ④ `payStatus` 的 body 键是 `orderNo`，读 `data.data.payflag`，只有 "2" 算到账，
    //    其余（含缺省）它自己兜成 "3" 并显示失败页；它只在结果页调一次，不轮询；
    // ⑤ 渠道在 `data.data.gateways`（数组），字段是 gateway_id / gateway_type / gateway_name /
    //    gateway_icon / gateway_info；`recharge_wallet`（选补贴钱包那档）是路由参数带过来的，
    //    没有就不发这个键，我们只充主钱包，所以不发；
    // ⑥ `ecardh5type==2` 时它不调 `request_content`，而是把这同一条 body 里的
    //    `orderInfo/callAccountid/projectPaywayList/extend` 交给 `epaySdk.callPay`。那份 SDK 读完了：
    //    它没有用任何原生桥，就是把这几样 encodeURIComponent 之后拼成
    //    `wapnew.17wanxiao.com/WapCashDesk/e-pay/payways.html?orderInfo=…&callAccountid=…` 再 location.href，
    //    顺带把同样几个键种成 `.17wanxiao.com` 的 cookie —— 所以我们在 app 内 WebView 里打开同一个地址就是等价行为，
    //    见 `wapCashierUrl()`。
    // 银行圈存那条（`XYK_QC`）故意不做：它要把消费密码明文放进 body。

    /** 下单成功后的东西：收银台地址、查结果用的单号、收银台域名要种的 cookie */
    data class CardPayOrder(
        val payUrl: String,
        val orderNo: String,
        val cashierCookies: List<Pair<String, String>> = emptyList(),
    )

    /** 充值页一次要用的全部信息：渠道 + 服务端给的预设和限额 */
    data class CardRechargeOptions(
        val channels: List<CardChannel>,
        val presets: List<Int>,
        val maxAmount: Int?,
        val minAmount: Int?,
        val minEnforced: Boolean,
        /** 服务端要求下单前短信验证（这条路径本应用没接，只用来把话说明白） */
        val needsSmsVerify: Boolean = false,
    )

    /**
     * 渠道 + 限额。两条都是读操作，走 `readWithRetry`；
     * `userInfo` 的公钥那份缓存也顺手填上了，之后挂失/改密不用再打一次。
     */
    suspend fun rechargeOptions(): Result<CardRechargeOptions> = readWithRetry {
        try {
            val row = userInfoOnce()
            val channels = channelList(row)
            Result.success(
                CardRechargeOptions(
                    channels = channels,
                    presets = presetsFrom(row.moneyData),
                    // 它自己那句是 `!isNaN(Number(quota)) && Number(quota) > 0 ? quota : "500"`
                    maxAmount = row.quota.asAmount()?.takeIf { it > 0 } ?: DEFAULT_MAX,
                    minAmount = row.limitLowMoney.asAmount(),
                    minEnforced = row.limitLowMoneySwitch.truthy(),
                    // 这所学校开了的话，它 H5 要先走短信验证页才让下单；那条没接，直接说明
                    needsSmsVerify = row.ecardPayVerifyMobile.truthy(),
                ),
            )
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "充值信息加载失败", e))
        }
    }

    /**
     * 渠道列表。`showPayWay=false` 时它 H5 根本不取 `gateway`，而是把 `defaultPayWayCfg`
     * 那段 JSON 直接当渠道用（chunk 17 的默认渠道分支），这里一样处理；
     * 另外 `gateways` 空了但 `defaultPayWayCfg` 在，也按它那条路走，不至于显示"没有可用支付方式"。
     */
    private suspend fun channelList(row: CardUserInfoRow): List<CardChannel> {
        val fromDefault = defaultChannel(row.defaultPayWayCfg)
        val showPayWay = row.showPayWay
        if (!showPayWay.isNullOrBlank() && !showPayWay.truthy()) {
            return listOfNotNull(fromDefault)
        }
        val gateways = channelListOnce()
        return gateways.ifEmpty { listOfNotNull(fromDefault) }
    }

    /** `defaultPayWayCfg` 形如 `{"gateway_id":"…","gateway_type":"…"}`，解析不出来就当没有 */
    private fun defaultChannel(cfg: String?): CardChannel? {
        val obj = runCatching { JSONObject(cfg.orEmpty()) }.getOrNull() ?: return null
        val id = obj.optString("gateway_id").trim()
        if (id.isBlank()) return null
        return CardChannel(
            gateway_id = id,
            gateway_type = obj.optString("gateway_type").trim(),
            gateway_name = obj.optString("gateway_name").trim().ifBlank { "默认支付方式" },
            gateway_info = null,
        )
    }

    /**
     * `moneyData` 在它的 `userInfo` 响应里是**一段 JSON 字符串**（`JSON.parse(e.moneyData)`，
     * 解析失败就回落到 10/20/30/50/100/200/300/500 这八个），数组项只用 `.money`；
     * `special`（"其他金额"那种按钮）这里不需要，界面本来就给了自由输入框。
     */
    private fun presetsFrom(moneyData: String?): List<Int> {
        val parsed = runCatching {
            val arr = JSONArray(moneyData.orEmpty())
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.optString("money")?.trim()?.toDoubleOrNull()?.let { whole ->
                    if (whole > 0) whole else null
                }
            }
        }.getOrNull().orEmpty()
        if (parsed.isEmpty()) return DEFAULT_PRESETS
        return parsed.map { if (it == it.toLong().toDouble()) it.toLong().toInt() else it.toInt() }.distinct()
    }

    private suspend fun channelListOnce(): List<CardChannel> {
        ensureSession()
        val resp = api.payChannels()
        if (!cardOk(resp.code_, resp.result_, resp.success)) {
            throw Exception(failureReason(resp.message_, resp.message, fallback = "支付方式没取到，服务端也没给原因"))
        }
        return resp.data?.gateways.orEmpty().filter { !it.gateway_id.isNullOrBlank() }
    }

    /**
     * 充值下单。**这是动钱的写操作，故意不套 readWithRetry** ——
     * 失败了自动重来有生成第二笔订单的风险，要不要再试一次由人决定。
     */
    suspend fun createOrder(amountYuan: Int, channel: CardChannel): Result<CardPayOrder> {
        return try {
            ensureSession()
            val resp = api.recharge(
                opfare = amountYuan.toString(),
                gatewayId = channel.gateway_id.orEmpty(),
                gatewayType = channel.gateway_type.orEmpty(),
                returnUrl = "false",
            )
            if (!cardOk(resp.code_, resp.result_, resp.success)) {
                return Result.failure(Exception(failureReason(resp.message_, resp.message, fallback = "下单失败，服务端没给原因")))
            }
            val data = resp.data
            val direct = data?.request_content.orEmpty().trim()
            val sdk = wapCashierUrl(data)
            val url = direct.ifBlank { sdk?.first.orEmpty() }
            if (url.isBlank()) {
                return Result.failure(
                    Exception(
                        failureReason(resp.message_, resp.message, fallback = "服务端既没给收银台地址，也没给下单凭据（orderInfo）"),
                    ),
                )
            }
            Result.success(
                CardPayOrder(
                    payUrl = url,
                    orderNo = orderNoOf(data),
                    cashierCookies = sdk?.second.orEmpty(),
                ),
            )
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "下单失败", e))
        }
    }

    /**
     * `ecardh5type==2` 那条路。它 H5 调的 `epaySdk.callPay({orderInfo, callAccountid, projectPaywayList, extend})`
     * 看着像原生支付，其实那份 SDK（wapnew.17wanxiao.com 上的 `cap-epay-sdk-min.js`）里没有任何桥调用，
     * 只有两件事：把 orderInfo 那几个键写成 `.17wanxiao.com` 的 cookie，然后
     * `location.href = https://wapnew.17wanxiao.com/WapCashDesk/e-pay/payways.html?orderInfo=…&callAccountid=…`。
     * 那就照它拼：地址给 WebView，cookie 一起带过去（收银台那几个页面两个来源都读）。
     * 返回 Pair(地址, 要种的 cookie)。
     */
    private fun wapCashierUrl(data: CardPayData?): Pair<String, List<Pair<String, String>>>? {
        val d = data ?: return null
        val orderInfo = d.orderInfo.asJsonText() ?: return null
        val account = d.callAccountid.asJsonText().orEmpty()
        val list = d.projectPaywayList.asJsonText()
        val sb = StringBuilder(ApiConstants.BASE_WAP_CASHIER)
            .append("/payways.html?orderInfo=").append(queryEncode(orderInfo))
            .append("&callAccountid=").append(queryEncode(account))
        if (!list.isNullOrBlank()) sb.append("&projectPaywayList=").append(queryEncode(list))
        val cookies = buildList {
            // SDK 那边是 encodeURIComponent 之后写进 document.cookie 的，这里编同样的形
            add("orderInfo" to queryEncode(orderInfo))
            add("callAccountid" to queryEncode(account))
            if (!list.isNullOrBlank()) add("projectPaywayList" to queryEncode(list))
        }
        return sb.toString() to cookies
    }

    /** `JsonElement` 可能是字符串（里面又是一段 JSON），也可能直接是对象 —— 两种都要能拿出 JSON 文本 */
    private fun com.google.gson.JsonElement?.asJsonText(): String? {
        val e = this ?: return null
        if (e.isJsonNull) return null
        if (e.isJsonPrimitive && e.asJsonPrimitive.isString) return e.asString
        return e.toString()
    }

    /** 单号优先级照它 H5：`jourorderno` → orderInfo 里的 `journo` → 之后从跳转地址上的 partnerjourno 补 */
    private fun orderNoOf(data: CardPayData?): String {
        data?.jourorderno.orEmpty().trim().let { if (it.isNotBlank()) return it }
        val orderInfo = data?.orderInfo.asJsonText().orEmpty()
        return Regex("\"(?:journo|payOrderNo|orderno)\":\\s*\"?([0-9A-Za-z]{6,})\"?")
            .find(orderInfo)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

    /** 照 encodeURIComponent 的意思来：URLEncoder 把空格编成 +，地址上得是 %20 */
    private fun queryEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /**
     * 查支付结果。只读，可以重试；返回的是服务端那个 payflag 原文，
     * 界面按 "2"=到账、"3"/其他=没到账来说话，不替服务端猜意思。
     */
    suspend fun payResult(orderNo: String): Result<String> = readWithRetry {
        try {
            ensureSession()
            val resp = api.payStatus(orderNo)
            if (!cardOk(resp.code_, resp.result_, resp.success)) {
                throw Exception(failureReason(resp.message_, resp.message, fallback = "支付结果没查到，服务端也没给原因"))
            }
            Result.success(resp.data?.payflag.orEmpty().trim())
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "支付结果查询失败", e))
        }
    }

    private fun String?.asAmount(): Int? = this?.trim()?.toDoubleOrNull()?.let {
        if (it == it.toLong().toDouble()) it.toLong().toInt() else it.toInt()
    }

    /** 那些开关键都按 String 收（类型变了对面照样解析得出来），判真值就统一走这里 */
    private fun String?.truthy(): Boolean {
        val v = this?.trim()?.lowercase().orEmpty()
        return v == "true" || v == "1"
    }

    /** 它请求层的成功判据是 `code_==0 || success || result_`，三个键都可能缺席 */
    private fun cardOk(code_: Int?, result_: Boolean?, success: Boolean?): Boolean =
        code_ == 0 || result_ == true || success == true

    companion object {
        /** 服务端没给预设金额时用它，和它 H5 自己那份兜底一致（10…500 八个） */
        private val DEFAULT_PRESETS = listOf(10, 20, 30, 50, 100, 200, 300, 500)

        /** `quota` 缺失或不是正数时，它 H5 自己就用 "500" */
        private const val DEFAULT_MAX = 500
    }
}

/** 一卡通这条链跨三个子域，cookie 必须自己攒着，所以给它一个独立的 client。 */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CardCookieJar

/**
 * 扁平存 + `Cookie.matches(url)` 判域。
 *
 * 不能按 host 分桶：一卡通在 hub 上种的会话 cookie 是 `Domain=.17wanxiao.com` 这种跨子域的，
 * 分桶存进 hub 桶后就再也发不给 ecardh5，服务端直接回"出现异常,请联系运维人员"。
 * 只有 `matches()` 才懂 domain / path / secure 那套规则。
 *
 * 另外开了 `forUrl()`：充值收银台是 app 内 WebView 打开的，WebView 有自己的 CookieManager，
 * 和这个 okhttp 存储不互通，所以要把会话 cookie 手工种过去，否则收银台会认为没登录。
 */
class CardCookieStore : CookieJar {

    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        synchronized(store) {
            store.removeAll { old ->
                old.expiresAt < now ||
                    cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path }
            }
            store += cookies
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return synchronized(store) {
            store.removeAll { it.expiresAt < now }
            store.filter { it.matches(url) }
        }
    }

    /**
     * 能发给这个地址的 cookie，成对给出（WebView 的 CookieManager 是一次种一条）。
     *
     * 故意不再造 `HttpUrl`：okhttp 4 的 `HttpUrl.parse/get` 在这个版本上是废弃位、
     * `toHttpUrlOrNull` 扩展又要 4.10+/5.x 才有，版本一变就编不过。
     * 这里只需要「host 是不是 cookie 域本身或它的子域」，字符串比较就够，
     * secure/path 的取舍交给对面（收银台本来就是 https 的 17wanxiao 域）。
     */
    fun forUrl(url: String): List<Pair<String, String>> {
        val host = url.substringAfter("://", "").substringBefore("/", "").substringBefore(":").lowercase()
        if (host.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        return synchronized(store) {
            store.removeAll { it.expiresAt < now }
            store.filter { cookie ->
                cookie.expiresAt > now && hostMatches(host, cookie.domain.lowercase())
            }.map { it.name to it.value }
        }
    }

    private fun hostMatches(host: String, domain: String): Boolean {
        val bare = domain.removePrefix(".")
        if (bare.isEmpty()) return false
        return host == bare || host.endsWith(".$bare")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CampusCardModule {

    @Provides
    @Singleton
    @CardCookieJar
    fun provideCardCookieJar(): CardCookieStore = CardCookieStore()

    @Provides
    @Singleton
    @CardRetrofit
    fun provideCardRetrofit(client: OkHttpClient, @CardCookieJar jar: CardCookieStore): Retrofit {
        // TEMP-取证用：只把 bootcallback 的响应原文抄一条 logcat（标签 LxCard）。
        // 故意不用 HttpLoggingInterceptor 的 BODY 级 —— 那会把 redirect.action 里带 access_token 的
        // 请求体一起打出来；这里只看业务响应，抓完充值这轮就撤。
        val trace = object : okhttp3.Interceptor {
            override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.url.encodedPath.endsWith("/bootcallback")) {
                    val body = runCatching { response.peekBody(200_000L).string() }.getOrDefault("")
                    android.util.Log.i("LxCard", "← ${body.take(20_000)}")
                }
                return response
            }
        }
        return Retrofit.Builder()
            .baseUrl(ApiConstants.BASE_ECARD_H5 + "/")
            .client(client.newBuilder().cookieJar(jar).addInterceptor(trace).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideCampusCardApi(@CardRetrofit retrofit: Retrofit): CampusCardApi =
        retrofit.create(CampusCardApi::class.java)
}
