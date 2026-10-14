package com.linxin.feature.running.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxTextField
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.location.CoordinateConverter
import com.linxin.core.location.LocationProvider
import com.linxin.feature.running.data.RouteTemplateStore
import com.linxin.feature.running.domain.GeoDistance
import com.linxin.feature.running.domain.RunningCampus
import com.linxin.feature.running.domain.TrackPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.api.IGeoPoint
import com.linxin.core.map.AmapStreetTileSource
import com.linxin.core.map.prepareOsmdroid
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 在地图上点选规划跑步路线。
 *
 * 底图用高德那套公开瓦片（2026-10-14 现测：校区这块 z16 瓦片回 200、7KB、90ms；
 * OSM 的瓦片在我们这台机的网上直接连不上，Mapbox 同理）。高德是 GCJ-02，
 * 我们存的是 GPS 的 WGS-84，所以**上图换算一次、点图反算一次**：
 * 存下来的路线跟真 GPS 同一套坐标，喂给内部假 GPS 才落在路上。
 */
@HiltViewModel
class RoutePlannerViewModel @Inject constructor(
    private val routeTemplateStore: RouteTemplateStore,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _waypoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val waypoints: StateFlow<List<TrackPoint>> = _waypoints.asStateFlow()

    /** 上一次动作的结果：存成功、或者"太短了"这类不让存的原因 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val templates = routeTemplateStore.templates

    var name by mutableStateOf("")
        private set

    fun rename(next: String) {
        name = next
    }

    val distanceKm: String get() = "%.2f".format(distanceMeters / 1000.0)

    val distanceMeters: Double
        get() {
            val list = _waypoints.value
            var total = 0.0
            for (i in 1 until list.size) {
                total += GeoDistance.metersBetween(
                    list[i - 1].latitude, list[i - 1].longitude,
                    list[i].latitude, list[i].longitude,
                )
            }
            return total
        }

    fun addWaypoint(point: TrackPoint) {
        _notice.value = null
        _waypoints.update { it + point }
    }

    fun undo() {
        _notice.value = null
        _waypoints.update { if (it.isEmpty()) it else it.dropLast(1) }
    }

    fun clear() {
        _notice.value = null
        _waypoints.value = emptyList()
    }

    /** 载入一条已存路线接着改；坐标原样搬，不重算 */
    fun load(templateId: String) {
        val template = templates.value.firstOrNull { it.id == templateId } ?: return
        _notice.value = null
        _waypoints.value = template.points
        name = template.name
    }

    fun campusCenter(): GeoPoint {
        val gcj = CoordinateConverter.wgs84ToGcj02(
            RunningCampus.CENTER_LATITUDE,
            RunningCampus.CENTER_LONGITUDE,
        )
        return GeoPoint(gcj.latitude, gcj.longitude)
    }

    /** 相机跳到当前位置（喂点开着就是喂进去的那份）；读不到就回学校中心 */
    fun centerOnMe(): GeoPoint {
        val current = locationProvider.getLastKnownLocation() ?: return campusCenter()
        val gcj = CoordinateConverter.wgs84ToGcj02(current.latitude, current.longitude)
        return GeoPoint(gcj.latitude, gcj.longitude)
    }

    /**
     * 存成路线模板。`durationSeconds` 按步行 1.35 m/s 估 —— 这一版只拿来喂内部假 GPS，
     * 不是运动成绩，估个"走得动"的节奏就够，别当成跑完这条要多久。
     */
    fun save() {
        val points = _waypoints.value
        if (points.size < 2) {
            _notice.value = "至少点两个点"
            return
        }
        val meters = distanceMeters
        if (meters < 50.0) {
            _notice.value = "这条太短了，不到 50 米"
            return
        }
        val label = name.ifBlank { "地图规划 · %.2f km".format(meters / 1000.0) }
        viewModelScope.launch {
            routeTemplateStore.save(
                name = label,
                points = points,
                totalDistanceMeters = meters,
                durationSeconds = (meters / WALK_METERS_PER_SECOND).toLong(),
            )
            _notice.value = "已存成路线：%s".format(label)
        }
    }

    private companion object {
        /** 约 4.9 km/h：喂假 GPS 时沿路线走的节奏 */
        const val WALK_METERS_PER_SECOND = 1.35
    }
}

@Composable
fun RoutePlannerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutePlannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var showLibrary by remember { mutableStateOf(false) }
    var pendingCenter by remember { mutableStateOf<GeoPoint?>(null) }

    // 只加我们自己那一层：点图加节点 + 画折线，不依赖 Marker 的图标资源
    val planOverlay = remember {
        PlanOverlay { point ->
            val wgs = CoordinateConverter.gcj02ToWgs84(point.latitude, point.longitude)
            viewModel.addWaypoint(TrackPoint(wgs.latitude, wgs.longitude))
        }
    }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(waypoints) {
        planOverlay.display = waypoints.map {
            val gcj = CoordinateConverter.wgs84ToGcj02(it.latitude, it.longitude)
            GeoPoint(gcj.latitude, gcj.longitude)
        }
        mapView?.invalidate()
    }

    LaunchedEffect(pendingCenter) {
        val target = pendingCenter ?: return@LaunchedEffect
        mapView?.controller?.setCenter(target)
        pendingCenter = null
    }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.planner_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    prepareOsmdroid(ctx)
                    MapView(ctx).apply {
                        setTileSource(AmapStreetTileSource())
                        setMultiTouchControls(true)
                        overlays.add(planOverlay)
                        controller.setZoom(17.0)
                        controller.setCenter(viewModel.campusCenter())
                        onResume()
                        mapView = this
                    }
                },
                onRelease = { view ->
                    view.overlays.remove(planOverlay)
                    view.onPause()
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.planner_distance, waypoints.size, viewModel.distanceKm),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                )
                Text(
                    text = stringResource(R.string.planner_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LxOutlinedButton(
                        text = stringResource(R.string.planner_undo),
                        onClick = viewModel::undo,
                        enabled = waypoints.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                    LxOutlinedButton(
                        text = stringResource(R.string.planner_clear),
                        onClick = viewModel::clear,
                        enabled = waypoints.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                    LxOutlinedButton(
                        text = stringResource(R.string.planner_me),
                        onClick = { pendingCenter = viewModel.centerOnMe() },
                        modifier = Modifier.weight(1f),
                    )
                }
                LxTextField(
                    value = viewModel.name,
                    onValueChange = viewModel::rename,
                    label = stringResource(R.string.planner_name),
                    singleLine = true,
                )
                notice?.let { message ->
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (message.startsWith("已存")) LxSandDeep else MiuixTheme.colorScheme.error,
                    )
                }
                LxButton(
                    text = stringResource(R.string.planner_save),
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                )
                LxOutlinedButton(
                    text = stringResource(R.string.planner_library),
                    onClick = { showLibrary = !showLibrary },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showLibrary) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (templates.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.planner_library_empty),
                                style = MiuixTheme.textStyles.footnote1,
                                color = LxInkMuted,
                            )
                        }
                    } else {
                        items(templates, key = { "p_" + it.id }) { template ->
                            LxCard(onClick = { viewModel.load(template.id) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = template.name,
                                        style = MiuixTheme.textStyles.body2,
                                        color = LxInk,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "%.2f km".format(template.totalDistanceMeters / 1000.0),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = LxInkMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 画点、画线，并把单击换成一个坐标。
 * 自己画而不用 Marker：那玩意要图标资源，换分辨率还得管；几个圆点加一条线够清楚了。
 */
private class PlanOverlay(
    private val onPick: (IGeoPoint) -> Unit,
) : Overlay() {

    var display: List<GeoPoint> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = Color.parseColor("#C75B39")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#C75B39")
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (display.isEmpty()) return
        val screen = ArrayList<Point>(display.size)
        display.forEach { geo ->
            val p = Point()
            mapView.projection.toPixels(geo, p)
            screen.add(p)
        }
        for (i in 1 until screen.size) {
            val a = screen[i - 1]
            val b = screen[i]
            canvas.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), linePaint)
        }
        screen.forEachIndexed { index, point ->
            val radius = if (index == 0) 20f else 14f
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, dotPaint)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, ringPaint)
        }
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        val geo = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) ?: return false
        onPick(geo)
        return true
    }
}
