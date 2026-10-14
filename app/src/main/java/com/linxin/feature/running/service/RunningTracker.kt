package com.linxin.feature.running.service

import com.linxin.core.locale.AppText
import com.linxin.R
import android.location.Location
import com.linxin.feature.running.domain.GeoDistance
import com.linxin.feature.running.domain.RunningSnapshot
import com.linxin.feature.running.domain.RunningStartInfo
import com.linxin.feature.running.domain.RunningTrackerState
import com.linxin.feature.running.domain.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunningTracker @Inject constructor() {

    private val _state = MutableStateFlow(RunningTrackerState())
    val state: StateFlow<RunningTrackerState> = _state

    fun beginSession(startInfo: RunningStartInfo, startTimeMillis: Long = System.currentTimeMillis()) {
        _state.value = RunningTrackerState(
            startInfo = startInfo,
            startTimeMillis = startTimeMillis,
            isSessionActive = true,
            isCollecting = true,
            locationLabel = AppText.str(R.string.trk_wait_location),
        )
    }

    /** 开启模板录制会话：不涉及跑步 startInfo。 */
    fun beginTemplateSession(startTimeMillis: Long = System.currentTimeMillis()) {
        _state.value = RunningTrackerState(
            startInfo = null,
            startTimeMillis = startTimeMillis,
            isSessionActive = true,
            isCollecting = true,
            locationLabel = AppText.str(R.string.trk_wait_location),
        )
    }

    fun onServiceStarted() {
        _state.value = _state.value.copy(
            isCollecting = true,
            locationLabel = AppText.str(R.string.trk_getting_gps),
            errorMessage = null,
        )
    }

    fun onLocationUpdate(location: Location) {
        val current = _state.value
        if (!current.isSessionActive) return
        // 官方暂停那颗按钮是 clearWatch —— 暂停期间一个点都不收，时长也不走。我们照这个来。
        if (current.isPaused) return

        val point = TrackPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )

        val previous = current.lastPoint
        val segmentMeters = if (previous == null) {
            0.0
        } else {
            GeoDistance.metersBetween(previous.latitude, previous.longitude, point.latitude, point.longitude)
        }

        val acceptedSegment = when {
            previous == null -> 0.0
            segmentMeters < 1.5 -> 0.0
            segmentMeters > 120.0 -> 0.0
            else -> segmentMeters
        }

        val nextPoints = if (previous == null || acceptedSegment > 0.0) {
            current.points + point
        } else {
            current.points
        }

        _state.value = current.copy(
            totalDistanceMeters = current.totalDistanceMeters + acceptedSegment,
            points = nextPoints,
            lastPoint = point,
            locationLabel = if (nextPoints.size >= 2) AppText.str(R.string.trk_ok) else AppText.str(R.string.trk_fixed),
            errorMessage = null,
        )
    }

    fun onLocationError(message: String) {
        _state.value = _state.value.copy(
            isCollecting = false,
            locationLabel = AppText.str(R.string.trk_failed),
            errorMessage = message,
        )
    }

    /** 暂停：不收点、时长不走，跟官方 clearWatch + clearInterval 一个效果 */
    fun pause() {
        val current = _state.value
        if (!current.isSessionActive || current.isPaused) return
        _state.value = current.copy(
            isPaused = true,
            pausedAtMillis = System.currentTimeMillis(),
            locationLabel = "已暂停",
        )
    }

    /** 继续：把暂停那一段累进 pausedTotalMillis，起点不动 */
    fun resume() {
        val current = _state.value
        if (!current.isPaused) return
        val now = System.currentTimeMillis()
        _state.value = current.copy(
            isPaused = false,
            pausedAtMillis = null,
            pausedTotalMillis = current.pausedTotalMillis +
                (now - (current.pausedAtMillis ?: now)).coerceAtLeast(0L),
        )
    }

    /** 这一场真正在跑多少秒（扣掉暂停） */
    fun elapsedSeconds(nowMillis: Long = System.currentTimeMillis()): Long {
        val current = _state.value
        if (current.startTimeMillis <= 0L) return 0L
        val pausedExtra = if (current.isPaused) (current.pausedAtMillis?.let { nowMillis - it } ?: 0L) else 0L
        return ((nowMillis - current.startTimeMillis - current.pausedTotalMillis - pausedExtra) / 1000L)
            .coerceAtLeast(0L)
    }

    fun stopCollecting() {
        _state.value = _state.value.copy(
            isCollecting = false,
            locationLabel = if (_state.value.points.isEmpty()) AppText.str(R.string.trk_stopped) else AppText.str(R.string.trk_locked),
        )
    }

    fun snapshotForUpload(): RunningSnapshot? {
        val current = _state.value
        val startInfo = current.startInfo ?: return null
        val durationSeconds = elapsedSeconds()
        return RunningSnapshot(
            startInfo = startInfo,
            startTimeMillis = current.startTimeMillis,
            durationSeconds = durationSeconds,
            distanceMeters = current.totalDistanceMeters,
            points = current.points.ifEmpty { listOfNotNull(current.lastPoint) },
        )
    }

    fun completeSession() {
        _state.value = RunningTrackerState()
    }

    fun cancelSession() {
        _state.value = RunningTrackerState()
    }
}
