package com.linxin.feature.running.ui

import androidx.compose.ui.res.stringResource
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDetailRow
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.feature.running.domain.RunningResult

@Composable
fun RunningResultScreen(
    onBack: () -> Unit,
    onBackToHome: () -> Unit,
    onBackToRunning: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = uiState.lastResult

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_running_result), onBack = onBack) },
    ) { padding ->
        if (result == null) {
            LxEmpty(
                message = stringResource(R.string.ui_064),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ResultStatusCard(result) }
            item { ResultMetricsCard(result) }
            item {
                LxButton(
                    text = stringResource(R.string.ui_065),
                    onClick = {
                        viewModel.clearResult()
                        onBackToRunning()
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                LxOutlinedButton(
                    text = stringResource(R.string.ui_066),
                    onClick = {
                        viewModel.clearResult()
                        onBackToHome()
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultStatusCard(result: RunningResult) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (result.success) "上传完成" else "上传失败",
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
                color = if (result.success) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.error
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result.message,
                style = MiuixTheme.textStyles.body2,
            )
            if (result.uploadId.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.ui_067, result.uploadId.toString()),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ResultMetricsCard(result: RunningResult) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.ui_068),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            LxDetailRow(label = stringResource(R.string.ui_069), value = result.startDate, showDivider = false)
            LxDetailRow(label = stringResource(R.string.ui_070), value = result.endDate, showDivider = false)
            LxDetailRow(label = "距离", value = String.format("%.2f km", result.distanceKm), showDivider = false)
            LxDetailRow(label = stringResource(R.string.ui_057), value = stringResource(R.string.ui_071, result.durationSeconds.toString()), showDivider = false)
            LxDetailRow(label = "速度", value = String.format("%.2f km/h", result.speedKmh), showDivider = false)
            LxDetailRow(label = stringResource(R.string.ui_072), value = stringResource(R.string.ui_073, result.pointCount.toString()), showDivider = false)
        }
    }
}
