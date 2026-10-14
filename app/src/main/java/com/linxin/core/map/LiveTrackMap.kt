package com.linxin.core.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.linxin.core.location.CoordinateConverter
import com.linxin.feature.running.domain.RunningCampus
import com.linxin.feature.running.domain.TrackPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * 内置地图的公共件：高德街道瓦片 + 一条轨迹。
 *
 * 为什么是高德：2026-10-14 在两台测试机上现测，校区这块 z16 瓦片回 200、7KB、90ms；
 * OSM 和 Mapbox 的瓦片在同一个网上直接超时。官方 App 那侧抄不来 —— 它显示地图用百度 SDK，
 * 抓包里一条瓦片请求都没有（全走 SDK 私有通道）。
 *
 * 坐标系：我们存的是 GPS 的 WGS-84，高德是 GCJ-02，所以**上图前一律换算一次**。
 */
class AmapStreetTileSource : OnlineTileSourceBase(
    "AMapStreet",
    3,
    18,
    256,
    ".png",
    arrayOf("https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&"),
) {
    override fun getTileURLString(tileIndex: Long): String =
        baseUrl + "x=" + MapTileIndex.getX(tileIndex) +
            "&y=" + MapTileIndex.getY(tileIndex) +
            "&z=" + MapTileIndex.getZoom(tileIndex)
}

/** osmdroid 要一个 User-Agent 才给瓦片；配置存我们自己的 sp，不去碰系统默认那份 */
fun prepareOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.userAgentValue = context.packageName
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
}

/**
 * 实时轨迹图：喂点在动、真 GPS 在动，这条线就跟着长。
 *
 * 放在跑步/录制那两页上，是为了让"虚拟跑"这件事看得见 —— 屏幕上有圈可绕，
 * 比只有一串数字好判断是不是真在走。新点一到就把相机跟上去。
 */
@Composable
fun LiveTrackMap(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val trackOverlay = remember { TrackOverlay() }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(points) {
        trackOverlay.display = points.map {
            val gcj = CoordinateConverter.wgs84ToGcj02(it.latitude, it.longitude)
            GeoPoint(gcj.latitude, gcj.longitude)
        }
        val last = trackOverlay.display.lastOrNull()
        if (last != null) mapView?.controller?.setCenter(last)
        mapView?.invalidate()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            prepareOsmdroid(ctx)
            MapView(ctx).apply {
                setTileSource(AmapStreetTileSource())
                setMultiTouchControls(true)
                setUseDataConnection(true)
                overlays.add(trackOverlay)
                controller.setZoom(17.5)
                val gcj = CoordinateConverter.wgs84ToGcj02(
                    RunningCampus.CENTER_LATITUDE,
                    RunningCampus.CENTER_LONGITUDE,
                )
                controller.setCenter(GeoPoint(gcj.latitude, gcj.longitude))
                onResume()
                mapView = this
            }
        },
        onRelease = { view ->
            view.overlays.remove(trackOverlay)
            view.onPause()
        },
    )
}

/** 一条线 + 一堆点 + 最后一个点画个大圆当"我在这儿" */
private class TrackOverlay : Overlay() {

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
        screen.forEach { point ->
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 8f, dotPaint)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 8f, ringPaint)
        }
        screen.lastOrNull()?.let { here ->
            canvas.drawCircle(here.x.toFloat(), here.y.toFloat(), 24f, dotPaint)
            canvas.drawCircle(here.x.toFloat(), here.y.toFloat(), 24f, ringPaint)
        }
    }
}
