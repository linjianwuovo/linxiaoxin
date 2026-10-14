package com.linxin.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val internalFeed: InternalGpsFeed,
) {
    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** 内部喂点开着时不需要真定位权限（跑步那条链要照常跑得起来） */
    fun hasLocationPermission(): Boolean {
        if (internalFeed.isOn.value) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 获取最后已知位置（快速，可能为null） */
    fun getLastKnownLocation(): Location? {
        internalFeed.snapshot()?.let { return it }
        if (!hasLocationPermission()) return null
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * 持续获取位置，返回 Flow<Location>。
     *
     * 内部喂点是**并着收**的，不是"订阅那一刻没开就整条流都收不到"：
     * 2026-10-14 他先开跑步、再开喂点，跑步页就永远停在「正在获取 GPS · 0 个轨迹点」——
     * 因为流是在订阅时决定走真 GPS 还是走喂点的。现在两个来源同时吐，谁先开都一样。
     */
    fun locationUpdates(intervalMs: Long = 3000L, minDistanceM: Float = 2f): Flow<Location> {
        return callbackFlow {
            if (!hasLocationPermission() && !internalFeed.isOn.value) {
                close(SecurityException("缺少定位权限"))
                return@callbackFlow
            }

            val listener = LocationListener { location -> trySend(location) }

            try {
                if (hasRealGpsPermission()) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        intervalMs,
                        minDistanceM,
                        listener,
                        Looper.getMainLooper(),
                    )
                }
            } catch (_: SecurityException) {
                // 权限被收回：喂点还开着就继续靠喂点，别整条流直接死掉
            }

            // 喂点固定一秒一次：调用方要的 3 秒是问真 GPS 的频率，
            // 拿它当采样间隔会把记录点变成 3 秒一个，比真跑的轨迹稀一大截
            val pump = launch {
                while (isActive) {
                    internalFeed.snapshot()?.let { trySend(it) }
                    delay(FEED_PUMP_INTERVAL_MS)
                }
            }

            awaitClose {
                pump.cancel()
                locationManager.removeUpdates(listener)
            }
        }
    }

    private companion object {
        const val FEED_PUMP_INTERVAL_MS = 1000L
    }

    /** 只问系统权限，不含"喂点开着也算"那层放宽 */
    private fun hasRealGpsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
