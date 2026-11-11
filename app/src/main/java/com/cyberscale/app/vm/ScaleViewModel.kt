package com.cyberscale.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cyberscale.app.ble.BleScanController
import com.cyberscale.app.ble.BleUiState
import com.cyberscale.app.ble.MeasurementStabilizer
import com.cyberscale.app.ble.ScaleFrame
import com.cyberscale.app.data.AppContainer
import com.cyberscale.app.data.AppTab
import com.cyberscale.app.data.ProfileDraft
import com.cyberscale.app.data.RecordStats
import com.cyberscale.app.data.ScaleRepository
import com.cyberscale.app.data.SettingsStore
import com.cyberscale.app.data.TrendRange
import com.cyberscale.app.data.UserEntity
import com.cyberscale.app.data.WeightRecordEntity
import com.cyberscale.app.data.WeightUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 界面唯一的聚合状态。所有内容都来自本地 Room 或 BLE 广播，没有任何网络请求。 */
data class AppUiState(
    val users: List<UserEntity> = emptyList(),
    val activeUser: UserEntity? = null,
    val records: List<WeightRecordEntity> = emptyList(),
    val trend: List<WeightRecordEntity> = emptyList(),
    val stats: RecordStats = RecordStats(),
    val ble: BleUiState = BleUiState(),
    val liveFrame: ScaleFrame? = null,
    val liveWeight: Double? = null,
    val lockedWeight: Double? = null,
    val goalProgress: Float = 0f,
    val tab: AppTab = AppTab.MEASURE,
    val range: TrendRange = TrendRange.WEEK,
    val unit: WeightUnit = WeightUnit.KG
)

/** 一次成功落库的事件，UI 用它弹 Snackbar（含"改归属"动作）。 */
data class SaveEvent(
    val recordId: Long,
    val weightKg: Double,
    val userId: Long,
    val userName: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScaleViewModel(
    private val repository: ScaleRepository,
    private val controller: BleScanController,
    private val settings: SettingsStore
) : ViewModel() {

    private val stabilizer = MeasurementStabilizer()

    private val _tab = MutableStateFlow(AppTab.MEASURE)
    private val _range = MutableStateFlow(TrendRange.WEEK)
    private val _live = MutableStateFlow<ScaleFrame?>(null)
    private val _locked = MutableStateFlow<Double?>(null)

    private val _saveEvent = MutableSharedFlow<SaveEvent>(extraBufferCapacity = 8)
    val saveEvent: SharedFlow<SaveEvent> = _saveEvent.asSharedFlow()

    /** 当前成员的记录的 Flow：切换成员会自动重新订阅（flatMapLatest）。 */
    private val recordsFlow = repository.observeActiveUser().flatMapLatest { user ->
        if (user == null) flowOf(emptyList<WeightRecordEntity>())
        else repository.observeRecords(user.id)
    }

    private data class Core(
        val users: List<UserEntity>,
        val active: UserEntity?,
        val records: List<WeightRecordEntity>
    )

    private val core: StateFlow<Core> = combine(
        repository.observeUsers(),
        repository.observeActiveUser(),
        recordsFlow
    ) { u, a, r -> Core(u, a, r) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Core(emptyList(), null, emptyList())
        )

    private data class BlePart(val ble: BleUiState, val live: ScaleFrame?, val locked: Double?)

    private val blePart: StateFlow<BlePart> = combine(
        controller.state, _live, _locked
    ) { b, l, k -> BlePart(b, l, k) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BlePart(BleUiState(), null, null)
        )

    private data class UiPart(val tab: AppTab, val range: TrendRange, val unit: WeightUnit)

    private val uiPart: StateFlow<UiPart> = combine(
        _tab, _range, settings.unit
    ) { t, r, u -> UiPart(t, r, u) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiPart(AppTab.MEASURE, TrendRange.WEEK, WeightUnit.KG)
        )

    val uiState: StateFlow<AppUiState> = combine(core, blePart, uiPart) { c, b, u ->
        AppUiState(
            users = c.users,
            activeUser = c.active,
            records = c.records,
            trend = repository.trendPoints(c.records, u.range),
            stats = repository.stats(c.records),
            ble = b.ble,
            liveFrame = b.live,
            liveWeight = b.live?.takeIf { it.isValidWeight }?.weightKg,
            lockedWeight = b.locked,
            goalProgress = repository.goalProgress(c.active, c.records.firstOrNull()?.weightKg),
            tab = u.tab,
            range = u.range,
            unit = u.unit
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultUser()
            controller.start()
        }

        viewModelScope.launch {
            controller.frames.collect { frame ->
                _live.value = frame
                if (frame.isFinal && frame.isValidWeight) {
                    _locked.value = frame.weightKg
                }
                val stable = stabilizer.onFrame(frame) ?: return@collect
                val user = repository.observeActiveUser().first()
                    ?: repository.observeUsers().first().firstOrNull()
                    ?: return@collect
                val id = repository.saveMeasurement(stable, user)
                _saveEvent.tryEmit(
                    SaveEvent(
                        recordId = id,
                        weightKg = stable.weightKg,
                        userId = user.id,
                        userName = user.name
                    )
                )
            }
        }
    }

    // ---------------- UI 交互 ----------------

    fun selectTab(tab: AppTab) { _tab.value = tab }
    fun selectRange(range: TrendRange) { _range.value = range }
    fun setUnit(unit: WeightUnit) = settings.setUnit(unit)

    fun toggleScan() {
        if (controller.state.value.scanning) controller.stop() else controller.start()
    }

    fun activateUser(id: Long) = viewModelScope.launch {
        repository.activate(id)
        stabilizer.reset()
    }

    fun addUser(draft: ProfileDraft) = viewModelScope.launch {
        repository.addUser(
            UserEntity(
                name = draft.name,
                colorArgb = draft.colorArgb,
                heightCm = draft.heightCm,
                birthYear = 0,
                gender = draft.gender,
                goalWeightKg = draft.goalWeightKg,
                startWeightKg = 0.0,
                createdAt = System.currentTimeMillis(),
                isActive = false,
                birthDate = draft.birthDate
            )
        )
    }

    /** 保存档案后立即用新档案补算该成员的历史记录（v1 遗留记录没有体成分指标）。 */
    fun updateUser(user: UserEntity) = viewModelScope.launch {
        repository.updateUser(user)
        repository.backfillMetrics(user.id)
    }

    /** 档案页手动触发：按当前档案补算仍缺指标的记录。 */
    fun backfill() = viewModelScope.launch {
        val id = uiState.value.activeUser?.id ?: return@launch
        repository.backfillMetrics(id)
    }

    fun deleteUser(user: UserEntity) = viewModelScope.launch { repository.deleteUser(user) }

    fun deleteRecord(record: WeightRecordEntity) = viewModelScope.launch {
        repository.deleteRecord(record)
    }

    fun clearRecords() = viewModelScope.launch {
        val id = uiState.value.activeUser?.id ?: return@launch
        repository.clearRecords(id)
    }

    /** Snackbar 的"改归属"：把刚保存的那条记录改挂到另一个成员。 */
    fun reassign(recordId: Long, newUserId: Long) = viewModelScope.launch {
        repository.reassign(recordId, newUserId)
    }
}

class ScaleViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        if (modelClass.isAssignableFrom(ScaleViewModel::class.java)) {
            ScaleViewModel(container.repository, container.bleController, container.settings) as T
        } else {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
}
