package com.linxin.feature.credit.ui
import androidx.compose.ui.res.stringResource
import com.linxin.R
import com.linxin.core.designsystem.theme.LxShapes
import com.linxin.core.designsystem.theme.RLg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDetailRow
import com.linxin.core.designsystem.component.LxError
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxCategoryColors
import com.linxin.core.designsystem.theme.LxSuccess
import com.linxin.feature.credit.domain.CreditModule
import com.linxin.feature.credit.domain.CreditOverview
import com.linxin.feature.credit.domain.CreditRecord
import com.linxin.feature.credit.domain.CreditRecordDetail

@Composable
fun CreditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_credit), onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.overview == null -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            else -> CreditContent(
                uiState = uiState,
                onRecordClick = viewModel::onRecordClick,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (uiState.showDetailSheet) {
        OverlayBottomSheet(
            show = true,
            onDismissRequest = viewModel::dismissDetail,
            backgroundColor = MiuixTheme.colorScheme.surface,
            cornerRadius = RLg,
        ) {
            DetailSheetContent(
                detail = uiState.selectedDetail,
                isLoading = uiState.isDetailLoading,
                error = uiState.detailError,
            )
        }
    }
}

@Composable
private fun CreditContent(
    uiState: CreditUiState,
    onRecordClick: (CreditRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.overview?.let { overview ->
            item(key = "overview") {
                OverviewCard(overview)
            }
        }

        item(key = "records_title") {
            Text(
                text = stringResource(R.string.credit_records),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        if (uiState.records.isEmpty() && uiState.error != null) {
            item(key = "records_error") {
                LxCard {
                    Text(
                        text = uiState.error!!,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        } else if (uiState.records.isEmpty()) {
            item(key = "empty") {
                LxCard {
                    Text(
                        text = stringResource(R.string.credit_no_records),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        } else {
            items(uiState.records, key = { it.id }) { record ->
                RecordCard(record = record, onClick = { onRecordClick(record) })
            }
        }
    }
}

@Composable
private fun OverviewCard(overview: CreditOverview) {
    val maxCredit = overview.modules.maxOfOrNull { it.credit }?.coerceAtLeast(1.0) ?: 1.0

    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.credit_overview),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.credit_credit_n, overview.totalCredit),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val statusColor = if (overview.pass) LxSuccess else MiuixTheme.colorScheme.error
                    Text(
                        text = if (overview.pass) stringResource(R.string.credit_pass) else stringResource(R.string.credit_fail),
                        style = MiuixTheme.textStyles.footnote2,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            overview.modules.forEachIndexed { index, module ->
                CreditBarRow(
                    label = module.name,
                    credit = module.credit,
                    maxCredit = maxCredit,
                    color = LxCategoryColors[index % LxCategoryColors.size],
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun CreditBarRow(
    label: String,
    credit: Double,
    maxCredit: Double,
    color: androidx.compose.ui.graphics.Color,
) {
    val fraction = (credit / maxCredit).toFloat().coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "%.1f".format(credit),
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun RecordCard(record: CreditRecord, onClick: () -> Unit) {
    LxCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.credit_score_status, record.score, record.statusName),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun DetailSheetContent(
    detail: CreditRecordDetail?,
    isLoading: Boolean,
    error: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        if (isLoading) {
            LxLoading()
        } else if (error != null) {
            Text(
                text = error,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        } else if (detail != null) {
            Text(
                text = detail.name,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LxDetailRow(label = stringResource(R.string.credit_award_level), value = detail.awardLevelName)
            LxDetailRow(label = stringResource(R.string.credit_award_grade), value = detail.awardPrizeName)
            LxDetailRow(label = stringResource(R.string.credit_highest_level), value = detail.highestLevelName)
            LxDetailRow(label = stringResource(R.string.credit_earned), value = "%.1f".format(detail.prizeScore))
            LxDetailRow(label = stringResource(R.string.credit_earned_at), value = detail.getTime)
            LxDetailRow(label = stringResource(R.string.credit_module), value = detail.qualityModuleName)
            LxDetailRow(label = stringResource(R.string.credit_subcat), value = detail.qualityCategoryName)
            LxDetailRow(label = stringResource(R.string.credit_audit), value = detail.statusName, showDivider = false)
        }
    }
}
