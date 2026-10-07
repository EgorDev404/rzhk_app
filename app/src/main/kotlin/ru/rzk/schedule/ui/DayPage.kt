package ru.rzk.schedule.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.data.FailureKind
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
    onRefresh: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onEnableNotifications: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        // Прокручиваемый контейнер нужен всегда — иначе «потянуть вниз» не работает на коротких экранах.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AnimatedContent(
                targetState = state.content,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(160)) },
                label = "dayContent",
            ) { content ->
                when (content) {
                    is DayContent.Loading -> LoadingPlaceholder(content.stage)
                    is DayContent.Ready -> ReadyContent(day, content, onOpenPage)
                    DayContent.NotPublished -> NotPublishedState(day, notificationsOn, onRefresh, onEnableNotifications)
                    is DayContent.Failed -> FailedState(content.kind, onRefresh)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReadyContent(day: LocalDate, content: DayContent.Ready, onOpenPage: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (content.stale) {
            InfoBanner(
                icon = Icons.Rounded.Warning,
                text = "Сайт недоступен — показана сохранённая копия (${updatedText(content.checkedAt).lowercase()}). " +
                    "Расписание могло измениться.",
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    updatedText(content.checkedAt) + " · потяните вниз, чтобы обновить",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content.pages.forEachIndexed { index, file ->
            SchedulePageCard(file, label = "Расписание ${Dates.accusative(day)}, страница ${index + 1}") { onOpenPage(index) }
        }
    }
}

@Composable
private fun NotPublishedState(
    day: LocalDate,
    notificationsOn: Boolean,
    onRetry: () -> Unit,
    onEnableNotifications: () -> Unit,
) {
    if (day.dayOfWeek == DayOfWeek.SUNDAY) {
        StatusState(
            icon = Icons.Rounded.EventBusy,
            title = "Воскресенье — выходной",
            message = "Занятий по расписанию нет. Хорошего отдыха!",
        )
        return
    }
    StatusState(
        icon = Icons.Rounded.EventBusy,
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
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Проверить снова")
        }
    }
}

@Composable
private fun FailedState(kind: FailureKind, onRetry: () -> Unit) {
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
    }
}
