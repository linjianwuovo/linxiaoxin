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
import com.linxin.feature.sports.domain.WgsPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/**
 * 跑步引擎：一场跑步从开始到能交出去，全在这一个单例里。
 *
 * 采集节奏照官方 APK 里那行 `LocationClientOption.setScanSpan(3000)` —— **三秒一个点**，
 * 而且官方 `onReceiveLocation` 是每来一个点无条件 `points.add()`，不做最小距离筛选。
 * 我们只多加一道"跳点保护"（相邻两点超过 120 米就当没看见），因为真 GPS 偶尔会甩一下，
 * 甩出来的那条长边会被服务器当成轨迹异常。
 *
 * 点源只有一个：真 GPS（`LocationManager` 的 GPS_PROVIDER）。
 * 不注册 test provider、不开"模拟位置应用" —— 官方那套检测（`isFakeLocation` 读百度 SDK 的
 * `getMockGpsProbability()`，概率≥2 就弹框并丢点）盯的就是系统级模拟，这条路在本包里不存在。
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
        val waitingFix: Boolean = true,
        /** 实时速度（km/h）：上一个点到这一个点之间算出来的，不是全程平均 */
        val instantKmh: Double = 0.0,
        val lastFixAt: Long = 0L,
        val lastFixMeters: Double = 0.0,
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
    private var listener: LocationListener? = null

    /** 有没有真定位权限 */
    fun hasGpsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** 开场：记下服务器给的那次会话，然后开始收真 GPS 点。 */
    fun begin(runSession: RunSession) {
        finishQuietly()
        session = runSession
        _state.value = State(active = true, startedAt = System.currentTimeMillis())
        startRealGps()
    }

    fun pause() {
        val current = _state.value
        if (!current.active || current.paused) return
        _state.value = current.copy(
            paused = true,
            pausedAt = System.currentTimeMillis(),
            instantKmh = 0.0,
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

    /** 收点：暂停期间一律不计 */
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
        val meters = current.meters + accepted
        val now = System.currentTimeMillis()
        // 这一段用了多久：第一次给点、或者中间断过 15 秒以上，就不算瞬时速度
        val spanSeconds = if (current.lastFixAt <= 0L) 0L else (now - current.lastFixAt) / 1000L
        val instant = if (spanSeconds in 1..15 && meters >= current.lastFixMeters) {
            val raw = (meters - current.lastFixMeters) / spanSeconds.toDouble() * 3.6
            if (current.instantKmh <= 0.0) raw else current.instantKmh * 0.5 + raw * 0.5
        } else {
            0.0
        }
        _state.value = current.copy(
            meters = meters,
            points = (current.points + point).takeLast(MAX_POINTS),
            waitingFix = current.points.size < 2,
            instantKmh = instant,
            lastFixAt = now,
            lastFixMeters = meters,
        )
    }

    /** 按弧长在折线上取点 */
    private companion object {
        const val TICK_MS = 3_000L
        const val MIN_STEP_METERS = 1.0
        const val JUMP_GUARD_METERS = 120.0
        const val MAX_POINTS = 4_000

        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

        fun formatText(millis: Long): String =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(FORMAT)
    }
}
