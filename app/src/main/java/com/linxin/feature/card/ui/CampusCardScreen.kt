package com.linxin.feature.card.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import com.linxin.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxTextButton
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.feature.card.data.CampusCardRepository
import com.linxin.feature.card.data.CardBalance
import com.linxin.feature.card.data.CardChannel
import com.linxin.feature.card.data.CardTrade
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class CardUiState(
    val balance: CardBalance? = null,
    val trades: List<CardTrade> = emptyList(),
    val tradesLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** 写操作的一次结果：ok + 服务端原话（失败时）或重新绑定地址（解绑成功时） */
data class CardWriteOutcome(
    val ok: Boolean,
    val text: String,
)

/** 充值页状态。选项是点开「充值」才去取的，不跟着首页那批一起加载，免得拖慢页面 */
data class CardRechargeState(
    val isLoading: Boolean = false,
    val loaded: Boolean = false,
    val options: CampusCardRepository.CardRechargeOptions? = null,
    val amountText: String = "",
    val amount: Int? = null,
    val selectedChannel: CardChannel? = null,
    val isOrdering: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/** 下单前的问题，交给界面翻成对应语言的话 */
enum class CardOrderProblem { NO_AMOUNT, NO_CHANNEL, TOO_BIG, TOO_SMALL, SMS_VERIFY }

/**
 * 校园卡页：余额 + 卡状态 + 本月流水 + 充值 + 三个卡操作。
 * 余额和流水两条一起发、一起显示，不一路加载一路蹦。
 * 充值 / 挂失 / 改密 / 解绑都会真的生效，每一步都要人确认，写操作一律不自动重试。
 */
@HiltViewModel
class CardViewModel @Inject constructor(
    private val repository: CampusCardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardUiState())
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

    private val _writeBusy = MutableStateFlow(false)
    val writeBusy: StateFlow<Boolean> = _writeBusy.asStateFlow()

    private val _writeOutcome = MutableStateFlow<CardWriteOutcome?>(null)
    val writeOutcome: StateFlow<CardWriteOutcome?> = _writeOutcome.asStateFlow()

    private val _recharge = MutableStateFlow(CardRechargeState())
    val recharge: StateFlow<CardRechargeState> = _recharge.asStateFlow()

    private val _payTarget = MutableStateFlow<PayTarget?>(null)
    val payTarget: StateFlow<PayTarget?> = _payTarget.asStateFlow()

    private val _payResult = MutableStateFlow<String?>(null)
    val payResult: StateFlow<String?> = _payResult.asStateFlow()

    /** 收银台要用的三样：地址、单号、下单那一刻从 okhttp 那边取到的会话 cookie */
    data class PayTarget(
        val url: String,
        val orderNo: String,
        val cookies: List<Pair<String, String>>,
    )

    fun clearWriteOutcome() {
        _writeOutcome.value = null
    }

    fun lostCard(password: String) = runWrite { repository.lostCard(password) }

    fun modifyPassword(oldPassword: String, newPassword: String) =
        runWrite { repository.modifyPassword(oldPassword, newPassword) }

    fun unbind(password: String) = runWrite { repository.unbind(password) }

    fun loadRechargeOptions() {
        if (_recharge.value.isLoading) return
        viewModelScope.launch {
            _recharge.update { it.copy(isLoading = true, error = null) }
            repository.rechargeOptions().fold(
                onSuccess = { options ->
                    _recharge.update {
                        it.copy(
                            isLoading = false,
                            loaded = true,
                            options = options,
                            // 默认选中服务端给的第一个渠道，省一次点击
                            selectedChannel = it.selectedChannel ?: options.channels.firstOrNull(),
                            amount = it.amount ?: options.presets.firstOrNull(),
                            amountText = it.amountText.ifBlank {
                                (it.amount ?: options.presets.firstOrNull())?.toString().orEmpty()
                            },
                        )
                    }
                },
                onFailure = { e ->
                    _recharge.update { it.copy(isLoading = false, loaded = true, error = e.message) }
                },
            )
        }
    }

    fun setAmount(amount: Int) {
        _recharge.update { it.copy(amount = amount, amountText = amount.toString(), message = null) }
    }

    fun setAmountText(text: String) {
        val digits = text.filter { it.isDigit() }.take(4)
        _recharge.update { it.copy(amountText = digits, amount = digits.toIntOrNull(), message = null) }
    }

    fun pickChannel(channel: CardChannel) {
        _recharge.update { it.copy(selectedChannel = channel, message = null) }
    }

    fun isChannelSelected(channel: CardChannel): Boolean =
        _recharge.value.selectedChannel?.gateway_id == channel.gateway_id

    /** 能不能下单；界面拿这个码翻成中文/英文的提示 */
    fun orderProblem(): CardOrderProblem? {
        val st = _recharge.value
        // 服务端要求先短信验证的学校，它 H5 会拐进 /code 那一页；那条没接，就不能让人按下支付
        if (st.options?.needsSmsVerify == true) return CardOrderProblem.SMS_VERIFY
        val amount = st.amount ?: return CardOrderProblem.NO_AMOUNT
        if (amount <= 0) return CardOrderProblem.NO_AMOUNT
        if (st.selectedChannel == null) return CardOrderProblem.NO_CHANNEL
        st.options?.maxAmount?.let { if (amount > it) return CardOrderProblem.TOO_BIG }
        if (st.options?.minEnforced == true) {
            st.options?.minAmount?.let { if (amount < it) return CardOrderProblem.TOO_SMALL }
        }
        return null
    }

    fun createOrder() {
        val st = _recharge.value
        val amount = st.amount ?: return
        val channel = st.selectedChannel ?: return
        viewModelScope.launch {
            _recharge.update { it.copy(isOrdering = true, message = null, error = null) }
            repository.createOrder(amount, channel).fold(
                onSuccess = { order ->
                    _payResult.value = null
                    _payTarget.value = PayTarget(
                        url = order.payUrl,
                        orderNo = order.orderNo,
                        // 会话 cookie（ecardh5 那套）+ 收银台自己要的 orderInfo 那几个键
                        cookies = repository.payCookiesFor(order.payUrl) + order.cashierCookies,
                    )
                    _recharge.update { it.copy(isOrdering = false) }
                },
                onFailure = { e ->
                    // VM 里拿不到 stringResource，兜底话按这个 app 的惯例先给中文，服务端原话优先
                    _recharge.update {
                        it.copy(isOrdering = false, message = e.message ?: "下单失败，服务端没给原因")
                    }
                },
            )
        }
    }

    /** 付完回来查一次。`payflag=="2"` 是它 H5 里唯一的到账判据，别的值一律说「还没到账」 */
    fun checkPayResult() {
        val orderNo = _payTarget.value?.orderNo.orEmpty()
        if (orderNo.isBlank()) {
            _payResult.value = "no_order"
            return
        }
        viewModelScope.launch {
            repository.payResult(orderNo).fold(
                onSuccess = { flag -> _payResult.value = flag.ifBlank { "empty" } },
                onFailure = { e -> _payResult.value = "error:${e.message}" },
            )
        }
    }

    /** 查完结果弹了窗，关掉就别再把上一条结果重复弹出来 */
    fun clearPayResult() {
        _payResult.value = null
    }

    fun closePay() {
        _payTarget.value = null
        _payResult.value = null
        load()
    }

    /**
     * 收银台跳转时认一遍地址。它 H5 取单号的原话是
     * `jourorderno || partnerjourno || 从 query 里找 partnerjourno`，
     * 所以下单响应没给 `jourorderno` 时，付完跳回来的那个 URL 上带着的就是同一串单号，
     * 捡回来才能查 `payStatus`。
     */
    fun onCashierUrl(url: String?) {
        val target = _payTarget.value ?: return
        if (target.orderNo.isNotBlank()) return
        val found = url?.substringAfter("?", "")
            ?.split("&")
            ?.firstOrNull { it.substringBefore("=").equals("partnerjourno", ignoreCase = true) }
            ?.substringAfter("=", "")
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .orEmpty()
        if (found.isBlank()) return
        _payTarget.value = target.copy(orderNo = found)
    }

    private fun runWrite(block: suspend () -> Result<String>) {
        viewModelScope.launch {
            _writeBusy.value = true
            block().fold(
                onSuccess = { extra ->
                    _writeOutcome.value = CardWriteOutcome(ok = true, text = extra)
                    load()
                },
                onFailure = { e ->
                    _writeOutcome.value = CardWriteOutcome(ok = false, text = e.message ?: "操作失败")
                },
            )
            _writeBusy.value = false
        }
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val month = YearMonth.from(LocalDate.now())
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val balance = async { repository.balance() }
            val trades = async {
                repository.monthTrades(month.atDay(1).format(fmt), month.atEndOfMonth().format(fmt))
            }
            val balanceResult = balance.await()
            val tradesResult = trades.await()
            _uiState.update {
                it.copy(
                    balance = balanceResult.getOrNull(),
                    trades = tradesResult.getOrNull().orEmpty(),
                    tradesLoaded = tradesResult.isSuccess,
                    isLoading = false,
                    error = balanceResult.exceptionOrNull()?.message
                        ?: tradesResult.exceptionOrNull()?.message,
                )
            }
        }
    }
}

@Composable
fun CampusCardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val payTarget by viewModel.payTarget.collectAsStateWithLifecycle()
    val payResult by viewModel.payResult.collectAsStateWithLifecycle()

    val resultText = payResult?.let { flag ->
        when {
            flag == "2" -> stringResource(R.string.card_recharge_paid)
            flag == "no_order" || flag == "empty" -> stringResource(R.string.card_recharge_pending)
            flag.startsWith("error:") -> flag.removePrefix("error:")
            else -> stringResource(R.string.card_recharge_unpaid)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            if (payTarget == null) {
                LxTopBar(title = stringResource(R.string.title_card), onBack = onBack)
            } else {
                // 收银台的「查结果」放在顶栏右边，页面本体整块留给网页，不再挤一排按钮
                LxTopBar(
                    title = stringResource(R.string.card_recharge_cashier),
                    onBack = viewModel::closePay,
                    actions = {
                        LxTextButton(
                            text = stringResource(R.string.card_recharge_check),
                            onClick = viewModel::checkPayResult,
                        )
                    },
                )
            }
        },
    ) { padding ->
        val target = payTarget
        if (target != null) {
            CardPayWebView(
                target = target,
                onNavigated = viewModel::onCashierUrl,
                modifier = Modifier.padding(padding),
            )
            // 到账与否以前只写在页面下面一行小字，真机上被收银台页面盖住，点了像没反应（2026-10-09 18:30）。
            // 改成对话框：跟「确认下单」那个弹窗同一个组件，跑不掉。
            resultText?.let {
                LxDialog(
                    title = stringResource(R.string.card_recharge_result),
                    message = it,
                    confirmText = stringResource(R.string.action_confirm),
                    onConfirm = viewModel::clearPayResult,
                    onDismissRequest = viewModel::clearPayResult,
                )
            }
            return@Scaffold
        }
        when {
            uiState.isLoading -> LxLoading(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
            uiState.balance == null && uiState.error != null -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.balance?.let { balance ->
                    item { BalanceCard(balance) }
                }
                item {
                    Text(
                        text = stringResource(R.string.card_month_trades),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = LxInk,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                when {
                    !uiState.tradesLoaded -> item {
                        Text(
                            text = stringResource(R.string.card_trades_failed),
                            style = MiuixTheme.textStyles.body2,
                            color = LxInkMuted,
                        )
                    }
                    uiState.trades.isEmpty() -> item {
                        Text(
                            text = stringResource(R.string.card_trades_empty),
                            style = MiuixTheme.textStyles.body2,
                            color = LxInkMuted,
                        )
                    }
                    else -> items(uiState.trades) { trade ->
                        TradeRow(trade)
                    }
                }
                item {
                    CardRechargeSection(viewModel = viewModel)
                }
                item {
                    CardActionsSection(viewModel = viewModel)
                }
                item {
                    Text(
                        text = stringResource(R.string.card_readonly_hint),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LxInkMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: CardBalance) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.card_total),
                style = MiuixTheme.textStyles.footnote1,
                color = LxInkMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "¥ " + balance.totalText.ifBlank {
                    "%.2f".format(balance.mainFare + balance.subsidyFare + balance.fundFare)
                },
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Bold,
                color = LxInk,
            )
            Spacer(modifier = Modifier.height(16.dp))
            CardMoneyRow(stringResource(R.string.card_main), "%.2f".format(balance.mainFare))
            CardMoneyRow(stringResource(R.string.card_subsidy), "%.2f".format(balance.subsidyFare))
            CardMoneyRow(stringResource(R.string.card_fund), "%.2f".format(balance.fundFare))
            Spacer(modifier = Modifier.height(10.dp))
            CardMoneyRow(
                label = stringResource(R.string.card_status),
                value = if (balance.status == 1) {
                    stringResource(R.string.card_status_ok)
                } else {
                    stringResource(R.string.card_status_code, balance.status)
                },
            )
            if (balance.validUntil.isNotBlank()) {
                CardMoneyRow(
                    label = stringResource(R.string.card_valid_until),
                    value = balance.validUntil,
                )
            }
        }
    }
}

@Composable
private fun TradeRow(trade: CardTrade) {
    LxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trade.title.ifBlank { stringResource(R.string.card_trade_untitled) },
                    style = MiuixTheme.textStyles.body1,
                    color = LxInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val sub = listOf(trade.time, trade.place).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MiuixTheme.textStyles.footnote2,
                        color = LxInkMuted,
                    )
                }
            }
            Text(
                text = "%.2f".format(trade.amount),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
        }
    }
}

@Composable
private fun CardMoneyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = LxInkMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = LxInk,
        )
    }
}
