package com.linxin.feature.running.ui

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxTextField
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.feature.running.service.RunTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun RouteTemplateRecordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteTemplateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSaveDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    // 权限回调和按钮 onClick 不是组合上下文，stringResource 必须先在外层取好
    val noPermMsg = stringResource(R.string.tpl_no_location_perm)
    val defaultRouteName = stringResource(R.string.tpl_default_name, uiState.templates.size + 1)

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                viewModel.clearError()
                if (viewModel.beginRecording()) {
                    RunTrackingService.start(context)
                }
            } else {
                viewModel.setError(noPermMsg)
            }
        },
    )


    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_route_template), onBack = onBack) },
    ) { padding ->
        val tracker = uiState.trackerState
        val isRecording = uiState.isRecording

        var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(tracker.startTimeMillis, tracker.isSessionActive) {
            while (tracker.isSessionActive) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
        val durationSeconds =
            if (tracker.isSessionActive) ((nowMillis - tracker.startTimeMillis) / 1000L).coerceAtLeast(0L)
            else 0L
        val distanceKm = tracker.totalDistanceMeters / 1000.0

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LxCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.tpl_recorded_distance),
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
                    MetricCell(
                        title = stringResource(R.string.tpl_duration),
                        value = durationSeconds.formatDurationTpl(),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCell(
                        title = stringResource(R.string.tpl_points_title),
                        value = "${tracker.points.size}",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCell(
                        title = stringResource(R.string.tpl_location),
                        value = tracker.locationLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                LxCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.tpl_notice),
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.tpl_save_rule),
                            style = MiuixTheme.textStyles.body2,
                        )
                        tracker.errorMessage?.takeIf { it.isNotBlank() }?.let { errorMessage ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                        uiState.errorMessage?.takeIf { it.isNotBlank() }?.let { errorMessage ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            !isRecording -> LxButton(
                                text = stringResource(R.string.tpl_start),
                                onClick = {
                                    val hasLocation = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                        || ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                    if (hasLocation) {
                                        viewModel.clearError()
                                        if (viewModel.beginRecording()) {
                                            RunTrackingService.start(context)
                                        }
                                    } else {
                                        locationPermLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            )
                                        )
                                    }
                                },
                                enabled = !uiState.isRealRunActive,
                            )
                            else -> {
                                LxButton(
                                    text = stringResource(R.string.tpl_finish_save),
                                    onClick = {
                                        RunTrackingService.stop(context)
                                        viewModel.stopCollecting()
                                        nameInput = defaultRouteName
                                        showSaveDialog = true
                                    },
                                )
                                LxOutlinedButton(
                                    text = stringResource(R.string.tpl_discard),
                                    onClick = {
                                        RunTrackingService.cancel(context)
                                        viewModel.cancelRecording()
                                        onBack()
                                    },
                                )
                            }
                        }
                        if (uiState.isRealRunActive && !isRecording) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.tpl_session_busy),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        LxDialog(
            title = stringResource(R.string.tpl_save_title),
            confirmText = stringResource(R.string.action_save),
            dismissText = stringResource(R.string.action_discard),
            onDismissRequest = { },
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            onDismiss = {
                showSaveDialog = false
                viewModel.cancelRecording()
                onBack()
            },
            onConfirm = {
                scope.launch {
                    val ok = viewModel.saveRecording(nameInput)
                    showSaveDialog = false
                    if (ok) {
                        onBack()
                    }
                }
            },
            content = {
                Column {
                    Text(stringResource(R.string.tpl_need_name), style = MiuixTheme.textStyles.body2)
                    Spacer(modifier = Modifier.height(12.dp))
                    LxTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = stringResource(R.string.tpl_name),
                        keyboardOptions = KeyboardOptions.Default,
                    )
                }
            },
        )
    }
}

@Composable
private fun MetricCell(title: String, value: String, modifier: Modifier = Modifier) {
    LxCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun Long.formatDurationTpl(): String {
    val hours = TimeUnit.SECONDS.toHours(this)
    val minutes = TimeUnit.SECONDS.toMinutes(this) % 60
    val seconds = this % 60
    return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}
