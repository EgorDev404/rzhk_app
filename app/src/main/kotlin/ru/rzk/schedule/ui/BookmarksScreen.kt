package ru.rzk.schedule.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import ru.rzk.schedule.data.BookmarkEntry
import ru.rzk.schedule.data.BookmarksStore
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.ui.theme.FrostCorners
import ru.rzk.schedule.ui.theme.Garland
import ru.rzk.schedule.ui.theme.LocalNewYear
import ru.rzk.schedule.ui.theme.LocalWinter
import ru.rzk.schedule.ui.theme.SnowFall
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Диалог добавления или редактирования закладки с полем для комментария и быстрыми подсказками-чипами.
 */
@Composable
fun BookmarkEditDialog(
    dayTitle: String,
    initialComment: String,
    isExisting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var comment by rememberSaveable { mutableStateOf(initialComment) }
    val focusRequester = remember { FocusRequester() }
    val suggestions = remember {
        listOf("Обычное расписание", "Чётная неделя", "Нечётная неделя", "Сокращённые пары", "Дистант")
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(if (isExisting) "Изменить закладку" else "Добавить в закладки")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = dayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text("Комментарий (необязательно)") },
                    placeholder = { Text("Например: обычное расписание на понедельник") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onSave(comment) },
                    ),
                    trailingIcon = if (comment.isNotEmpty()) {
                        {
                            IconButton(onClick = { comment = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Очистить")
                            }
                        }
                    } else null,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { preset ->
                        AssistChip(
                            onClick = {
                                comment = if (comment.isBlank()) preset else "$comment · $preset"
                            },
                            label = { Text(preset) },
                            shape = RoundedCornerShape(50),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onSave(comment) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isExisting && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        },
    )
}

/**
 * Экран «Закладки»: поиск по комментарию, дате и дню недели, фильтр по дням недели,
 * редактирование комментария, удаление и мгновенный просмотр сохранённых страниц расписания.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    store: BookmarksStore,
    bookmarks: List<BookmarkEntry>,
    onOpenViewer: (ViewerRequest) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var selectedWeekday by rememberSaveable { mutableStateOf<Int?>(null) } // 1..6 (Пн..Сб)
    var editingEntry by remember { mutableStateOf<BookmarkEntry?>(null) }

    // Какие дни недели вообще встречаются среди закладок (чтобы показывать удобные быстрые чипы фильтра).
    val availableWeekdays by remember(bookmarks) {
        derivedStateOf {
            bookmarks.map { it.day.dayOfWeek.value }.distinct().sorted()
        }
    }

    val filtered by remember(bookmarks, query, selectedWeekday) {
        derivedStateOf {
            bookmarks.filter { entry ->
                val matchesDay = selectedWeekday == null || entry.day.dayOfWeek.value == selectedWeekday
                matchesDay && store.matches(entry, query)
            }
        }
    }

    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LocalWinter.current?.let { winter ->
            Box(Modifier.fillMaxSize().zIndex(1f)) {
                FrostCorners(winter)
                SnowFall(winter)
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = safeInsets,
            topBar = {
                Box {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.matchParentSize()) {}
                    LocalNewYear.current?.let { Garland(it) }
                    Box(Modifier.zIndex(2f)) {
                        TopAppBar(
                            windowInsets = TopAppBarDefaults.windowInsets.union(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                            ),
                            title = {
                                Column {
                                    Text("Закладки")
                                    if (bookmarks.isNotEmpty()) {
                                        Text(
                                            text = "${bookmarks.size} ${pluralBookmarks(bookmarks.size)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (bookmarks.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        StatusState(
                            icon = Icons.Outlined.BookmarkBorder,
                            title = "Закладок пока нет",
                            message = "Добавляйте нужные файлы расписания в избранное, чтобы открывать их в любое время даже без интернета.",
                        )
                    }
                } else {
                    CenteredColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxWidth = 640.dp,
                    ) {
                        // Строка поиска по комментарию, дате или дню недели
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                            placeholder = { Text("Поиск по комментарию, дате или дню недели…") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            },
                            trailingIcon = if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Очистить поиск")
                                    }
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        )

                        // Быстрые фильтры по дням недели (показываются, если закладок на разные дни недели больше одной)
                        if (availableWeekdays.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = selectedWeekday == null,
                                    onClick = { selectedWeekday = null },
                                    label = { Text("Все дни") },
                                    shape = RoundedCornerShape(50),
                                )
                                availableWeekdays.forEach { dayNum ->
                                    val dow = DayOfWeek.of(dayNum)
                                    val label = weekdayLabel(dow)
                                    FilterChip(
                                        selected = selectedWeekday == dayNum,
                                        onClick = {
                                            selectedWeekday = if (selectedWeekday == dayNum) null else dayNum
                                        },
                                        label = { Text(label) },
                                        shape = RoundedCornerShape(50),
                                    )
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            StatusState(
                                icon = Icons.Rounded.Search,
                                title = "Ничего не найдено",
                                message = "Попробуйте изменить запрос или сбросить фильтр по дню недели.",
                            ) {
                                FilledTonalButton(onClick = {
                                    query = ""
                                    selectedWeekday = null
                                }) {
                                    Text("Сбросить поиск")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(filtered, key = { it.id }) { entry ->
                                CenteredColumn(maxWidth = 640.dp) {
                                    BookmarkCard(
                                        entry = entry,
                                        onOpenPage = { pageIndex ->
                                            val title = bookmarkViewerTitle(entry)
                                            onOpenViewer(ViewerRequest(title, entry.pages, pageIndex))
                                        },
                                        onEdit = { editingEntry = entry },
                                        onDelete = {
                                            scope.launch {
                                                store.remove(entry.id)
                                                snackbar.currentSnackbarData?.dismiss()
                                                snackbar.showSnackbar("Закладка удалена")
                                            }
                                        },
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    editingEntry?.let { target ->
        BookmarkEditDialog(
            dayTitle = bookmarkDayHeader(target.day),
            initialComment = target.comment,
            isExisting = true,
            onDismiss = { editingEntry = null },
            onSave = { newComment ->
                store.updateComment(target.id, newComment)
                editingEntry = null
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Комментарий сохранён")
                }
            },
            onDelete = {
                val id = target.id
                editingEntry = null
                scope.launch {
                    store.remove(id)
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Закладка удалена")
                }
            },
        )
    }
}

@Composable
private fun BookmarkCard(
    entry: BookmarkEntry,
    onOpenPage: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = bookmarkDayHeader(entry.day),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "%02d.%02d.%04d · %d стр.".format(
                            entry.day.dayOfMonth,
                            entry.day.monthValue,
                            entry.day.year,
                            entry.pages.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Изменить комментарий",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Удалить из закладок",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entry.comment.isNotBlank()) {
                Surface(
                    onClick = onEdit,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = entry.comment,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            } else {
                Text(
                    text = "Добавить комментарий…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit)
                        .padding(vertical = 2.dp),
                )
            }

            // Превью страниц расписания: по нажатию сразу открывается полноэкранный просмотр с зумом
            if (entry.pages.size == 1) {
                BookmarkPagePreview(
                    file = entry.pages.first(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenPage(0) },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    entry.pages.forEachIndexed { index, file ->
                        BookmarkPagePreview(
                            file = file,
                            pageLabel = "Стр. ${index + 1}",
                            modifier = Modifier.width(260.dp),
                            onClick = { onOpenPage(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkPagePreview(
    file: File,
    modifier: Modifier = Modifier,
    pageLabel: String? = null,
    onClick: () -> Unit,
) {
    val cachedBitmap = remember(file) { PageImageCache.peek(file, PageImageCache.SAMPLE_SMALL) }
    val image by produceState<ImageBitmap?>(initialValue = cachedBitmap, file) {
        if (value == null) value = PageImageCache.load(file, PageImageCache.SAMPLE_SMALL)
    }
    val ratio = image?.let { it.width.toFloat() / it.height } ?: PAGE_RATIO

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            if (image == null) {
                Box(Modifier.fillMaxSize().shimmer())
            } else {
                Image(
                    bitmap = image!!,
                    contentDescription = "Открыть расписание",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (pageLabel != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xAA000000),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) {
                    Text(
                        text = pageLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private fun bookmarkDayHeader(day: LocalDate): String {
    val weekday = Dates.weekday(day).replaceFirstChar { it.uppercase() }
    return "$weekday, ${Dates.dayMonth(day)}"
}

private fun bookmarkViewerTitle(entry: BookmarkEntry): String {
    val base = bookmarkDayHeader(entry.day)
    return if (entry.comment.isNotBlank()) "$base · ${entry.comment}" else base
}

private fun weekdayLabel(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "Понедельник"
    DayOfWeek.TUESDAY -> "Вторник"
    DayOfWeek.WEDNESDAY -> "Среда"
    DayOfWeek.THURSDAY -> "Четверг"
    DayOfWeek.FRIDAY -> "Пятница"
    DayOfWeek.SATURDAY -> "Суббота"
    DayOfWeek.SUNDAY -> "Воскресенье"
}

private fun pluralBookmarks(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "закладок"
        mod10 == 1 -> "закладка"
        mod10 in 2..4 -> "закладки"
        else -> "закладок"
    }
}
