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
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.feature.card.data.CampusCardRepository
import com.linxin.feature.card.data.CardBalance
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

/**
 * 校园卡只读页：余额 + 卡状态 + 本月流水。
 * 余额和流水两条一起发、一起显示，不一路加载一路蹦。
 * 充值 / 挂失 / 改密 / 解绑都是动钱动凭证的写操作，没有做进来。
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

    fun clearWriteOutcome() {
        _writeOutcome.value = null
    }

    fun lostCard(password: String) = runWrite { repository.lostCard(password) }

    fun modifyPassword(oldPassword: String, newPassword: String) =
        runWrite { repository.modifyPassword(oldPassword, newPassword) }

    fun unbind(password: String) = runWrite { repository.unbind(password) }

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

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_card), onBack = onBack) },
    ) { padding ->
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
