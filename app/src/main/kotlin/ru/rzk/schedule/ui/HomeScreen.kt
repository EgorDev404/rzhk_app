package ru.rzk.schedule.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Bookmarks
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.rzk.schedule.app
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.ui.theme.FrostCorners
import ru.rzk.schedule.ui.theme.Garland
import ru.rzk.schedule.ui.theme.LocalNewYear
import ru.rzk.schedule.ui.theme.LocalWinter
import ru.rzk.schedule.ui.theme.NewYearCountdown
import ru.rzk.schedule.ui.theme.SnowFall
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Что показать в полноэкранном просмотре. [day] передаётся, чтобы прямо из просмотра можно было добавить день в закладки. */
class ViewerRequest(
    val title: String,
    val pages: List<File>,
    val startPage: Int,
    val day: LocalDate? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    notificationsOn: Boolean,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenViewer: (ViewerRequest) -> Unit,
) {
    val context = LocalContext.current
    val bookmarksStore = remember { context.app.bookmarks }
    val bookmarks by bookmarksStore.flow.collectAsStateWithLifecycle()

    val days by vm.days.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val pending by vm.pendingDay.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { days.size }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // Низкий экран (телефон в горизонтали): вместо большой шапки — компактная, с переключателем дней внутри.
    val compact = isCompactHeight()
    val scrollBehavior = if (compact) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var bookmarkDialogTarget by remember { mutableStateOf<Pair<LocalDate, List<File>>?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    // Показ конкретного дня (push или календарь). Работает и при первом появлении экрана.
    LaunchedEffect(pending, days) {
        val day = pending ?: return@LaunchedEffect
        val index = days.indexOf(day)
        if (index >= 0) {
            pagerState.animateScrollToPage(index, animationSpec = Motion.iosSpring())
            vm.consumePendingDay()
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            fun say(text: String) {
                snackbar.currentSnackbarData?.dismiss()
                scope.launch { snackbar.showSnackbar(text) }
            }
            when (event) {
                is UiEvent.Refreshed ->
                    say(if (event.changed) "Расписание обновлено" else "Без изменений — у вас актуальная версия")
                UiEvent.StillOffline -> say("Сайт недоступен — показана сохранённая копия")
                UiEvent.StillNotPublished -> say("Пока не выложено")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Иней и снегопад рисуются поверх фона и контента, но под заголовком и кнопками тулбара.
        LocalWinter.current?.let { winter ->
            Box(Modifier.fillMaxSize().zIndex(1f)) {
                FrostCorners(winter)
                SnowFall(winter)
            }
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // нижний отступ даёт навигационная панель снаружи
            topBar = {
                val barActions: @Composable RowScope.() -> Unit = {
                    // Значок закладок рядом с календарём появляется только тогда, когда есть хотя бы одна закладка.
                    AnimatedVisibility(
                        visible = bookmarks.isNotEmpty(),
                        enter = fadeIn(Motion.iosSnappySpring()) + scaleIn(Motion.iosSnappySpring(), initialScale = 0.7f),
                        exit = fadeOut(Motion.iosSnappySpring()) + scaleOut(Motion.iosSnappySpring(), targetScale = 0.7f),
                    ) {
                        IconButton(onClick = onOpenBookmarks) {
                            Icon(Icons.Rounded.Bookmarks, contentDescription = "Закладки")
                        }
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = "Выбрать другой день")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                    }
                }
                val fraction = if (compact) {
                    if (scrollBehavior.state.overlappedFraction > 0.01f) 1f else 0f
                } else {
                    scrollBehavior.state.collapsedFraction
                }
                val barColor by animateColorAsState(
                    lerp(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainer,
                        fraction,
                    ),
                    animationSpec = Motion.iosSpring(),
                    label = "topBarBg",
                )
                // Фон шапки лежит под снегом и гирляндой, а сам TopAppBar с заголовком и кнопками — поверх них (zIndex = 2f).
                Box {
                    Surface(color = barColor, modifier = Modifier.matchParentSize()) {}
                    LocalNewYear.current?.let { Garland(it) }
                    Box(Modifier.zIndex(2f)) {
                        if (compact) {
                            TopAppBar(
                                title = {
                                    DayTabs(
                                        days = days,
                                        states = states,
                                        bookmarkedDays = bookmarks.mapTo(HashSet()) { it.day },
                                        selected = pagerState.targetPage,
                                        onSelect = { index ->
                                            scope.launch { pagerState.animateScrollToPage(index, animationSpec = Motion.iosSpring()) }
                                        },
                                        compact = true,
                                    )
                                },
                                actions = barActions,
                                scrollBehavior = scrollBehavior,
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = Color.Transparent,
                                ),
                            )
                        } else {
                            LargeTopAppBar(
                                title = {
                                    // Подсветка заголовка стартует, когда расписание на сегодня загрузилось.
                                    val loaded = states[days.first()]?.content?.let { it !is DayContent.Loading } ?: false
                                    LaunchHighlightTitle("Расписание", ready = loaded)
                                },
                                actions = barActions,
                                scrollBehavior = scrollBehavior,
                                colors = TopAppBarDefaults.largeTopAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (!compact) {
                    DayTabs(
                        days = days,
                        states = states,
                        bookmarkedDays = bookmarks.mapTo(HashSet()) { it.day },
                        selected = pagerState.targetPage,
                        onSelect = { index ->
                            scope.launch { pagerState.animateScrollToPage(index, animationSpec = Motion.iosSpring()) }
                        },
                    )
                    // Новогодний счётчик дней под переключателем дней.
                    if (LocalNewYear.current != null) {
                        NewYearCountdown(Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp))
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { index -> days.getOrNull(index)?.toString() ?: index.toString() },
                    beyondViewportPageCount = 1,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = Motion.iosSpring(stiffness = 400f),
                    ),
                ) { index ->
                    val day = days.getOrNull(index) ?: return@HorizontalPager
                    val state = states[day] ?: DayState()
                    val bookmarkForDay = remember(bookmarks, day) { bookmarks.firstOrNull { it.day == day } }
                    DayPage(
                        day = day,
                        state = state,
                        notificationsOn = notificationsOn,
                        bookmark = bookmarkForDay,
                        hasAnyBookmarks = bookmarks.isNotEmpty(),
                        onBookmarkClick = { pages -> bookmarkDialogTarget = day to pages },
                        onOpenBookmarks = onOpenBookmarks,
                        onRefresh = { vm.refresh(day) },
                        onOpenPage = { page ->
                            val ready = state.content as? DayContent.Ready ?: return@DayPage
                            onOpenViewer(ViewerRequest(Dates.full(day), ready.pages, page, day = day))
                        },
                        onEnableNotifications = onEnableNotifications,
                    )
                }
            }
        }
    }

    bookmarkDialogTarget?.let { (day, pages) ->
        val existing = bookmarks.firstOrNull { it.day == day }
        BookmarkEditDialog(
            dayTitle = Dates.full(day),
            initialComment = existing?.comment.orEmpty(),
            isExisting = existing != null,
            onDismiss = { bookmarkDialogTarget = null },
            onSave = { comment ->
                bookmarkDialogTarget = null
                scope.launch {
                    bookmarksStore.save(day, pages, comment)
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Сохранено в закладки")
                }
            },
            onDelete = if (existing != null) {
                {
                    bookmarkDialogTarget = null
                    scope.launch {
                        bookmarksStore.removeByDay(day)
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("Удалено из закладок")
                    }
                }
            } else null,
        )
    }

    if (showDatePicker) {
        if (compact) {
            // Низкий экран: стандартный календарь не помещается, показываем компактный.
            CompactDatePickerDialog(
                initial = days.first(),
                onDismiss = { showDatePicker = false },
                onConfirm = { date ->
                    vm.showDay(date)
                    showDatePicker = false
                },
            )
        } else {
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
}

/**
 * Крупные переключатели дней с индикатором, есть ли расписание (и значком закладки, если день сохранён).
 *
 * Заголовок таба: «Сегодня» / «Завтра» для первых двух дней, для третьего (это бывает
 * в пятницу и субботу) — просто день недели, потому что «Послезавтра» не помещается.
 */
@Composable
private fun DayTabs(
    days: List<LocalDate>,
    states: Map<LocalDate, DayState>,
    bookmarkedDays: Set<LocalDate>,
    selected: Int,
    onSelect: (Int) -> Unit,
    compact: Boolean = false,
) {
    val today = Dates.today()
    Row(
        if (compact) Modifier.fillMaxWidth().padding(end = 4.dp)
        else Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 6.dp),
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = index == selected
            val isBookmarked = day in bookmarkedDays
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = Motion.iosSnappySpring(),
                label = "tabContainer",
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = Motion.iosSnappySpring(),
                label = "tabContent",
            )
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
                color = container,
                contentColor = content,
                modifier = Modifier.weight(1f),
            ) {
                if (compact) {
                    // Компактный вариант для низкого экрана: заголовок и дата в две строки, статус — значком справа.
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                tabTitle(day, today),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${Dates.weekdayShort(day)}, ${Dates.dayMonth(day)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TabStatus(states[day]?.content, isBookmarked = isBookmarked, compact = true)
                    }
                } else {
                    Column(
                        Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            tabTitle(day, today),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${Dates.weekdayShort(day)}, ${Dates.dayMonth(day)}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        TabStatus(states[day]?.content, isBookmarked = isBookmarked)
                    }
                }
            }
        }
    }
}

/** «Сегодня» / «Завтра» для ближайших двух дней, дальше — день недели с заглавной буквы. */
private fun tabTitle(day: LocalDate, today: LocalDate): String = when (day.toEpochDay() - today.toEpochDay()) {
    0L -> "Сегодня"
    1L -> "Завтра"
    else -> Dates.weekday(day).replaceFirstChar { it.uppercase() }
}

@Composable
private fun TabStatus(content: DayContent?, isBookmarked: Boolean = false, compact: Boolean = false) {
    val (icon, text) = when (content) {
        is DayContent.Ready -> Icons.Rounded.CheckCircle to "есть"
        DayContent.NotPublished -> Icons.Rounded.EventBusy to "нет"
        is DayContent.Failed -> Icons.Rounded.CloudOff to "ошибка"
        else -> null to "…"
    }
    if (compact) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Icon(icon, contentDescription = text, modifier = Modifier.size(16.dp))
            if (isBookmarked) Icon(Icons.Rounded.Bookmark, contentDescription = "В закладках", modifier = Modifier.size(14.dp))
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
        if (isBookmarked) Icon(Icons.Rounded.Bookmark, contentDescription = "В закладках", modifier = Modifier.size(12.dp))
    }
}
