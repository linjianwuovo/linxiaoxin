package com.linxin.feature.more.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxFilterChip
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.location.InternalGpsFeed
import com.linxin.core.location.LocationProvider
import com.linxin.core.map.LiveTrackMap
import com.linxin.feature.running.data.RouteTemplateStore
import com.linxin.feature.running.domain.RouteTemplate
import com.linxin.feature.running.domain.RunningCampus
import com.linxin.feature.running.domain.TrackPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 这一页能干的四种事：定在当前位置、定在学校中心、沿录好的路线走、原地绕圈跑 */
enum class MockMode { CURRENT, CAMPUS, ROUTE, LAPS }

/**
 * 应用内部喂假 GPS 那一页。
 *
 * 刻意不碰系统那套 test provider：它要他在开发者选项里把安大信选成模拟位置应用，
 * 而且注出去的点会同时给别的 App。这里只接我们自己的 `LocationProvider` ——
 * 开着的时候安大信读到的是这份轨迹，关掉立刻回真 GPS，别的 App 全程不知道发生过什么。
 */
@HiltViewModel
class MockLocationViewModel @Inject constructor(
    private val feed: InternalGpsFeed,
    private val locationProvider: LocationProvider,
    routeTemplateStore: RouteTemplateStore,
) : ViewModel() {

    val isOn = feed.isOn
    val point = feed.point
    val fedCount = feed.fedCount
    val templates = routeTemplateStore.templates
    val trail = feed.trail

    var mode by mutableStateOf(MockMode.CURRENT)
        private set

    /** 点了开始之后才报得出来的话：这台机没读到当前位置、还没录好的路线 */
    var notice by mutableStateOf<String?>(null)
        private set

    /** 换档先停当前这一档：不然"点了没反应"看起来像坏了，其实是还在喂上一档 */
    fun selectMode(next: MockMode) {
        if (feed.isOn.value) feed.stop()
        mode = next
        notice = null
    }

    /** 圈心：默认拿当前位置，读不到才退回学校中心；可以在预览图上点着挪 */
    var ringCenter by mutableStateOf<TrackPoint?>(null)
        private set

    var ringPerimeter by mutableStateOf(400.0)
        private set

    fun pickCenter(point: TrackPoint) {
        ringCenter = point
    }

    fun pickPerimeter(meters: Double) {
        ringPerimeter = meters
    }

    /** 当前位置优先；喂点正开着时读到的就是喂进去的那份，所以先记下再取 */
    fun resolvedRingCenter(): TrackPoint =
        ringCenter ?: locationProvider.getLastKnownLocation()?.let {
            TrackPoint(it.latitude, it.longitude)
        } ?: TrackPoint(RunningCampus.CENTER_LATITUDE, RunningCampus.CENTER_LONGITUDE)

    /** 预览/开跑前都要拿到的那一圈点 */
    fun ringPoints(): List<TrackPoint> = feed.lapRing(resolvedRingCenter(), ringPerimeter)

    fun useCurrentAsCenter() {
        locationProvider.getLastKnownLocation()?.let {
            ringCenter = TrackPoint(it.latitude, it.longitude)
            notice = null
        } ?: run { notice = "没读到当前位置，直接在图上点一个圈心" }
    }

    fun start(template: RouteTemplate?) {
        notice = null
        when (mode) {
            MockMode.CURRENT -> {
                // 必须在开喂之前取：开喂之后 getLastKnownLocation() 回的就是喂进去的那份了
                val current = locationProvider.getLastKnownLocation()
                if (current == null) {
                    notice = "没读到当前位置，先用学校中心或者选一条路线"
                    return
                }
                feed.startHold(TrackPoint(current.latitude, current.longitude))
            }

            MockMode.CAMPUS -> feed.startHold(
                TrackPoint(RunningCampus.CENTER_LATITUDE, RunningCampus.CENTER_LONGITUDE),
            )

            MockMode.LAPS -> feed.startLaps(resolvedRingCenter(), ringPerimeter)

            MockMode.ROUTE -> {
                if (template == null) {
                    notice = "还没有录好的路线，先去跑步那里录一条"
                    return
                }
                feed.startRoute(template.points, template.durationSeconds)
            }
        }
    }

    fun stop() = feed.stop()
}

@Composable
fun MockLocationScreen(
    onBack: () -> Unit,
    onOpenPlanner: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MockLocationViewModel = hiltViewModel(),
) {
    val isOn by viewModel.isOn.collectAsStateWithLifecycle()
    val point by viewModel.point.collectAsStateWithLifecycle()
    val fedCount by viewModel.fedCount.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    var selectedRoute by remember { mutableStateOf<RouteTemplate?>(null) }

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
                            text = if (isOn) {
                                stringResource(R.string.mock_loc_state_feeding, fedCount)
                            } else {
                                stringResource(R.string.mock_loc_state_idle)
                            },
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOn) LxSandDeep else LxInk,
                        )
                        point?.let {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "%.5f, %.5f".format(it.latitude, it.longitude),
                                style = MiuixTheme.textStyles.footnote1,
                                color = LxInkMuted,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(
                                if (isOn) R.string.mock_loc_scope_on else R.string.mock_loc_scope,
                            ),
                            style = MiuixTheme.textStyles.footnote1,
                            color = LxInkMuted,
                        )
                    }
                }
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
                    ModeChip(
                        label = stringResource(R.string.mock_loc_mode_laps),
                        selected = viewModel.mode == MockMode.LAPS,
                        onClick = { viewModel.selectMode(MockMode.LAPS) },
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

            if (viewModel.mode == MockMode.LAPS || isOn) {
                item(key = "mock_ring_map") {
                    LxCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(
                                    if (isOn) R.string.mock_loc_trail_hint else R.string.mock_loc_ring_hint,
                                ),
                                style = MiuixTheme.textStyles.footnote1,
                                color = LxInkMuted,
                                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
                            )
                            LiveTrackMap(
                                points = if (isOn) trail else viewModel.ringPoints(),
                                markLast = isOn,
                                follow = isOn,
                                onPick = if (isOn) null else { picked -> viewModel.pickCenter(picked) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                            )
                        }
                    }
                }
                item(key = "mock_ring_size") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(400.0, 512.0, 800.0).forEach { meters ->
                            ModeChip(
                                label = "%.0f 米".format(meters),
                                selected = viewModel.ringPerimeter == meters,
                                onClick = { viewModel.pickPerimeter(meters) },
                            )
                        }
                        ModeChip(
                            label = stringResource(R.string.mock_loc_ring_center_now),
                            selected = false,
                            onClick = { viewModel.useCurrentAsCenter() },
                        )
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
                LxButton(
                    text = stringResource(
                        if (isOn) R.string.mock_loc_stop else R.string.mock_loc_start,
                    ),
                    onClick = { if (isOn) viewModel.stop() else viewModel.start(selectedRoute) },
                    enabled = !isOn || viewModel.mode != MockMode.ROUTE || selectedRoute != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "mock_planner") {
                // 想画一条真路线就去地图上点：那一页是我自己的，不碰旧的"路线模拟"
                LxOutlinedButton(
                    text = stringResource(R.string.planner_entry),
                    onClick = onOpenPlanner,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "mock_no_write") {
                Text(
                    text = stringResource(R.string.mock_loc_no_write),
                    style = MiuixTheme.textStyles.footnote1,
                    color = LxInkMuted,
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    LxFilterChip(label = label, selected = selected, onClick = onClick)
}
