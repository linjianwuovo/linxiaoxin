package com.linxin.feature.sports.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.linxin.feature.sports.SportsCoords
import com.linxin.feature.sports.domain.SportsRoute
import com.linxin.feature.sports.domain.WgsPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自己画的路线，存在 filesDir 的 `route_templates.json`。
 *
 * 文件名和 JSON 形状故意跟旧那套保持一致（`points/latitude/longitude` 那几个字段名没改），
 * 这样重写之后**他已经画好的那几条线还能用**，不用重画一遍。
 * 读的时候按"能读多少算多少"处理：字段缺了就跳过那条，不整表作废。
 */
@Singleton
class SportsRouteStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val gson = Gson()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _routes = MutableStateFlow<List<SportsRoute>>(emptyList())
    val routes: StateFlow<List<SportsRoute>> = _routes.asStateFlow()

    init {
        scope.launch { withContext(Dispatchers.IO) { _routes.value = read() } }
    }

    suspend fun save(name: String, points: List<WgsPoint>): SportsRoute {
        val route = SportsRoute(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "我的路线" },
            createdAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
            distanceMeters = distanceOf(points),
            points = points,
        )
        mutex.withLock {
            val list = read().toMutableList().apply { add(0, route) }
            withContext(Dispatchers.IO) { write(list) }
            _routes.value = list
        }
        return route
    }

    suspend fun rename(id: String, name: String) {
        mutex.withLock {
            val list = read().map { route ->
                if (route.id == id && name.isNotBlank()) {
                    route.copy(name = name, updatedAtMillis = System.currentTimeMillis())
                } else {
                    route
                }
            }
            withContext(Dispatchers.IO) { write(list) }
            _routes.value = list
        }
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            val list = read().filterNot { it.id == id }
            withContext(Dispatchers.IO) { write(list) }
            _routes.value = list
        }
    }

    fun distanceOf(points: List<WgsPoint>): Double {
        var sum = 0.0
        for (i in 1 until points.size) {
            sum += SportsCoords.metersBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            )
        }
        return sum
    }

    private fun read(): List<SportsRoute> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrDefault("")
        if (text.isBlank()) return emptyList()
        // 旧文件是一个对象包着列表，也兼容直接就是列表
        return runCatching {
            val root = JsonParser.parseString(text)
            val array = when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject && root.asJsonObject.get("templates")?.isJsonArray == true ->
                    root.asJsonObject.getAsJsonArray("templates")
                else -> null
            } ?: return emptyList()
            val type = object : TypeToken<List<LegacyRouteJson>>() {}.type
            (gson.fromJson<List<LegacyRouteJson>>(array.toString(), type) ?: emptyList())
                .mapNotNull { it.toModel() }
        }.getOrDefault(emptyList())
    }

    private fun write(list: List<SportsRoute>) {
        val type = object : TypeToken<List<LegacyRouteJson>>() {}.type
        val json = gson.toJson(list.map { LegacyRouteJson.of(it) }, type)
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(json)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    /** 跟旧存储共用的磁盘形状，字段名不许改 */
    private class LegacyRouteJson(
        val id: String? = null,
        val name: String? = null,
        val createdAtMillis: Long? = null,
        val updatedAtMillis: Long? = null,
        val totalDistanceMeters: Double? = null,
        val points: List<LegacyPointJson>? = null,
    ) {
        fun toModel(): SportsRoute? {
            val pts = points.orEmpty().mapNotNull { it.toPoint() }
            if (pts.size < 2) return null
            val meters = totalDistanceMeters?.takeIf { it > 0.0 } ?: SportsRoute0.distance(pts)
            return SportsRoute(
                id = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                name = name?.takeIf { it.isNotBlank() } ?: "我的路线",
                createdAtMillis = createdAtMillis ?: System.currentTimeMillis(),
                updatedAtMillis = updatedAtMillis ?: createdAtMillis ?: System.currentTimeMillis(),
                distanceMeters = meters,
                points = pts,
            )
        }

        companion object {
            fun of(route: SportsRoute) = LegacyRouteJson(
                id = route.id,
                name = route.name,
                createdAtMillis = route.createdAtMillis,
                updatedAtMillis = route.updatedAtMillis,
                totalDistanceMeters = route.distanceMeters,
                points = route.points.map { LegacyPointJson(it.latitude, it.longitude, it.atMillis) },
            )
        }
    }

    private class LegacyPointJson(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val timestampMillis: Long? = null,
    ) {
        fun toPoint(): WgsPoint? {
            val lat = latitude ?: return null
            val lng = longitude ?: return null
            if (!SportsCoords.isSane(lat, lng)) return null
            return WgsPoint(lat, lng, timestampMillis ?: System.currentTimeMillis())
        }
    }

    private object SportsRoute0 {
        fun distance(points: List<WgsPoint>): Double {
            var sum = 0.0
            for (i in 1 until points.size) {
                sum += SportsCoords.metersBetween(
                    points[i - 1].latitude, points[i - 1].longitude,
                    points[i].latitude, points[i].longitude,
                )
            }
            return sum
        }
    }

    private companion object {
        const val FILE_NAME = "route_templates.json"
    }
}
