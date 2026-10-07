package ru.rzk.schedule.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.rzk.schedule.data.Dates
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Что показать в полноэкранном просмотре. */
class ViewerRequest(val title: String, val pages: List<File>, val startPage: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    notificationsOn: Boolean,
    onOpenSettings: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenViewer: (ViewerRequest) -> Unit,
) {
    val days by vm.days.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { days.size }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDatePicker by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            fun say(text: String) {
                snackbar.currentSnackbarData?.dismiss()
                scope.launch { snackbar.showSnackbar(text) }
            }
            when (event) {
                is UiEvent.ShowDay -> {
                    val list = vm.days.first { event.day in it }
                    pagerState.animateScrollToPage(list.indexOf(event.day))
                }
                is UiEvent.Refreshed ->
                    say(if (event.changed) "Расписание обновлено" else "Без изменений — у вас актуальная версия")
                UiEvent.StillOffline -> say("Сайт недоступен — показана сохранённая копия")
                UiEvent.StillNotPublished -> say("Пока не выложено")
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Расписание") },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = "Выбрать другой день")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            DayTabs(
                days = days,
                states = states,
                selected = pagerState.targetPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> days.getOrNull(index)?.toString() ?: index.toString() },
                beyondViewportPageCount = 1,
            ) { index ->
                val day = days.getOrNull(index) ?: return@HorizontalPager
                val state = states[day] ?: DayState()
                DayPage(
                    day = day,
                    state = state,
                    notificationsOn = notificationsOn,
                    onRefresh = { vm.refresh(day) },
                    onOpenPage = { page ->
                        val ready = state.content as? DayContent.Ready ?: return@DayPage
                        onOpenViewer(ViewerRequest(Dates.full(day), ready.pages, page))
                    },
                    onEnableNotifications = onEnableNotifications,
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = days.first().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        vm.showDay(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("Показать") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) { DatePicker(state = pickerState) }
    }
}

/** Крупные переключатели «Сегодня / Завтра / …» с индикатором, есть ли расписание на этот день. */
@Composable
private fun DayTabs(days: List<LocalDate>, states: Map<LocalDate, DayState>, selected: Int, onSelect: (Int) -> Unit) {
    val today = Dates.today()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = index == selected
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "tabContainer",
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabContent",
            )
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(20.dp),
                color = container,
                contentColor = content,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Dates.title(day, today), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Dates.weekdayShort(day)}, ${Dates.dayMonth(day)}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    TabStatus(states[day]?.content)
                }
            }
        }
    }
}

@Composable
private fun TabStatus(content: DayContent?) {
    val (icon, text) = when (content) {
        is DayContent.Ready -> Icons.Rounded.CheckCircle to "есть"
        DayContent.NotPublished -> Icons.Rounded.EventBusy to "нет"
        is DayContent.Failed -> Icons.Rounded.CloudOff to "ошибка"
        else -> null to "…"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
