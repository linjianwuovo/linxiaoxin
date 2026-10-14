package com.linxin.feature.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxEmpty
import com.linxin.core.designsystem.component.LxIconButton
import com.linxin.core.designsystem.component.LxLoading
import com.linxin.core.designsystem.component.LxSecondaryButton
import com.linxin.core.designsystem.component.LxTextButton
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxRose
import com.linxin.core.designsystem.theme.LxSage
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.map.LiveTrackMap
import com.linxin.feature.running.domain.RunningTrackerState
import com.linxin.feature.running.service.RunTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 运动中那一屏 —— 照官方 `/running`（原生壳那版 `SportMapActivity` 是同一套东西）：
 * 上面一张跟着长的地图，下面三块「X.XX 公里 / 速率 / 时长」，
 * 再下面「暂停」和「结束」两颗，锁屏时整页盖一层「长按可解锁」。
 *
 * 官方暂停 = `clearWatch` + `clearInterval`：暂停期间一个点都不收、时长也不走，
 * 这边 [RunningTrackerState.isPaused] 就是照它做的。
 */
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

    val trackerState = uiState.trackerState
    val title = when {
        !trackerState.isSessionActive -> stringResource(R.string.ra_title_ended)
        trackerState.isPaused -> stringResource(R.string.ra_title_paused)
        else -> stringResource(R.string.ra_title_running)
    }

    Scaffold(
        modifier = modifier,
        topBar = { LxTopBar(title = title, onBack = onBack) },
    ) { padding ->
        when {
            uiState.isUploadingRun -> LxLoading(modifier = Modifier.padding(padding))
            !trackerState.isSessionActive -> LxEmpty(
                message = stringResource(R.string.ui_055),
                modifier = Modifier.padding(padding),
            )
            else -> RunningActiveContent(
                trackerState = trackerState,
                isSimulating = uiState.armedRoute != null,
                modifier = Modifier.padding(padding),
                onTogglePause = viewModel::togglePaused,
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
    isSimulating: Boolean,
    onTogglePause: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var locked by remember { mutableStateOf(false) }
    LaunchedEffect(trackerState.startTimeMillis, trackerState.isSessionActive, trackerState.isPaused) {
        while (trackerState.isSessionActive) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val durationSeconds = elapsedOf(trackerState, nowMillis)
    val distanceKm = trackerState.totalDistanceMeters / 1000.0
    val speedKmh = if (durationSeconds > 0L) distanceKm / durationSeconds * 3600.0 else 0.0

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LxCard {
                    LiveTrackMap(
                        points = trackerState.points,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        markLast = true,
                        follow = true,
                    )
                }
            }
            item {
                LxCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "%.2f".format(distanceKm),
                            style = MiuixTheme.textStyles.headline1,
                            fontWeight = FontWeight.Bold,
                            color = LxInk,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.ra_km),
                            style = MiuixTheme.textStyles.body2,
                            color = LxInkMuted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        if (isSimulating) {
                            Text(
                                text = stringResource(R.string.ra_sim_on),
                                style = MiuixTheme.textStyles.footnote2,
                                color = LxTerra,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MiniStat(
                            label = stringResource(R.string.ra_speed),
                            value = "%.2f".format(speedKmh),
                            modifier = Modifier.weight(1f),
                        )
                        MiniStat(
                            label = stringResource(R.string.ra_duration),
                            value = clock(durationSeconds),
                            modifier = Modifier.weight(1f),
                        )
                        MiniStat(
                            label = stringResource(R.string.ra_points, trackerState.points.size),
                            value = trackerState.locationLabel,
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LxIconButton(onClick = { locked = !locked }) {
                        Icon(
                            imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = stringResource(R.string.ra_lock),
                            tint = LxInkMuted,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (trackerState.isPaused) {
                            LxButton(text = stringResource(R.string.ra_resume), onClick = onTogglePause)
                        } else {
                            LxSecondaryButton(text = stringResource(R.string.ra_pause), onClick = onTogglePause)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LxButton(text = stringResource(R.string.ra_finish), onClick = onFinish)
                    }
                }
            }
            item {
                if (distanceKm > 0.0 && distanceKm < 0.8) {
                    Text(
                        text = stringResource(R.string.ra_done_short),
                        style = MiuixTheme.textStyles.footnote1,
                        color = LxRose,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                LxTextButton(text = stringResource(R.string.action_cancel), onClick = onCancel)
            }
        }

        if (locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { locked = false })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.ra_lock_hint),
                    style = MiuixTheme.textStyles.title3,
                    color = LxInk,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = LxInkMuted,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
            color = LxInk,
            maxLines = 1,
        )
    }
}

/** 扣掉暂停那一段，跟官方 clearInterval 的效果一致 */
private fun elapsedOf(state: RunningTrackerState, nowMillis: Long): Long {
    if (state.startTimeMillis <= 0L) return 0L
    val pausedExtra = if (state.isPaused) {
        (nowMillis - (state.pausedAtMillis ?: nowMillis)).coerceAtLeast(0L)
    } else {
        0L
    }
    return ((nowMillis - state.startTimeMillis - state.pausedTotalMillis - pausedExtra) / 1000L)
        .coerceAtLeast(0L)
}

/** 官方那格时长是 mm:ss，超过一小时分钟继续涨 */
private fun clock(seconds: Long): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%02d:%02d".format(minutes, rest)
}
