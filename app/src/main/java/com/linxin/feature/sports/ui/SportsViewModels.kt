package com.linxin.feature.sports.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.feature.sports.data.SportsRepository
import com.linxin.feature.sports.data.SportsRouteStore
import com.linxin.feature.sports.domain.RecordDetail
import com.linxin.feature.sports.domain.RecordRow
import com.linxin.feature.sports.domain.RunRules
import com.linxin.feature.sports.domain.SportsRoute
import com.linxin.feature.sports.domain.WgsPoint
import com.linxin.feature.sports.service.SportsRunEngine
import com.linxin.feature.sports.service.SportsRunService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** 开始运动那一屏：读约束、挑模拟线、开场 */
data class StartUiState(
    val rules: RunRules? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val routes: List<SportsRoute> = emptyList(),
    val armedRoute: SportsRoute? = null,
    val paceKmh: Double = 7.0,
    val isStarting: Boolean = false,
    val startError: String? = null,
)

@HiltViewModel
class SportsStartViewModel @Inject constructor(
    private val repository: SportsRepository,
    private val routeStore: SportsRouteStore,
    private val engine: SportsRunEngine,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartUiState())
    val uiState: StateFlow<StartUiState> = _uiState.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            routeStore.routes.collect { list ->
                _uiState.update { state ->
                    state.copy(
                        routes = list,
                        armedRoute = state.armedRoute?.let { armed -> list.firstOrNull { it.id == armed.id } },
                    )
                }
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.rules().fold(
                onSuccess = { rules -> _uiState.update { it.copy(rules = rules, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    fun armRoute(route: SportsRoute) {
        _uiState.update { it.copy(armedRoute = if (it.armedRoute?.id == route.id) null else route) }
    }

    fun setPace(kmh: Double) {
        _uiState.update { it.copy(paceKmh = kmh) }
    }

    /** 开场：先跟服务器要一次会话标识，再让引擎开始收点。失败就不开跑，避免"跑了但交不了" */
    fun startRun(onStarted: () -> Unit) {
        val rules = _uiState.value.rules
        if (rules == null) {
            _uiState.update { it.copy(startError = "跑步任务还没读到，先下拉刷新一次") }
            return
        }
        if (_uiState.value.isStarting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStarting = true, startError = null) }
            repository.startSession(rules).fold(
                onSuccess = { session ->
                    SportsRunService.begin(
                        context = application,
                        engine = engine,
                        session = session,
                        route = _uiState.value.armedRoute,
                        paceKmh = _uiState.value.paceKmh,
                    )
                    _uiState.update { it.copy(isStarting = false) }
                    onStarted()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isStarting = false, startError = e.message) }
                },
            )
        }
    }
}

/** 运动中那一屏 */
@HiltViewModel
class SportsRunViewModel @Inject constructor(
    private val repository: SportsRepository,
    private val engine: SportsRunEngine,
    private val application: Application,
) : ViewModel() {

    val state: StateFlow<SportsRunEngine.State> = engine.state

    private val _finish = MutableStateFlow(FinishState())
    val finish: StateFlow<FinishState> = _finish.asStateFlow()

    data class FinishState(
        val uploading: Boolean = false,
        val message: String? = null,
        val recordId: String? = null,
        val tooShort: Boolean = false,
    )

    fun pause() = engine.pause()

    fun resume() = engine.resume()

    fun togglePaused() {
        if (engine.state.value.paused) engine.resume() else engine.pause()
    }

    /** 结束 = 收点 + 交这一场；服务器回 id 才算真落库 */
    fun finishRun(onDone: () -> Unit) {
        if (_finish.value.uploading) return
        val seconds = engine.state.value.elapsedSeconds()
        val km = engine.state.value.distanceKm
        // 官方那条客户端硬校验：不到 0.8 公里直接判无效，别白跑一趟服务器
        if (km < 0.8 && seconds > 5) {
            _finish.value = FinishState(tooShort = true, message = "本次运动低于0.8公里，故无效")
        }
        val draft = engine.finish()
        SportsRunService.stop(application)
        if (draft == null) {
            _finish.value = FinishState(message = "一个点都没收到，这一场不算")
            onDone()
            return
        }
        viewModelScope.launch {
            _finish.value = FinishState(uploading = true, tooShort = draft.mileKm < 0.8)
            repository.submit(draft).fold(
                onSuccess = { id ->
                    _finish.value = FinishState(recordId = id, message = "上传成功")
                    onDone()
                },
                onFailure = { e ->
                    _finish.value = FinishState(message = e.message ?: "上传失败")
                    onDone()
                },
            )
        }
    }

    fun discardRun() {
        engine.cancel()
        SportsRunService.stop(application)
        _finish.value = FinishState()
    }

    fun clearFinish() {
        _finish.value = FinishState()
    }
}

/** 运动记录列表 */
data class RecordsUiState(
    val month: YearMonth = YearMonth.now(),
    val tab: Int = 0,
    val rows: List<RecordRow> = emptyList(),
    val totals: com.linxin.feature.sports.domain.RecordTotals? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val monthLabel: String get() = "${month.year}年${month.monthValue}月"
    val queryDate: String get() = month.format(MONTH)

    companion object {
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.CHINA)
    }
}

@HiltViewModel
class SportsRecordsViewModel @Inject constructor(
    private val repository: SportsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val month = _uiState.value.month
        val tab = _uiState.value.tab
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val page = repository.records(month.format(RecordsUiState.MONTH), tab)
            val totals = repository.totals()
            _uiState.update { current ->
                if (current.month != month || current.tab != tab) current else current.copy(
                    rows = page.getOrNull()?.rows ?: current.rows,
                    totals = totals.getOrNull() ?: current.totals,
                    isLoading = false,
                    error = page.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun selectTab(tab: Int) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab) }
        reload()
    }

    fun shiftMonth(delta: Int) {
        val next = _uiState.value.month.plusMonths(delta.toLong())
        if (next.isAfter(YearMonth.now())) return
        _uiState.update { it.copy(month = next) }
        reload()
    }
}

/** 运动明细 */
@HiltViewModel
class SportsDetailViewModel @Inject constructor(
    private val repository: SportsRepository,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    private val checkId: String = savedStateHandle.get<String>("checkId").orEmpty()

    data class DetailState(
        val detail: RecordDetail? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(DetailState())
    val uiState: StateFlow<DetailState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        if (checkId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "缺少记录编号") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.detail(checkId).fold(
                onSuccess = { detail -> _uiState.update { it.copy(detail = detail, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }
}

/** 考勤维护记录 */
@HiltViewModel
class SportsAppealsViewModel @Inject constructor(
    private val repository: SportsRepository,
) : ViewModel() {

    data class AppealsState(
        val month: YearMonth = YearMonth.now(),
        val tab: Int = 0,
        val auditState: String = "",
        val page: com.linxin.feature.sports.domain.AppealPage? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
    ) {
        val monthLabel: String get() = "${month.year}年${month.monthValue}月"
    }

    private val _uiState = MutableStateFlow(AppealsState())
    val uiState: StateFlow<AppealsState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.appeals(
                queryDate = snapshot.month.format(RecordsUiState.MONTH),
                queryType = snapshot.tab,
                auditState = snapshot.auditState,
            )
            _uiState.update { current ->
                current.copy(
                    page = result.getOrNull() ?: current.page,
                    isLoading = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun selectTab(tab: Int) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab) }
        reload()
    }

    fun selectAudit(auditState: String) {
        if (_uiState.value.auditState == auditState) return
        _uiState.update { it.copy(auditState = auditState) }
        reload()
    }

    fun shiftMonth(delta: Int) {
        val next = _uiState.value.month.plusMonths(delta.toLong())
        if (next.isAfter(YearMonth.now())) return
        _uiState.update { it.copy(month = next) }
        reload()
    }
}

/** 地图规划路线那一屏 */
@HiltViewModel
class SportsPlannerViewModel @Inject constructor(
    private val store: SportsRouteStore,
) : ViewModel() {

    data class PlannerState(
        val points: List<WgsPoint> = emptyList(),
        val name: String = "",
        val saved: Boolean = false,
        val error: String? = null,
    ) {
        val distanceMeters: Double
            get() {
                var sum = 0.0
                for (i in 1 until points.size) {
                    sum += com.linxin.feature.sports.SportsCoords.metersBetween(
                        points[i - 1].latitude, points[i - 1].longitude,
                        points[i].latitude, points[i].longitude,
                    )
                }
                return sum
            }
    }

    private val _uiState = MutableStateFlow(PlannerState())
    val uiState: StateFlow<PlannerState> = _uiState.asStateFlow()

    fun add(point: WgsPoint) {
        _uiState.update { it.copy(points = it.points + point, saved = false) }
    }

    fun undo() {
        _uiState.update { it.copy(points = it.points.dropLast(1), saved = false) }
    }

    fun clear() {
        _uiState.update { it.copy(points = emptyList(), saved = false) }
    }

    fun rename(text: String) {
        _uiState.update { it.copy(name = text) }
    }

    fun save() {
        val state = _uiState.value
        if (state.points.size < 2) {
            _uiState.update { it.copy(error = "先在图上点两个点") }
            return
        }
        if (state.distanceMeters < 50.0) {
            _uiState.update { it.copy(error = "这条线太短，至少 50 米") }
            return
        }
        viewModelScope.launch {
            store.save(state.name.ifBlank { "路线 ${System.currentTimeMillis() / 1000 % 10000}" }, state.points)
            _uiState.update { it.copy(saved = true, error = null) }
        }
    }
}
