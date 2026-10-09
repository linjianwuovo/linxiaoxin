package com.linxin.feature.card.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import com.linxin.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxDialogConfirmTone
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxTextField
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 校园卡充值。
 *
 * 走的是它 H5 自己的第三方渠道那条链（`gateway` 取渠道 → `pay` 下单 → app 内 WebView 打开收银台 →
 * 回来用 `payStatus` 查一次），字段和拼写全部照它调用点抄，见 `CampusCardRepository` 的充值段。
 *
 * 两条硬规矩：
 * - **银行圈存那条没做**。它 H5 的 `XYK_QC` 是把消费密码明文放进 body 的，这种请求我不发；
 * - 下单是动钱的写操作，**不自动重试**，失败就把服务端原话贴出来，要不要再来一次由人决定。
 *   第一笔订单也一定是你亲手点出来的，我这轮只保证链路和字段是对的。
 */
@Composable
fun CardRechargeSection(viewModel: CardViewModel) {
    val state by viewModel.recharge.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val busy = state.isOrdering

    LxCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.card_recharge),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            if (!state.loaded) {
                LxOutlinedButton(
                    text = if (state.isLoading) {
                        stringResource(R.string.card_recharge_loading)
                    } else {
                        stringResource(R.string.card_recharge)
                    },
                    enabled = !state.isLoading,
                    onClick = viewModel::loadRechargeOptions,
                )
            } else {
                state.error?.let {
                    Text(text = it, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
                }
                if (state.options != null) {
                    val options = state.options!!
                    if (options.presets.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.card_recharge_presets),
                            style = MiuixTheme.textStyles.footnote1,
                            color = LxInkMuted,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(options.presets, key = { it }) { amount ->
                                LxFilterChip(
                                    label = amount.toString(),
                                    selected = state.amount == amount,
                                    onClick = { viewModel.setAmount(amount) },
                                )
                            }
                        }
                    }
                    LxTextField(
                        value = state.amountText,
                        onValueChange = viewModel::setAmountText,
                        label = stringResource(R.string.card_recharge_amount),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = listOfNotNull(
                            stringResource(R.string.card_recharge_amount_hint),
                            options.maxAmount?.let { stringResource(R.string.card_recharge_max, it.toString()) },
                            if (options.minEnforced) {
                                options.minAmount?.let { stringResource(R.string.card_recharge_min, it.toString()) }
                            } else {
                                null
                            },
                        ).joinToString(" · "),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LxInkMuted,
                    )

                    if (options.channels.isEmpty()) {
                        Text(
                            text = stringResource(R.string.card_recharge_channel_none),
                            style = MiuixTheme.textStyles.footnote2,
                            color = LxInkMuted,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.card_recharge_channel),
                            style = MiuixTheme.textStyles.footnote1,
                            color = LxInkMuted,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(options.channels, key = { it.gateway_id.orEmpty() }) { channel ->
                                LxFilterChip(
                                    label = channel.gateway_name.orEmpty()
                                        .ifBlank { channel.gateway_id.orEmpty() },
                                    selected = viewModel.isChannelSelected(channel),
                                    onClick = { viewModel.pickChannel(channel) },
                                )
                            }
                        }
                    }
                }
                state.message?.let {
                    Text(text = it, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
                }
                // 金额、渠道、上限、下限任一样不对，就把话摊在按钮上面，不让它点下去
                val problem = when (viewModel.orderProblem()) {
                    CardOrderProblem.NO_AMOUNT -> stringResource(R.string.card_recharge_amount_hint)
                    CardOrderProblem.NO_CHANNEL -> stringResource(R.string.card_recharge_channel_none)
                    CardOrderProblem.SMS_VERIFY -> stringResource(R.string.card_recharge_sms_verify)
                    CardOrderProblem.TOO_BIG -> {
                        val max = state.options?.maxAmount?.toString().orEmpty()
                        stringResource(R.string.card_recharge_max, max)
                    }
                    CardOrderProblem.TOO_SMALL -> {
                        val min = state.options?.minAmount?.toString().orEmpty()
                        stringResource(R.string.card_recharge_min, min)
                    }
                    null -> null
                }
                problem?.let {
                    Text(text = it, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
                }
                LxButton(
                    text = stringResource(R.string.card_recharge_go),
                    enabled = !busy && problem == null && !state.isOrdering,
                    onClick = { confirm = true },
                )
            }
        }
    }

    if (confirm) {
        val amount = state.amount
        LxDialog(
            title = stringResource(R.string.card_recharge_go),
            message = stringResource(R.string.card_recharge_warn) +
                "\n" + stringResource(R.string.card_recharge_amount) + "：$amount",
            confirmText = stringResource(R.string.action_confirm),
            confirmTone = LxDialogConfirmTone.Destructive,
            onConfirm = {
                confirm = false
                viewModel.createOrder()
            },
            onDismissRequest = { confirm = false },
        )
    }
}

/**
 * 收银台。它 H5 就一句 `location.href = request_content`，这里等价地用 WebView 打开同一个地址。
 *
 * WebView 的 cookie 和 okhttp 那份是两套，所以下单成功时顺手把当时能发给这个域的会话 cookie
 * 一起带过来种进去 —— 不种的话第三方会认为没登录。只放行 http/https，
 * 其它 scheme（微信、支付宝那种）交给系统。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CardPayWebView(
    target: CardViewModel.PayTarget,
    resultText: String?,
    onClose: () -> Unit,
    onCheckResult: () -> Unit,
    onNavigated: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.card_recharge_cashier),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                )
                if (target.orderNo.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.card_recharge_order_no, target.orderNo),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LxInkMuted,
                    )
                }
            }
            LxOutlinedButton(text = stringResource(R.string.card_recharge_check), onClick = onCheckResult)
            LxOutlinedButton(text = stringResource(R.string.card_close), onClick = onClose)
        }
        Text(
            text = stringResource(R.string.card_recharge_cashier_hint),
            style = MiuixTheme.textStyles.footnote2,
            color = LxInkMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        resultText?.let {
            Text(
                text = it,
                style = MiuixTheme.textStyles.footnote2,
                color = LxInk,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                seedCookies(target.url, target.cookies)
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme ?: return false
                            if (scheme == "http" || scheme == "https") {
                                onNavigated(request.url.toString())
                                return false
                            }
                            // 第三方支付常常要把人甩给微信/支付宝 app，这类 scheme 交给系统处理
                            return runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                                true
                            }.getOrDefault(false)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            // 付完它会跳回带 ?partnerjourno= 的地址，那上面有单号
                            onNavigated(url)
                        }
                    }
                    loadUrl(target.url)
                }
            },
        )
    }
}

/** 把一卡通会话 cookie 种进 WebView（收银台和 okhttp 用的是两套 cookie 存储） */
private fun seedCookies(url: String, cookies: List<Pair<String, String>>) {
    if (cookies.isEmpty()) return
    val manager = CookieManager.getInstance()
    manager.setAcceptCookie(true)
    cookies.forEach { (name, value) -> manager.setCookie(url, "$name=$value") }
    manager.flush()
}
