package com.linxin.feature.sports.service

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.linxin.feature.sports.SportsCoords
import com.linxin.feature.sports.domain.RunRecordDraft
import com.linxin.feature.sports.domain.RunSession
import com.linxin.feature.sports.domain.SportsRoute
import com.linxin.feature.sports.domain.WgsPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

/**
 * 跑步引擎：一场跑步从开始到能交出去，全在这一个单例里。
 *
 * 采集节奏照官方 APK 里那行 `LocationClientOption.setScanSpan(3000)` —— **三秒一个点**，
 * 而且官方 `onReceiveLocation` 是每来一个点无条件 `points.add()`，不做最小距离筛选。
 * 我们只多加一道"跳点保护"（相邻两点超过 120 米就当没看见），因为真 GPS 偶尔会甩一下，
 * 甩出来的那条长边会被服务器当成轨迹异常。
 *
 * 两种点源，同一个入口：
 *  1. 真 GPS（`LocationManager` 的 GPS_PROVIDER）；
 *  2. **应用内模拟**：沿自己画的线走，一秒都不碰系统 —— 不注册 test provider、
 *     不开"模拟位置应用"。官方那套检测（`isFakeLocation` 读百度 SDK 的
 *     `getMockGpsProbability()`，概率≥2 就弹框并丢点）只盯系统级模拟，我们这种
 *     只在自己进程里发的点它根本看不见。
 */
@Singleton
class SportsRunEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class State(
        val active: Boolean = false,
        val paused: Boolean = false,
        val startedAt: Long = 0L,
        val pausedTotalMillis: Long = 0L,
        val pausedAt: Long? = null,
        val meters: Double = 0.0,
        val points: List<WgsPoint> = emptyList(),
        val simulating: Boolean = false,
        val waitingFix: Boolean = true,
        val error: String? = null,
    ) {
        val distanceKm: Double get() = meters / 1000.0

        /** 扣掉暂停的那一段 */
        fun elapsedSeconds(now: Long = System.currentTimeMillis()): Long {
            if (startedAt <= 0L) return 0L
            val pausedExtra = if (paused) (now - (pausedAt ?: now)).coerceAtLeast(0L) else 0L
            return ((now - startedAt - pausedTotalMillis - pausedExtra) / 1000L).coerceAtLeast(0L)
        }

        val speedKmh: Double
            get() {
                val seconds = elapsedSeconds().coerceAtLeast(1L)
                return distanceKm / seconds * 3600.0
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: RunSession? = null

    /** 这一场的目标里程：流体云那条进度条按它算，没开场时按 1 公里 */
    val targetKm: Double get() = session?.targetKm?.takeIf { it > 0.0 } ?: 1.0
    private var simJob: Job? = null
    private var listener: LocationListener? = null

    /** 有没有真定位权限（模拟跑的时候不需要，但界面要能告诉他差别） */
    fun hasGpsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * 开场。[route] 给了就走模拟，不给就等真 GPS。
     * [paceKmh] 只影响模拟那一路。
     */
    fun begin(runSession: RunSession, route: SportsRoute?, paceKmh: Double) {
        finishQuietly()
        session = runSession
        _state.value = State(
            active = true,
            startedAt = System.currentTimeMillis(),
            simulating = route != null,
        )
        if (route != null && route.points.size >= 2) {
            startSimulation(route, paceKmh)
        } else {
            startRealGps()
        }
    }

    fun pause() {
        val current = _state.value
        if (!current.active || current.paused) return
        _state.value = current.copy(
            paused = true,
            pausedAt = System.currentTimeMillis(),
        )
    }

    fun resume() {
        val current = _state.value
        if (!current.paused) return
        val now = System.currentTimeMillis()
        _state.value = current.copy(
            paused = false,
            pausedAt = null,
            pausedTotalMillis = current.pausedTotalMillis +
                (now - (current.pausedAt ?: now)).coerceAtLeast(0L),
        )
    }

    /** 收场，交出可以上传的那一坨；不够一个点或没开场就返回 null */
    fun finish(): RunRecordDraft? {
        val current = _state.value
        val runSession = session ?: return null
        stopSources()
        session = null
        _state.value = State()
        if (current.points.size < 2) return null
        val seconds = current.elapsedSeconds().coerceAtLeast(1L)
        return RunRecordDraft(
            session = runSession,
            startText = formatText(current.startedAt),
            endText = formatText(System.currentTimeMillis()),
            mileKm = current.distanceKm,
            timeSeconds = seconds,
            speedKmh = current.distanceKm / seconds * 3600.0,
            points = current.points,
        )
    }

    fun cancel() {
        stopSources()
        session = null
        _state.value = State()
    }

    private fun finishQuietly() {
        stopSources()
        session = null
    }

    private fun stopSources() {
        simJob?.cancel()
        simJob = null
        listener?.let { runCatching { locationManager().removeUpdates(it) } }
        listener = null
    }

    private fun locationManager(): LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun startRealGps() {
        if (!hasGpsPermission()) {
            _state.value = _state.value.copy(error = "没有定位权限，开不了真 GPS")
            return
        }
        val receiver = LocationListener { location -> onFix(location.toWgsPoint()) }
        listener = receiver
        try {
            locationManager().requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                TICK_MS,
                0f,
                receiver,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(error = "定位权限被收回")
        }
    }

    private fun Location.toWgsPoint(): WgsPoint =
        WgsPoint(latitude = latitude, longitude = longitude, atMillis = time)

    /** 沿画好的线三秒发一个点；到线尾折返，绝不瞬移回起点 */
    private fun startSimulation(route: SportsRoute, paceKmh: Double) {
        val line = route.points
        val sampler = LineSampler(line)
        if (sampler.totalMeters < 20.0) {
            _state.value = _state.value.copy(error = "这条线太短，先画一条长一点的")
            return
        }
        val target = (paceKmh / 3.6).coerceIn(SPEED_MIN_MS, SPEED_MAX_MS)
        val low = (target * 0.85).coerceAtLeast(SPEED_MIN_MS)
        val high = (target * 1.15).coerceAtMost(SPEED_MAX_MS)
        val wander = Wander(Random(System.nanoTime()))
        simJob = scope.launch {
            var traveled = 0.0
            var speed = target
            var forward = true
            while (isActive) {
                if (!_state.value.paused) {
                    val base = sampler.at(traveled)
                    val offset = wander.next()
                    onFix(
                        WgsPoint(
                            latitude = base.latitude + SportsCoords.latDelta(offset.first),
                            longitude = base.longitude +
                                SportsCoords.lngDelta(offset.second, base.latitude),
                        )
                    )
                    speed = (speed + (Random.nextDouble() - 0.5) * 0.3).coerceIn(low, high)
                    traveled += (speed * SECONDS_PER_TICK).let { if (forward) it else -it }
                    if (traveled >= sampler.totalMeters) {
                        traveled = 2.0 * sampler.totalMeters - traveled
                        forward = false
                    } else if (traveled <= 0.0) {
                        traveled = -traveled
                        forward = true
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    /** 真 GPS 和模拟走同一个入口：暂停期间一律不计 */
    private fun onFix(point: WgsPoint) {
        val current = _state.value
        if (!current.active || current.paused) return
        if (!SportsCoords.isSane(point.latitude, point.longitude)) return

        val last = current.points.lastOrNull()
        val segment = last?.let {
            SportsCoords.metersBetween(it.latitude, it.longitude, point.latitude, point.longitude)
        } ?: 0.0
        // 跳点保护：一秒甩出 120 米不是人，那种长边会让整条轨迹被判异常
        if (segment > JUMP_GUARD_METERS) return

        val accepted = if (last == null || segment < MIN_STEP_METERS) 0.0 else segment
        _state.value = current.copy(
            meters = current.meters + accepted,
            points = (current.points + point).takeLast(MAX_POINTS),
            waitingFix = current.points.size < 2,
        )
    }

    /** 按弧长在折线上取点 */
    private class LineSampler(private val points: List<WgsPoint>) {
        private val cumulative = DoubleArray(points.size)
        val totalMeters: Double

        init {
            var sum = 0.0
            for (i in 1 until points.size) {
                sum += SportsCoords.metersBetween(
                    points[i - 1].latitude, points[i - 1].longitude,
                    points[i].latitude, points[i].longitude,
                )
                cumulative[i] = sum
            }
            totalMeters = sum
        }

        fun at(meters: Double): WgsPoint {
            if (points.size < 2) return WgsPoint(0.0, 0.0)
            val target = meters.coerceIn(0.0, totalMeters)
            var index = 1
            while (index < points.size && cumulative[index] < target) index++
            val to = if (index >= points.size) points.lastIndex else index
            val from = (to - 1).coerceAtLeast(0)
            val length = (cumulative[to] - cumulative[from]).coerceAtLeast(0.001)
            val ratio = ((target - cumulative[from]) / length).coerceIn(0.0, 1.0)
            val a = points[from]
            val b = points[to]
            return WgsPoint(
                latitude = a.latitude + (b.latitude - a.latitude) * ratio,
                longitude = a.longitude + (b.longitude - a.longitude) * ratio,
            )
        }
    }

    /** 真 GPS 不贴线走：偏移做缓慢随机游走，每步最多 0.6 米、离线最远 2.5 米 */
    private class Wander(private val random: Random) {
        private var north = 0.0
        private var east = 0.0

        fun next(): Pair<Double, Double> {
            north = (north + (random.nextDouble() - 0.5) * 1.2).coerceIn(-MAX_OFFSET, MAX_OFFSET)
            east = (east + (random.nextDouble() - 0.5) * 1.2).coerceIn(-MAX_OFFSET, MAX_OFFSET)
            return north to east
        }

        companion object {
            private const val MAX_OFFSET = 2.5
        }
    }

    private companion object {
        const val TICK_MS = 3_000L
        const val SECONDS_PER_TICK = TICK_MS / 1000.0
        const val SPEED_MIN_MS = 0.6
        const val SPEED_MAX_MS = 4.2
        const val MIN_STEP_METERS = 1.0
        const val JUMP_GUARD_METERS = 120.0
        const val MAX_POINTS = 4_000

        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

        fun formatText(millis: Long): String =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(FORMAT)
    }
}
