package com.linxin.feature.schedule.data

import android.os.SystemClock
import com.linxin.core.auth.TokenManager
import com.linxin.core.network.CshRetrofit
import com.linxin.feature.schedule.domain.Course
import com.linxin.feature.schedule.domain.ScheduleData
import com.linxin.feature.schedule.domain.WeekInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import retrofit2.Retrofit
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val api: ScheduleApi,
    private val tokenManager: TokenManager,
) {
    private val weekMutex = Mutex()
    private var weekCached: Pair<Long, WeekInfo>? = null

    private val coursesMutex = Mutex()
    private val coursesCached = mutableMapOf<String, Pair<Long, ScheduleData>>()

    private fun elapsed(): Long = SystemClock.elapsedRealtime()

    private companion object {
        // TTL 只要够吃掉冷启动那一瞬间的并发重复请求就行，再长就成了"刷新了却没变"
        const val CACHE_TTL_MS = 60_000L
        const val MAX_CACHED_WEEKS = 32
    }

    suspend fun getWeekInfo(): Result<WeekInfo> = weekMutex.withLock {
        weekCached?.takeIf { elapsed() - it.first < CACHE_TTL_MS }
            ?.let { return@withLock Result.success(it.second) }
        fetchWeekInfo().also { result ->
            result.getOrNull()?.let { weekCached = elapsed() to it }
        }
    }

    private suspend fun fetchWeekInfo(): Result<WeekInfo> {
        return try {
            val response = api.getWeekList()
            val data = response.data
            val rows = response.rows

            if (data == null) {
                Result.failure(Exception("获取周次信息失败"))
            } else {
                Result.success(
                    WeekInfo(
                        currentWeek = data.week?.toIntOrNull() ?: 1,
                        totalWeeks = rows?.size ?: 18,
                        schoolYear = data.schoolYear ?: "",
                        schoolTerm = data.schoolTerm ?: "",
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapErrorMessage("获取周次", e), e))
        }
    }

    suspend fun getCourses(
        schoolYear: String,
        schoolTerm: String,
        week: Int,
    ): Result<ScheduleData> {
        // 缓存键带上学号：换账号登录后不能把上一个人的课表在 60 秒内发给新账号
        val userCode = tokenManager.getUserCode().orEmpty()
        if (userCode.isBlank()) {
            return Result.failure(Exception("登录信息已失效，请重新登录"))
        }
        return coursesMutex.withLock {
            val key = "$userCode|$schoolYear|$schoolTerm|$week"
            coursesCached[key]?.takeIf { elapsed() - it.first < CACHE_TTL_MS }
                ?.let { return@withLock Result.success(it.second) }
            fetchCourses(userCode, schoolYear, schoolTerm, week).also { result ->
                result.getOrNull()?.let {
                    if (coursesCached.size >= MAX_CACHED_WEEKS) coursesCached.clear()
                    coursesCached[key] = elapsed() to it
                }
            }
        }
    }

    private suspend fun fetchCourses(
        userCode: String,
        schoolYear: String,
        schoolTerm: String,
        week: Int,
    ): Result<ScheduleData> {
        return try {
            val response = api.getTimeTable(
                userCode = userCode,
                schoolYear = schoolYear,
                schoolTerm = schoolTerm,
                week = week.toString(),
            )

            val courses = mutableListOf<Course>()
            val weekDates = mutableMapOf<Int, String>()
            response.rows?.forEach { day ->
                val dayOfWeek = day.xq?.toIntOrNull() ?: return@forEach
                if (day.rq != null) weekDates[dayOfWeek] = day.rq
                day.kcVoList?.forEach { vo ->
                    val startSection = vo.ksjc?.toIntOrNull() ?: return@forEach
                    val endSection = vo.jsjc?.toIntOrNull() ?: startSection
                    courses.add(
                        Course(
                            name = vo.kcmc ?: "",
                            startSection = startSection,
                            endSection = endSection,
                            room = vo.jsmc ?: "",
                            teacher = vo.teacherName ?: "",
                            dayOfWeek = dayOfWeek,
                        )
                    )
                }
            }

            Result.success(ScheduleData(courses = courses, weekDates = weekDates))
        } catch (e: Exception) {
            Result.failure(Exception(mapErrorMessage("加载课表", e), e))
        }
    }

    private fun mapErrorMessage(action: String, error: Exception): String {
        return when (error) {
            is HttpException -> when {
                error.code() == 401 -> "登录已失效，请重新登录"
                error.code() >= 500 -> "${action}接口暂时异常，请稍后重试"
                else -> "${action}失败（HTTP ${error.code()}）"
            }

            is IOException -> "网络异常，请检查连接后重试"
            else -> error.message ?: "${action}失败"
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ScheduleModule {

    @Provides
    @Singleton
    fun provideScheduleApi(@CshRetrofit retrofit: Retrofit): ScheduleApi =
        retrofit.create(ScheduleApi::class.java)
}
