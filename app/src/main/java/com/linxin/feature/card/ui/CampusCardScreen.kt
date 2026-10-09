package com.linxin.feature.card.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.stringResource
import com.linxin.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    val monthTrades: Int? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * 校园卡只读页：余额 + 卡状态 + 本月流水条数。
 *
 * 流水**只显示条数**——抓到的那次账本是空的（`{"size":0,"data":[]}`），
 * 每一行有哪些字段没有出处，所以不猜；要显示明细得先拿到一次非空返回再补。
 */
@HiltViewModel
class CardViewModel @Inject constructor(
    private val repository: CampusCardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardUiState())
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

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
                repository.monthTradeCount(
                    month.atDay(1).format(fmt),
                    month.atEndOfMonth().format(fmt),
                )
            }
            val balanceResult = balance.await()
            val tradesResult = trades.await()
            _uiState.update {
                it.copy(
                    balance = balanceResult.getOrNull(),
                    monthTrades = tradesResult.getOrNull(),
                    isLoading = false,
                    error = balanceResult.exceptionOrNull()?.message
                        ?: tradesResult.exceptionOrNull()?.message
                        ?: "校园卡加载失败",
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            when {
                uiState.isLoading -> LxLoading(modifier = Modifier.fillMaxSize())
                uiState.balance == null && uiState.error != null -> LxError(
                    message = uiState.error!!,
                    onRetry = viewModel::load,
                )
                else -> uiState.balance?.let { balance ->
                    Column {
                        LxCard {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(R.string.card_total),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = LxInkMuted,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "¥ ${balance.totalText.ifBlank { "%.2f".format(balance.mainFare + balance.subsidyFare + balance.fundFare) }}",
                                    style = MiuixTheme.textStyles.headline1,
                                    fontWeight = FontWeight.Bold,
                                    color = LxInk,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                CardMoneyRow(stringResource(R.string.card_main), balance.mainFare)
                                CardMoneyRow(stringResource(R.string.card_subsidy), balance.subsidyFare)
                                CardMoneyRow(stringResource(R.string.card_fund), balance.fundFare)
                                Spacer(modifier = Modifier.height(12.dp))
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
                                uiState.monthTrades?.let { count ->
                                    CardMoneyRow(
                                        label = stringResource(R.string.card_month_trades),
                                        value = stringResource(R.string.card_month_trades_value, count),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.card_readonly_hint),
                            style = MiuixTheme.textStyles.footnote2,
                            color = LxInkMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardMoneyRow(label: String, value: Double) {
    CardMoneyRow(label, "%.2f".format(value))
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

