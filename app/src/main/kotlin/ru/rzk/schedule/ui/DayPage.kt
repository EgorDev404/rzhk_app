package ru.rzk.schedule.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.rzk.schedule.data.BookmarkEntry
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.data.FailureKind
import ru.rzk.schedule.ui.theme.LocalWinter
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM 'в' HH:mm")

private fun updatedText(at: Instant): String {
    val local = at.atZone(ZoneId.systemDefault())
    val sameDay = local.toLocalDate() == LocalDate.now(ZoneId.systemDefault())
    return "Обновлено " + if (sameDay) "в ${timeFormat.format(local)}" else dateTimeFormat.format(local)
}

/** Одна страница пейджера: расписание на конкретный день со всеми состояниями. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPage(
    day: LocalDate,
    state: DayState,
    notificationsOn: Boolean,
    bookmark: BookmarkEntry?,
    hasAnyBookmarks: Boolean,
    onBookmarkClick: (List<File>) -> Unit,
    onOpenBookmarks: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onEnableNotifications: () -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        // Крупный индикатор: масштабируем стандартный в 1.4 раза.
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter).scale(1.4f),
                isRefreshing = state.refreshing,
                state = pullState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        // Прокручиваемый контейнер нужен всегда — иначе «потянуть вниз» не работает на коротких экранах.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // На широких экранах (планшет, горизонталь) ограничиваем ширину карточек и центрируем их.
            CenteredColumn {
                AnimatedContent(
                    targetState = state.content,
                    contentKey = { it::class },
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(Motion.iosSpring(), initialScale = 0.98f)) togetherWith fadeOut(tween(140))
                    },
                    label = "dayContent",
                ) { content ->
                    when (content) {
                        is DayContent.Loading -> LoadingPlaceholder(content.stage)
                        is DayContent.Ready -> ReadyContent(
                            day = day,
                            content = content,
                            bookmark = bookmark,
                            onBookmarkClick = { onBookmarkClick(content.pages) },
                            onOpenPage = onOpenPage,
                        )
                        DayContent.NotPublished -> NotPublishedState(
                            day = day,
                            notificationsOn = notificationsOn,
                            hasAnyBookmarks = hasAnyBookmarks,
                            onOpenBookmarks = onOpenBookmarks,
                            onRetry = onRefresh,
                            onEnableNotifications = onEnableNotifications,
                        )
                        is DayContent.Failed -> FailedState(
                            kind = content.kind,
                            hasAnyBookmarks = hasAnyBookmarks,
                            onOpenBookmarks = onOpenBookmarks,
                            onRetry = onRefresh,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReadyContent(
    day: LocalDate,
    content: DayContent.Ready,
    bookmark: BookmarkEntry?,
    onBookmarkClick: () -> Unit,
    onOpenPage: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (content.stale) {
            InfoBanner(
                icon = Icons.Rounded.Warning,
                text = "Сайт недоступен — показана сохранённая копия (${updatedText(content.checkedAt).lowercase()}). " +
                    "Расписание могло измениться.",
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!content.stale) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = updatedText(content.checkedAt) + " · потяните вниз, чтобы обновить",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            BookmarkPill(
                bookmarked = bookmark != null,
                onClick = onBookmarkClick,
            )
        }

        // Если к закладке на этот день добавлен комментарий — аккуратно показываем его прямо над расписанием.
        if (bookmark != null && bookmark.comment.isNotBlank()) {
            Surface(
                onClick = onBookmarkClick,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = bookmark.comment,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        content.pages.forEachIndexed { index, file ->
            SchedulePageCard(file, label = "Расписание ${Dates.accusative(day)}, страница ${index + 1}") { onOpenPage(index) }
        }
    }
}

@Composable
private fun BookmarkPill(bookmarked: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (bookmarked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = Motion.iosSnappySpring(),
        label = "bookmarkPillBg",
    )
    val content by animateColorAsState(
        if (bookmarked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.iosSnappySpring(),
        label = "bookmarkPillContent",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkAdd,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (bookmarked) "В закладках" else "В закладки",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun NotPublishedState(
    day: LocalDate,
    notificationsOn: Boolean,
    hasAnyBookmarks: Boolean,
    onOpenBookmarks: () -> Unit,
    onRetry: () -> Unit,
    onEnableNotifications: () -> Unit,
) {
    if (day.dayOfWeek == DayOfWeek.SUNDAY) {
        StatusState(
            icon = if (LocalWinter.current != null) Icons.Rounded.AcUnit else Icons.Rounded.EventBusy,
            title = "Воскресенье — выходной",
            message = "Занятий по расписанию нет. Хорошего отдыха!",
        ) {
            if (hasAnyBookmarks) {
                FilledTonalButton(onClick = onOpenBookmarks) {
                    Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Открыть закладки")
                }
            }
        }
        return
    }
    StatusState(
        icon = if (LocalWinter.current != null) Icons.Rounded.AcUnit else Icons.Rounded.EventBusy,
        title = "Расписания ${Dates.accusative(day)} пока нет",
        message = "Колледж ещё не выложил файл. Потяните вниз, чтобы проверить снова" +
            if (notificationsOn) " — или просто подождите: мы сообщим, как только он появится." else ".",
    ) {
        if (notificationsOn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Уведомления включены", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            FilledTonalButton(onClick = onEnableNotifications) {
                Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Сообщить, когда выложат")
            }
        }
        if (hasAnyBookmarks) {
            FilledTonalButton(
                onClick = onOpenBookmarks,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Посмотреть закладки")
            }
        }
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Проверить снова")
        }
    }
}

@Composable
private fun FailedState(
    kind: FailureKind,
    hasAnyBookmarks: Boolean,
    onOpenBookmarks: () -> Unit,
    onRetry: () -> Unit,
) {
    val (title, message) = when (kind) {
        FailureKind.Offline -> "Нет подключения к интернету" to
            "Проверьте сеть и потяните вниз, чтобы повторить."
        FailureKind.SiteDown -> "Сайт колледжа не отвечает" to
            "Попробуйте через пару минут. Если включён VPN или вы за границей — отключите VPN: сайт открывается только из России."
        FailureKind.Certificate -> "Не удалось проверить сертификат сайта" to
            "Проверьте дату и время на телефоне и повторите."
        FailureKind.BadFile -> "Не получилось открыть файл расписания" to
            "Файл на сайте повреждён или необычного формата. Попробуйте обновить позже."
    }
    StatusState(icon = Icons.Rounded.CloudOff, title = title, message = message) {
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Повторить")
        }
        if (hasAnyBookmarks) {
            OutlinedButton(onClick = onOpenBookmarks) {
                Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Открыть закладки")
            }
        }
    }
}