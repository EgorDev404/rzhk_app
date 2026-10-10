package ru.rzk.schedule.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import ru.rzk.schedule.R
import ru.rzk.schedule.data.Bell
import ru.rzk.schedule.data.BellSchedule
import ru.rzk.schedule.data.BellState
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.widget.BellsWidget
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Часы колледжа: обновляются раз в секунду, пока экран на виду (в фоне не тратят батарею). */
@Composable
private fun rememberNow(): State<LocalDateTime> {
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = LocalDateTime.now(Dates.zone), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalDateTime.now(Dates.zone)
                delay(1000 - System.currentTimeMillis() % 1000) // тикаем ровно на границе секунды
            }
        }
    }
}

private val numeric = TextStyle(fontFeatureSettings = "tnum")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BellsScreen(
    fullDay: Boolean,
    onFullDayChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val now by rememberNow()
    val count = if (fullDay) BellSchedule.FULL_DAY else BellSchedule.SHORT_DAY
    val state = BellSchedule.state(now, count)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Предложение добавить виджет — только один раз за всю жизнь приложения.
    val context = LocalContext.current
    var showWidgetSuggest by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("widget_suggested", false)) {
            showWidgetSuggest = true
            prefs.edit().putBoolean("widget_suggested", true).apply()
        }
    }

    if (showWidgetSuggest) {
        WidgetSuggestDialog(
            onAdd = {
                showWidgetSuggest = false
                requestPinWidget(context)
            },
            onDismiss = { showWidgetSuggest = false },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Звонки") },
                actions = {
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
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NowCard(state = state, canExpand = !fullDay, onShowFullDay = { onFullDayChange(true) })
            DayLengthSwitch(fullDay = fullDay, onChange = onFullDayChange)
            BellTable(visibleCount = count, state = state)
            Footnote()
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ================================================================== диалог про виджет ====

@Composable
private fun WidgetSuggestDialog(onAdd: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_suggest_title)) },
        text = { Text(stringResource(R.string.widget_suggest_message)) },
        confirmButton = {
            TextButton(onClick = onAdd) { Text(stringResource(R.string.widget_suggest_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.widget_suggest_no)) }
        },
    )
}

/**
 * Запрашивает у лаунчера пин виджета. На Android 8+ — через [AppWidgetManager.requestPinAppWidget],
 * что открывает системный диалог «Добавить на главный экран?». На более старых — открываем
 * список виджетов, где пользователь сам найдёт наш.
 */
private fun requestPinWidget(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(AppWidgetManager::class.java)
        if (manager?.isRequestPinAppWidgetSupported == true) {
            val provider = ComponentName(context, BellsWidget::class.java)
            manager.requestPinAppWidget(provider, null, null)
            return
        }
    }
    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

// ================================================================== карточка «Сейчас» ====

@Composable
private fun NowCard(state: BellState, canExpand: Boolean, onShowFullDay: () -> Unit) {
    val latest by rememberUpdatedState(state)
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        is BellState.InLesson -> scheme.primaryContainer to scheme.onPrimaryContainer
        is BellState.InBreak -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        is BellState.BeforeClasses -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    val containerColor by animateColorAsState(container, tween(500), label = "nowContainer")
    val contentColor by animateColorAsState(content, tween(500), label = "nowContent")

    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        AnimatedContent(
            targetState = state::class,
            transitionSpec = {
                (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.96f)) togetherWith fadeOut(tween(200))
            },
            label = "nowKind",
        ) { kind ->
            val holder = remember { StateHolder(latest) }
            if (latest::class == kind) holder.value = latest
            NowContent(holder.value, canExpand, onShowFullDay)
        }
    }
}

private class StateHolder(var value: BellState)

@Composable
private fun NowContent(state: BellState, canExpand: Boolean, onShowFullDay: () -> Unit) {
    when (state) {
        is BellState.InLesson -> HeroLayout(
            icon = Icons.Rounded.School,
            chip = "Идёт ${state.bell.number} урок",
            ringProgress = state.progress,
            ringCenter = { p -> PercentCenter(p, "пройдено") },
            big = BellSchedule.countdown(state.remaining),
            bigLabel = "до конца урока",
            stats = listOf("Начался" to BellSchedule.hm(state.bell.start), "Закончится" to BellSchedule.hm(state.bell.end)),
            footer = state.next?.let {
                "Дальше: перемена ${BellSchedule.minutes(state.breakAfter!!)}, затем ${it.number} урок в ${BellSchedule.hm(it.start)}"
            } ?: "Это последний урок на сегодня",
        )

        is BellState.InBreak -> HeroLayout(
            icon = Icons.Rounded.Coffee,
            chip = "Перемена",
            ringProgress = state.progress,
            ringCenter = { p -> PercentCenter(p, "перемены") },
            big = BellSchedule.countdown(state.remaining),
            bigLabel = "до начала ${state.next.number} урока",
            stats = listOf("Урок ${state.after.number} закончился" to BellSchedule.hm(state.after.end),
                "Урок ${state.next.number} начнётся" to BellSchedule.hm(state.next.start)),
            footer = "Перемена длится ${BellSchedule.minutes(state.total)}",
        )

        is BellState.BeforeClasses -> HeroLayout(
            icon = Icons.Rounded.Schedule,
            chip = "Занятия ещё не начались",
            ringProgress = 0f,
            ringCenter = { IconCenter(Icons.Rounded.Alarm) },
            big = BellSchedule.countdown(state.remaining),
            bigLabel = "до начала ${state.first.number} урока",
            stats = listOf("Начало занятий" to BellSchedule.hm(state.first.start)),
            footer = "${state.first.number} урок: ${BellSchedule.range(state.first)}",
        )

        is BellState.Finished -> HeroLayout(
            icon = Icons.Rounded.CheckCircle,
            chip = "Занятия закончились",
            ringProgress = 1f,
            ringCenter = { IconCenter(Icons.Rounded.CheckCircle) },
            big = "На сегодня всё",
            bigLabel = "Последний урок закончился в ${BellSchedule.hm(state.last.end)}",
            stats = emptyList(),
            footer = (if (state.tomorrowIsMonday) "В понедельник" else "Завтра") +
                " занятия начнутся в ${BellSchedule.hm(state.firstTomorrow)}",
            action = if (canExpand && state.last.number < BellSchedule.FULL_DAY) {
                { TextButton(onClick = onShowFullDay) { Text("Больше 8 уроков? Показать все 12") } }
            } else null,
        )

        is BellState.DayOff -> HeroLayout(
            icon = Icons.Rounded.Weekend,
            chip = "Воскресенье",
            ringProgress = 1f,
            ringCenter = { IconCenter(Icons.Rounded.Weekend) },
            big = "Выходной",
            bigLabel = "Звонков сегодня нет",
            stats = emptyList(),
            footer = "В понедельник занятия начнутся в ${BellSchedule.hm(state.nextStart)}",
        )
    }
}

@Composable
private fun HeroLayout(
    icon: ImageVector,
    chip: String,
    ringProgress: Float,
    ringCenter: @Composable (Float) -> Unit,
    big: String,
    bigLabel: String,
    stats: List<Pair<String, String>>,
    footer: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = RoundedCornerShape(50), color = LocalContentColor.current.copy(alpha = 0.12f)) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(chip, style = MaterialTheme.typography.labelLarge)
            }
        }

        ProgressRing(progress = ringProgress, center = ringCenter)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                big,
                style = MaterialTheme.typography.displayMedium.merge(numeric).copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                bigLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        if (stats.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                stats.forEach { (label, value) ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = LocalContentColor.current.copy(alpha = 0.10f),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium,
                                color = LocalContentColor.current.copy(alpha = 0.75f))
                            Text(value, style = MaterialTheme.typography.titleLarge.merge(numeric))
                        }
                    }
                }
            }
        }

        Text(
            footer,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
private fun ProgressRing(progress: Float, center: @Composable (Float) -> Unit) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animated by animateFloatAsState(
        targetValue = if (started) progress else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxSize(),
            color = LocalContentColor.current,
            trackColor = LocalContentColor.current.copy(alpha = 0.14f),
            strokeWidth = 14.dp,
            strokeCap = StrokeCap.Round,
        )
        center(animated)
    }
}

@Composable
private fun PercentCenter(progress: Float, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.displaySmall.merge(numeric).copy(fontWeight = FontWeight.Bold),
        )
        Text(caption, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.75f))
    }
}

@Composable
private fun IconCenter(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp))
}

// ================================================================== переключатель 8 / 12 ====

@Composable
private fun DayLengthSwitch(fullDay: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Таблица звонков", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !fullDay,
                onClick = { onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("8 уроков") }
            SegmentedButton(
                selected = fullDay,
                onClick = { onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("12 уроков") }
        }
    }
}

// ================================================================== таблица ====

private enum class RowStatus { Past, Current, Upcoming, Neutral }

/**
 * Готовые данные для одной строки таблицы, вычисленные один раз на уровне [BellTable].
 * `data class` важен: благодаря структурному равенству Compose пропускает рекомпозицию
 * строк, у которых ничего не поменялось.
 */
private data class RowUi(
    val bell: Bell,
    val status: RowStatus,
    val currentLesson: BellState.InLesson?,
    val startsIn: String?,
)

private fun rowUi(bell: Bell, state: BellState): RowUi {
    val status = when (state) {
        is BellState.DayOff -> RowStatus.Neutral
        is BellState.BeforeClasses -> RowStatus.Upcoming
        is BellState.InLesson -> when {
            bell.number < state.bell.number -> RowStatus.Past
            bell.number == state.bell.number -> RowStatus.Current
            else -> RowStatus.Upcoming
        }
        is BellState.InBreak -> if (bell.number <= state.after.number) RowStatus.Past else RowStatus.Upcoming
        is BellState.Finished -> RowStatus.Past
    }
    val current = (state as? BellState.InLesson)?.takeIf { it.bell.number == bell.number }
    val startsIn = when (state) {
        is BellState.InBreak -> if (state.next.number == bell.number) BellSchedule.countdown(state.remaining) else null
        is BellState.BeforeClasses -> if (state.first.number == bell.number) BellSchedule.countdown(state.remaining) else null
        else -> null
    }
    return RowUi(bell, status, current, startsIn)
}

@Composable
private fun BellTable(visibleCount: Int, state: BellState) {
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BellSchedule.all.forEachIndexed { index, bell ->
            key(bell.number) {
                AnimatedVisibility(
                    visible = bell.number <= visibleCount,
                    enter = expandVertically(tween(350)) + fadeIn(tween(350)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(200)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (index > 0) {
                            val previous = BellSchedule.all[index - 1]
                            BreakChip(
                                minutes = BellSchedule.breakAfter(previous, BellSchedule.all)!!.toMinutes(),
                                current = (state as? BellState.InBreak)?.takeIf { it.next.number == bell.number },
                            )
                        }
                        BellRow(ui = rowUi(bell, state))
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakChip(minutes: Long, current: BellState.InBreak?) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (current != null) scheme.tertiaryContainer else scheme.background, tween(400), label = "breakBg",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = container,
            contentColor = if (current != null) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Coffee, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(
                    if (current != null) "Перемена · осталось ${BellSchedule.countdown(current.remaining)}"
                    else "перемена $minutes мин",
                    style = MaterialTheme.typography.labelMedium.merge(numeric),
                )
            }
        }
    }
}

@Composable
private fun BellRow(ui: RowUi) {
    val scheme = MaterialTheme.colorScheme
    val bell = ui.bell
    val container by animateColorAsState(
        when (ui.status) {
            RowStatus.Current -> scheme.primaryContainer
            RowStatus.Past -> scheme.surfaceContainerLow
            else -> scheme.surfaceContainerHigh
        },
        tween(400), label = "rowBg",
    )
    val fade by animateFloatAsState(if (ui.status == RowStatus.Past) 0.55f else 1f, tween(400), label = "rowFade")
    val isCurrent = ui.status == RowStatus.Current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = if (isCurrent) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth().alpha(fade),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(40.dp).background(if (isCurrent) scheme.primary else scheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        bell.number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCurrent) scheme.onPrimary else scheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(BellSchedule.range(bell), style = MaterialTheme.typography.titleMedium.merge(numeric))
                    Text(
                        BellSchedule.minutes(bell.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
                when {
                    isCurrent -> Pill("идёт", scheme.primary, scheme.onPrimary)
                    ui.status == RowStatus.Past ->
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Прошёл", tint = scheme.primary.copy(alpha = 0.7f))
                    ui.startsIn != null -> Pill("через ${ui.startsIn}", scheme.secondaryContainer, scheme.onSecondaryContainer)
                }
            }

            val lesson = ui.currentLesson
            if (lesson != null) {
                val progress by animateFloatAsState(lesson.progress, tween(1000, easing = LinearEasing), label = "rowProgress")
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = scheme.primary,
                        trackColor = scheme.onPrimaryContainer.copy(alpha = 0.15f),
                    )
                    Text(
                        "${(lesson.progress * 100).roundToInt()}% · осталось ${BellSchedule.countdown(lesson.remaining)}",
                        style = MaterialTheme.typography.labelMedium.merge(numeric),
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.merge(numeric),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Footnote() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Info, contentDescription = null,
            modifier = Modifier.size(14.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            "Расписание звонков показывается с понедельника по субботу независимо от расписания уроков.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}