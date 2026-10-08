package com.linxin.feature.running.ui

import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.feature.running.domain.RunningTrackerState
import com.linxin.feature.running.service.RunTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun RunningActiveScreen(
    onBack: () -> Unit,
    onNavigateResult: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.shouldNavigateToResult) {
        if (uiState.shouldNavigateToResult) {
            viewModel.consumeResultNavigation()
            onNavigateResult()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_running), onBack = onBack) },
    ) { padding ->
        when {
            uiState.isUploadingRun -> LxLoading(modifier = Modifier.padding(padding))
            !uiState.trackerState.isSessionActive -> LxEmpty(
                message = stringResource(R.string.ui_055),
                modifier = Modifier.padding(padding),
            )
            else -> RunningActiveContent(
                trackerState = uiState.trackerState,
                modifier = Modifier.padding(padding),
                onFinish = {
                    scope.launch {
                        RunTrackingService.stop(context)
                        viewModel.finishRealRun()
                    }
                },
                onCancel = {
                    RunTrackingService.cancel(context)
                    viewModel.abandonRealRun()
                    onBack()
                },
            )
        }
    }
}

@Composable
private fun RunningActiveContent(
    trackerState: RunningTrackerState,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trackerState.startTimeMillis, trackerState.isSessionActive) {
        while (trackerState.isSessionActive) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val durationSeconds = ((nowMillis - trackerState.startTimeMillis) / 1000L).coerceAtLeast(0L)
    val distanceKm = trackerState.totalDistanceMeters / 1000.0
    val paceSeconds = if (distanceKm > 0.0) (durationSeconds / distanceKm).toLong() else 0L
    val speedKmh = if (durationSeconds > 0L) distanceKm / durationSeconds * 3600.0 else 0.0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            LxCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.ui_056),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = String.format("%.2f", distanceKm),
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "km",
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActiveMetricCard(
                    title = stringResource(R.string.ui_057),
                    value = durationSeconds.formatDuration(),
                    modifier = Modifier.weight(1f),
                )
                ActiveMetricCard(
                    title = stringResource(R.string.ui_058),
                    value = if (paceSeconds > 0L) paceSeconds.formatPace() else "--'--\"",
                    modifier = Modifier.weight(1f),
                )
                ActiveMetricCard(
                    title = stringResource(R.string.ui_059),
                    value = if (speedKmh > 0.0) String.format("%.1f", speedKmh) else "--",
                    unitText = if (speedKmh > 0.0) "km/h" else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            LxCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.ui_060),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = trackerState.locationLabel,
                        style = MiuixTheme.textStyles.body2,
                    )
                    trackerState.errorMessage?.takeIf { it.isNotBlank() }?.let { errorMessage ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ui_061, trackerState.points.size.toString()),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LxButton(text = stringResource(R.string.ui_062), onClick = onFinish)
                    LxOutlinedButton(text = stringResource(R.string.ui_063), onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun ActiveMetricCard(
    title: String,
    value: String,
    unitText: String? = null,
    modifier: Modifier = Modifier,
) {
    LxCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (!unitText.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unitText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private fun Long.formatDuration(): String {
    val hours = TimeUnit.SECONDS.toHours(this)
    val minutes = TimeUnit.SECONDS.toMinutes(this) % 60
    val seconds = this % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun Long.formatPace(): String {
    val minutes = this / 60
    val seconds = this % 60
    return String.format("%d'%02d\"", minutes, seconds)
}
