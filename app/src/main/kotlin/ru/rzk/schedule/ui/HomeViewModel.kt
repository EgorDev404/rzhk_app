package ru.rzk.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.rzk.schedule.ScheduleApp
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.data.FailureKind
import ru.rzk.schedule.data.FetchOutcome
import ru.rzk.schedule.data.LoadStage
import ru.rzk.schedule.data.Snapshot
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

sealed interface DayContent {
    data class Loading(val stage: LoadStage) : DayContent
    data class Ready(val pages: List<File>, val sha: String, val checkedAt: Instant, val stale: Boolean) : DayContent
    data object NotPublished : DayContent
    data class Failed(val kind: FailureKind) : DayContent
}

data class DayState(
    val content: DayContent = DayContent.Loading(LoadStage.Connecting),
    val refreshing: Boolean = false,
)

sealed interface UiEvent {
    data class Refreshed(val changed: Boolean) : UiEvent
    data object StillOffline : UiEvent
    data object StillNotPublished : UiEvent
}

/**
 * Какие дни показывать на главном экране.
 *
 *  - пятница и суббота — три дня, включая понедельник: колледж часто выкладывает расписание
 *    на понедельник заранее, и пользователь должен увидеть его сразу, не дожидаясь выходных;
 *  - воскресенье — сразу понедельник и вторник (сегодня занятий не бывает);
 *  - остальные дни — сегодня и завтра.
 *
 * Воскресенье никогда не попадает в список: это гарантированно не учебный день.
 * День из [custom] (выбор в календаре или переход из уведомления) добавляется в конец,
 * если его ещё нет в списке.
 */
internal fun upcomingDays(today: LocalDate, custom: LocalDate?): List<LocalDate> {
    val wanted = when (today.dayOfWeek) {
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> 3
        DayOfWeek.SUNDAY -> 2
        else -> 2
    }
    return buildList {
        var cursor = today
        while (size < wanted) {
            if (cursor.dayOfWeek != DayOfWeek.SUNDAY) add(cursor)
            cursor = cursor.plusDays(1)
        }
        if (custom != null && custom !in this) add(custom)
    }
}

class HomeViewModel(app: ScheduleApp) : ViewModel() {
    private val repository = app.repository

    private val today = MutableStateFlow(Dates.today())
    private val custom = MutableStateFlow<LocalDate?>(null)

    /** Дни, между которыми можно листать на главном экране. */
    val days: StateFlow<List<LocalDate>> = combine(today, custom) { t, c ->
        upcomingDays(t, c)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, upcomingDays(Dates.today(), null))

    private val _states = MutableStateFlow<Map<LocalDate, DayState>>(emptyMap())
    val states: StateFlow<Map<LocalDate, DayState>> = _states.asStateFlow()

    /** День, который нужно показать, как только главный экран появится (push, календарь, deep link). */
    private val _pendingDay = MutableStateFlow<LocalDate?>(null)
    val pendingDay: StateFlow<LocalDate?> = _pendingDay.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val jobs = HashMap<LocalDate, Job>() // только из главного потока

    /** Вызывается при каждом возвращении в приложение: дата могла смениться, расписание — обновиться. */
    fun onResume() {
        val now = Dates.today()
        if (now != today.value) today.value = now
        days.value.forEach { load(it, force = false, userInitiated = false) }
    }

    /** Потянули экран вниз: игнорируем кэш и качаем файл заново. */
    fun refresh(day: LocalDate) = load(day, force = true, userInitiated = true)

    /** Выбор дня в календаре или переход из уведомления. */
    fun showDay(day: LocalDate) {
        if (day !in days.value) custom.value = day
        load(day, force = false, userInitiated = false)
        _pendingDay.value = day
    }

    /** Главный экран обработал [pendingDay] и может его сбросить. */
    fun consumePendingDay() {
        _pendingDay.value = null
    }

    // ---------------------------------------------------------------------------------------------

    private fun load(day: LocalDate, force: Boolean, userInitiated: Boolean) {
        val running = jobs[day]
        if (running?.isActive == true && !force) return
        running?.cancel()

        jobs[day] = viewModelScope.launch {
            val self = coroutineContext[Job]
            try {
                val shown = _states.value[day]?.content
                val cached = repository.cached(day)

                // 1. Мгновенно показываем то, что уже лежит на диске, — без пустого экрана.
                if (shown !is DayContent.Ready) {
                    if (cached != null) showSnapshot(day, cached, stale = false)
                    else if (shown == null) setContent(day, DayContent.Loading(LoadStage.Connecting))
                }
                if (force) setRefreshing(day, true)

                // 2. Тихо сверяемся с сайтом.
                val outcome = repository.fetch(day, force) { stage ->
                    if (_states.value[day]?.content !is DayContent.Ready &&
                        _states.value[day]?.content !is DayContent.NotPublished
                    ) {
                        setContent(day, DayContent.Loading(stage))
                    }
                }

                when (outcome) {
                    is FetchOutcome.Ok -> {
                        val current = _states.value[day]?.content as? DayContent.Ready
                        if (current == null || current.sha != outcome.snapshot.sha256 || current.stale != outcome.stale) {
                            showSnapshot(day, outcome.snapshot, outcome.stale)
                        }
                        if (!outcome.stale) repository.markSeen(day, outcome.snapshot.sha256)
                        if (userInitiated) {
                            _events.tryEmit(if (outcome.stale) UiEvent.StillOffline else UiEvent.Refreshed(outcome.changed))
                        }
                    }
                    FetchOutcome.NotPublished -> {
                        setContent(day, DayContent.NotPublished)
                        if (userInitiated) _events.tryEmit(UiEvent.StillNotPublished)
                    }
                    is FetchOutcome.Failed -> setContent(day, DayContent.Failed(outcome.kind))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setContent(day, DayContent.Failed(FailureKind.BadFile))
            } finally {
                // не сбрасываем индикатор, если задачу заменила более новая
                if (jobs[day] === self) setRefreshing(day, false)
            }
        }
    }

    private suspend fun showSnapshot(day: LocalDate, snapshot: Snapshot, stale: Boolean) {
        if (_states.value[day]?.content !is DayContent.Ready) setContent(day, DayContent.Loading(LoadStage.Rendering))
        val pages = repository.pages(snapshot)
        if (pages.isEmpty()) {
            setContent(day, DayContent.Failed(FailureKind.BadFile))
            return
        }
        setContent(day, DayContent.Ready(pages, snapshot.sha256, snapshot.checkedAt, stale))
    }

    private fun setContent(day: LocalDate, content: DayContent) = _states.update(day) { it.copy(content = content) }
    private fun setRefreshing(day: LocalDate, value: Boolean) = _states.update(day) { it.copy(refreshing = value) }

    private fun MutableStateFlow<Map<LocalDate, DayState>>.update(day: LocalDate, change: (DayState) -> DayState) {
        value = value + (day to change(value[day] ?: DayState()))
    }
}