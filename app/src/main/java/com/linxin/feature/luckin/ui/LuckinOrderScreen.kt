package com.linxin.feature.luckin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.gson.JsonElement
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxChoiceChip
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.luckin.LuckinMcp
import com.linxin.core.luckin.LuckinWeb
import com.linxin.core.settings.LuckinPrefs
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 规格 + 下单页。
 *
 * 规格表来自瑞幸自家网关的商详（匿名可读，见 [LuckinWeb]）：组名、每组取值、整张
 * 「值组合 → skuCode」表一次拿全，所以选规格不调任何写接口，表里没有的组合直接灰掉。
 *
 * 下单是写操作，两道闸：先「预览订单」（previewOrder 只读，不建单）把实付算给他看，
 * 再点「确认下单」弹一次确认框。**只创建订单，付款必须他自己在瑞幸点，我们绝不代付。**
 * 下错了有「取消这笔订单」（cancelOrder）收回去。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuckinProductDetailScreen(
    deptId: Long,
    productId: Long,
    productName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { LuckinPrefs(context) }
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf("") }
    var point by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    var spec by remember { mutableStateOf<LuckinWeb.Spec?>(null) }
    var choice by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var amount by remember { mutableStateOf(1) }
    var preview by remember { mutableStateOf<LuckinMcp.Preview?>(null) }
    var previewing by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }

    var askCreate by remember { mutableStateOf(false) }
    var ordering by remember { mutableStateOf(false) }
    var orderError by remember { mutableStateOf<String?>(null) }
    var orderData by remember { mutableStateOf<com.google.gson.JsonObject?>(null) }
    var canceling by remember { mutableStateOf(false) }
    var cancelMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val s = prefs.current()
        token = s.token
        point = s.lng.toDoubleOrNull()?.let { l -> s.lat.toDoubleOrNull()?.let { l to it } }
    }

    LaunchedEffect(deptId, productId) {
        loading = true; error = null
        runCatching { LuckinWeb.spec(deptId, productId) }
            .onSuccess {
                spec = it
                choice = it.defaultChoice
            }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    val sku = spec?.skuFor(choice)
    val lines = sku?.let { listOf(LuckinMcp.lineItem(productId, it, amount)) }.orEmpty()

    // 改了规格或数量，先前那次预览就不作数了
    LaunchedEffect(choice, amount) { preview = null; previewError = null; orderError = null }

    Scaffold(topBar = {
        LxTopBar(
            title = productName.ifBlank { spec?.name.orEmpty() }.ifBlank {
                stringResource(R.string.luckin_detail_title)
            },
            onBack = onBack,
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
            }
            error?.let {
                Text(stringResource(R.string.luckin_error, it), fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.error)
                Spacer(Modifier.height(14.dp))
            }

            val s = spec
            if (s != null) {
                LxCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        s.pictureUrl?.let { url ->
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                                    .background(MiuixTheme.colorScheme.surfaceVariant),
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s.name.ifBlank { productName }, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            val deal = s.discountPrice.takeIf { it > 0.0 }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "¥${"%.1f".format(deal ?: s.initialPrice)}",
                                    fontSize = 17.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (deal != null && s.initialPrice > deal) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.luckin_detail_origin, "%.1f".format(s.initialPrice)),
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                s.groups.forEach { group ->
                    LxCard {
                        Column(Modifier.padding(16.dp)) {
                            Text(group.groupName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                group.values.forEach { value ->
                                    LxChoiceChip(
                                        text = value,
                                        selected = choice[group.groupId] == value,
                                        enabled = s.canPick(group.groupId, value, choice),
                                        onClick = { choice = choice + (group.groupId to value) },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                LxCard {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.luckin_amount), fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        LxPillButton("−", enabled = amount > 1) { if (amount > 1) amount-- }
                        Text(stringResource(R.string.luckin_cup_count, amount), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        LxPillButton("＋", enabled = amount < 10) { if (amount < 10) amount++ }
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (sku == null) {
                    Text(stringResource(R.string.luckin_sku_none), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.error)
                    Spacer(Modifier.height(14.dp))
                }

                if (token.isBlank()) {
                    Text(stringResource(R.string.luckin_need_login), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.height(24.dp))
                } else {
                    LxPillButton(
                        if (previewing) stringResource(R.string.luckin_previewing)
                        else stringResource(R.string.luckin_preview),
                        enabled = sku != null && !previewing,
                    ) {
                        scope.launch {
                            previewing = true; previewError = null; preview = null
                            runCatching { LuckinMcp.previewOrder(token, deptId, lines) }
                                .onSuccess { preview = it }
                                .onFailure { previewError = it.message ?: it.javaClass.simpleName }
                            previewing = false
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    previewError?.let {
                        Text(stringResource(R.string.luckin_error, it), fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                    }
                    preview?.let { p ->
                        LxCard {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.luckin_preview_title), fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                if (p.shopName.isNotBlank()) {
                                    Text(p.shopName, fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                                // 打烊时瑞幸照样回一个"几分钟后可取"，那是假时间，别拿它骗人
                                if (p.aboutTime > 0L && LuckinWeb.isOpen(deptId) != false) {
                                    val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.aboutTime))
                                    Text(stringResource(R.string.luckin_about_time, t), fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                                p.lines.forEach { l ->
                                    Spacer(Modifier.height(6.dp))
                                    Text("${l.amount} × ${l.name}" +
                                        (if (l.addition.isBlank()) "" else "  ${l.addition}"), fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    if (p.totalInitialPrice > 0) {
                                        Text(stringResource(R.string.luckin_initial_total, "%.1f".format(p.totalInitialPrice)),
                                            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    }
                                    if (p.privilegeMoney > 0) {
                                        Text(stringResource(R.string.luckin_saved, "%.1f".format(p.privilegeMoney)),
                                            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.luckin_pay_total, "%.1f".format(p.discountPrice)),
                                    fontSize = 16.sp, color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        if (orderData == null) {
                            LxPillButton(
                                if (ordering) stringResource(R.string.luckin_ordering)
                                else stringResource(R.string.luckin_create),
                                enabled = !ordering,
                            ) { askCreate = true }
                        }
                        orderError?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.luckin_order_error, it), fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.error)
                        }
                    }
                }

                orderData?.let { data ->
                    Spacer(Modifier.height(14.dp))
                    LxCard {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.luckin_order_created_title), fontSize = 15.sp,
                                fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            data.entrySet().forEach { (k, v) -> renderOrderField(k, v)?.let { line ->
                                Text(line, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                            } }
                            Text(stringResource(R.string.luckin_pay_yourself), fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            val payUrl = payUrlOf(data)
                            if (payUrl != null) {
                                Spacer(Modifier.height(12.dp))
                                LxPillButton(stringResource(R.string.luckin_go_pay)) {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(payUrl),
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }.onFailure { orderError = it.message ?: it.javaClass.simpleName }
                                }
                            }
                            val oid = orderIndexOf(data)
                            if (oid != null) {
                                Spacer(Modifier.height(12.dp))
                                LxPillButton(
                                    if (canceling) stringResource(R.string.luckin_canceling)
                                    else stringResource(R.string.luckin_cancel),
                                    enabled = !canceling,
                                ) {
                                    scope.launch {
                                        canceling = true; cancelMsg = null
                                        runCatching { LuckinMcp.cancelOrder(token, oid) }
                                            .onSuccess { cancelMsg = it }
                                            .onFailure { cancelMsg = it.message ?: it.javaClass.simpleName }
                                        canceling = false
                                    }
                                }
                            }
                            cancelMsg?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (askCreate) {
        val p = preview
        LxDialog(
            title = stringResource(R.string.luckin_confirm_title),
            message = stringResource(
                R.string.luckin_confirm_body,
                p?.let { "%.1f".format(it.discountPrice) } ?: "?",
            ),
            dismissText = stringResource(R.string.luckin_confirm_no),
            confirmText = stringResource(R.string.luckin_confirm_yes),
            onConfirm = {
                askCreate = false
                scope.launch {
                    ordering = true; orderError = null
                    val pt = point
                    if (pt == null) {
                        orderError = context.getString(R.string.luckin_no_point)
                    } else {
                        runCatching {
                            LuckinMcp.createOrder(
                                token = token,
                                deptId = deptId,
                                items = lines,
                                longitude = pt.first,
                                latitude = pt.second,
                                couponCodeList = p?.couponCodeList.orEmpty(),
                            )
                        }.onSuccess { orderData = it }
                            .onFailure { orderError = it.message ?: it.javaClass.simpleName }
                    }
                    ordering = false
                }
            },
            onDismissRequest = { askCreate = false },
        )
    }
}

/** 订单返回里能认的字段逐行列出来，认不出的整段留原文，不吞信息 */
private fun renderOrderField(key: String, value: JsonElement): String? {
    if (value.isJsonNull) return null
    val text = runCatching { value.asString }.getOrNull()
    return if (text != null) "$key: $text" else "$key: ${value.toString().take(160)}"
}

/** 瑞幸回的支付凭据：优先微信调起链接，其次二维码地址，两个都没有就不给按钮 */
private fun payUrlOf(data: com.google.gson.JsonObject): String? {
    for (k in listOf("payOrderUrl", "payOrderQrCodeUrl", "payUrl", "qrCodeUrl")) {
        data.get(k)?.takeIf { !it.isJsonNull }?.let {
            val v = runCatching { it.asString }.getOrNull()
            if (!v.isNullOrBlank()) return v
        }
    }
    return null
}

private fun orderIndexOf(data: com.google.gson.JsonObject): String? {
    for (k in listOf("orderId", "orderCode", "no", "orderNo")) {
        data.get(k)?.takeIf { !it.isJsonNull }?.let { return runCatching { it.asString }.getOrNull() }
    }
    return null
}
