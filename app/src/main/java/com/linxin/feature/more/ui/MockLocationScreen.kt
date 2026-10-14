package com.linxin.feature.more.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.location.LocationProvider
import com.linxin.core.location.MockLocationFeeder
import com.linxin.feature.running.domain.RouteTemplate
import com.linxin.feature.running.domain.RunningCampus
import com.linxin.feature.running.domain.TrackPoint
import com.linxin.feature.running.data.RouteTemplateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 这一页能干的三种事：定在当前位置、定在学校中心、沿录好的路线走 */
enum class MockMode { CURRENT, CAMPUS, ROUTE }

@HiltViewModel
class MockLocationViewModel @Inject constructor(
    private val feeder: MockLocationFeeder,
    private val locationProvider: LocationProvider,
    routeTemplateStore: RouteTemplateStore,
) : ViewModel() {

    val status = feeder.status
    val fedPoint = feeder.fedPoint
    val fedCount = feeder.fedCount
    val templates = routeTemplateStore.templates

    var mode by mutableStateOf(MockMode.CURRENT)
        private set

    /** 点了开始之后才报出来的话：没当前位置、没路线这类"这模式在这台机上不成立" */
    var notice by mutableStateOf<String?>(null)
        private set

    fun selectMode(next: MockMode) {
        mode = next
        notice = null
    }

    /** 进来先探一次能不能注，别等点了开始才发现开发者选项没设 */
    fun probe(): MockLocationFeeder.Status = feeder.probe()

    fun start(template: RouteTemplate?) {
        notice = null
        when (mode) {
            MockMode.CURRENT -> {
                val current = locationProvider.getLastKnownLocation()
                if (current == null) {
                    notice = "没读到当前位置，先用学校中心或者选一条路线"
                    return
                }
                feeder.startHold(TrackPoint(current.latitude, current.longitude))
            }

            MockMode.CAMPUS -> feeder.startHold(
                TrackPoint(RunningCampus.CENTER_LATITUDE, RunningCampus.CENTER_LONGITUDE),
            )

            MockMode.ROUTE -> {
                if (template == null) {
                    notice = "还没有录好的路线，先去跑步那里录一条"
                    return
                }
                feeder.startRoute(template.points, template.durationSeconds)
            }
        }
    }

    fun stop() = feeder.stop()
}

@Composable
fun MockLocationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MockLocationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val fedPoint by viewModel.fedPoint.collectAsStateWithLifecycle()
    val fedCount by viewModel.fedCount.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var selectedRoute by remember { mutableStateOf<RouteTemplate?>(null) }

    // 这一页一走人就停：假 GPS 是整机级的，留着它别的 App 会一直收到假坐标
    DisposableEffect(Unit) {
        viewModel.probe()
        onDispose { viewModel.stop() }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.mock_loc_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "mock_state") {
                LxCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = statusLabel(status, fedCount),
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = if (status == MockLocationFeeder.Status.FEEDING) {
                                LxSandDeep
                            } else {
                                LxInk
                            },
                        )
                        fedPoint?.let { point ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "%.5f, %.5f".format(point.latitude, point.longitude),
                                style = MiuixTheme.textStyles.footnote1,
                                color = LxInkMuted,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.mock_loc_hint_dev),
                            style = MiuixTheme.textStyles.footnote1,
                            color = LxInkMuted,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            LxOutlinedButton(
                                text = stringResource(R.string.mock_loc_open_dev),
                                onClick = { context.openDevelopmentSettings() },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            LxOutlinedButton(
                                text = stringResource(R.string.mock_loc_reprobe),
                                onClick = { viewModel.probe() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item(key = "mock_desc") {
                Text(
                    text = stringResource(R.string.mock_loc_desc),
                    style = MiuixTheme.textStyles.footnote1,
                    color = LxInkMuted,
                )
            }

            item(key = "mock_modes") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeChip(
                        label = stringResource(R.string.mock_loc_mode_current),
                        selected = viewModel.mode == MockMode.CURRENT,
                        onClick = { viewModel.selectMode(MockMode.CURRENT) },
                    )
                    ModeChip(
                        label = stringResource(R.string.mock_loc_mode_campus),
                        selected = viewModel.mode == MockMode.CAMPUS,
                        onClick = { viewModel.selectMode(MockMode.CAMPUS) },
                    )
                    ModeChip(
                        label = stringResource(R.string.mock_loc_mode_route),
                        selected = viewModel.mode == MockMode.ROUTE,
                        onClick = { viewModel.selectMode(MockMode.ROUTE) },
                    )
                }
            }

            if (viewModel.mode == MockMode.ROUTE) {
                if (templates.isEmpty()) {
                    item(key = "mock_no_route") {
                        Text(
                            text = stringResource(R.string.mock_loc_no_route),
                            style = MiuixTheme.textStyles.footnote1,
                            color = LxInkMuted,
                        )
                    }
                } else {
                    items(templates, key = { "r_" + it.id }) { template ->
                        val picked = selectedRoute?.id == template.id
                        LxCard(onClick = { selectedRoute = template }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = template.name,
                                    style = MiuixTheme.textStyles.body2,
                                    color = LxInk,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (picked) {
                                        stringResource(R.string.mock_loc_picked)
                                    } else {
                                        "%.2f km".format(template.totalDistanceMeters / 1000.0)
                                    },
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = if (picked) LxSandDeep else LxInkMuted,
                                )
                            }
                        }
                    }
                }
            }

            viewModel.notice?.let { message ->
                item(key = "mock_notice") {
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }

            item(key = "mock_action") {
                if (status == MockLocationFeeder.Status.FEEDING) {
                    LxButton(
                        text = stringResource(R.string.mock_loc_stop),
                        onClick = { viewModel.stop() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LxButton(
                        text = stringResource(R.string.mock_loc_start),
                        onClick = { viewModel.start(selectedRoute) },
                        enabled = viewModel.mode != MockMode.ROUTE || selectedRoute != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    LxFilterChip(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun statusLabel(status: MockLocationFeeder.Status, fedCount: Int): String = when (status) {
    MockLocationFeeder.Status.FEEDING -> stringResource(R.string.mock_loc_state_feeding, fedCount)
    MockLocationFeeder.Status.NOT_SELECTED -> stringResource(R.string.mock_loc_state_not_selected)
    MockLocationFeeder.Status.NO_SERVICE -> stringResource(R.string.mock_loc_state_no_service)
    MockLocationFeeder.Status.IDLE -> stringResource(R.string.mock_loc_state_idle)
}

private fun Context.openDevelopmentSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
