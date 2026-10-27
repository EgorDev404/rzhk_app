package ru.rzk.schedule.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.rzk.schedule.app
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.data.LampState
import ru.rzk.schedule.data.LampStatus
import ru.rzk.schedule.data.Reports
import ru.rzk.schedule.data.SystemStatus

private val Green = Color(0xFF2E9E5B)
private val Red = Color(0xFFE5483D)
private val Yellow = Color(0xFFF2B01E)

private class LampLook(val color: Color, val title: String, val subtitle: String)

private fun ago(seconds: Long): String = when {
    seconds < 90 -> "только что"
    seconds < 3600 -> "${seconds / 60} мин назад"
    else -> "${seconds / 3600} ч назад"
}

@Composable
private fun lookOf(status: LampStatus): LampLook {
    val gray = MaterialTheme.colorScheme.outline
    return when (status.state) {
        LampState.Working -> LampLook(
            Green,
            "Система оповещения работает стабильно",
            "Сервер проверяет сайт колледжа · последняя проверка ${ago(status.lastCheckAgeSec ?: 0)}",
        )
        LampState.Down -> LampLook(
            Red,
            "Система оповещения по какой-либо причине не работает",
            "Сервер остановлен или не отвечает. Уведомления не придут, расписание можно смотреть в приложении вручную.",
        )
        LampState.SiteUnreachable -> LampLook(
            Yellow,
            "Система оповещения не смогла установить соединение с сайтом колледжа",
            "Сервер работает, но сайт колледжа сейчас недоступен. Как только он откроется, проверка продолжится сама.",
        )
        LampState.Offline -> LampLook(
            gray,
            "Нет подключения к сети для уведомлений",
            "Подключитесь к интернету, чтобы получать уведомления о расписании.",
        )
        LampState.Unknown -> LampLook(
            gray,
            "Не удалось узнать состояние системы оповещения",
            "Попробуем ещё раз через несколько секунд.",
        )
        LampState.Loading -> LampLook(gray, "Проверяю состояние системы оповещения…", "")
    }
}

/**
 * Строка со «лампочкой» и состоянием системы оповещения. Обновляется сама, пока открыт экран.
 * Если лампочка красная, под ней появляется кнопка «Сообщить разработчику» (не чаще раза в сутки).
 */
@Composable
fun SystemStatusRow(onMessage: (String) -> Unit) {
    val status by rememberSystemStatus()
    val look = lookOf(status)
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    if (look.subtitle.isEmpty()) {
        ListItem(
            leadingContent = { StatusLamp(look.color) },
            headlineContent = { Text(look.title) },
            colors = colors,
            modifier = Modifier.animateContentSize(),
        )
    } else {
        ListItem(
            leadingContent = { StatusLamp(look.color) },
            headlineContent = { Text(look.title) },
            supportingContent = { Text(look.subtitle) },
            colors = colors,
            modifier = Modifier.animateContentSize(),
        )
    }
    AnimatedVisibility(visible = status.state == LampState.Down) {
        ReportButton(status, onMessage)
    }
}

@Composable
private fun ReportButton(status: LampStatus, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { context.app.settings }
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var reportedToday by remember { mutableStateOf(store.lastReportDay() == Dates.today().toString()) }
    var sending by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        FilledTonalButton(
            enabled = !reportedToday && !sending,
            onClick = {
                sending = true
                scope.launch {
                    val result = Reports.send(store.installId(), version, status.lastCheckAgeSec)
                    sending = false
                    when (result) {
                        Reports.SendResult.Sent -> {
                            store.markReported(Dates.today().toString())
                            reportedToday = true
                            onMessage("Спасибо! Разработчик получит сообщение")
                        }
                        Reports.SendResult.AlreadySent -> {
                            store.markReported(Dates.today().toString())
                            reportedToday = true
                            onMessage("Вы уже сообщали об этом сегодня")
                        }
                        Reports.SendResult.Failed ->
                            onMessage("Не удалось отправить. Проверьте интернет и попробуйте позже")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (sending) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (reportedToday) "Сообщение уже отправлено сегодня" else "Сообщить разработчику")
        }
        if (!reportedToday) {
            Text(
                "Отправить можно один раз в сутки. Сообщение анонимное.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

/** Лампочка: мягко «дышит» и пускает расходящееся кольцо. Мигают все состояния, различаются цветом. */
@Composable
private fun StatusLamp(color: Color) {
    val transition = rememberInfiniteTransition(label = "lamp")
    val breathe by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "ripple",
    )
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(28.dp)
                .graphicsLayer {
                    val scale = 0.45f + 0.55f * ripple
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.45f * (1f - ripple)
                }
                .background(color, CircleShape),
        )
        Box(Modifier.size(14.dp).alpha(breathe).background(color, CircleShape))
    }
}

private fun isOnline(manager: ConnectivityManager): Boolean {
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Состояние лампочки: следит за наличием интернета и, пока экран на виду, раз в 20 секунд
 * спрашивает у сервера, как дела.
 */
@Composable
private fun rememberSystemStatus(): State<LampStatus> {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember { mutableStateOf(isOnline(manager)) }

    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = isOnline(manager) }
            override fun onLost(network: Network) { online = isOnline(manager) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                online = isOnline(manager)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }

    val state = remember { mutableStateOf(LampStatus(LampState.Loading)) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(online) {
        val job = scope.launch {
            if (!online) {
                state.value = LampStatus(LampState.Offline)
                return@launch
            }
            if (state.value.state == LampState.Offline) state.value = LampStatus(LampState.Loading)
            while (true) {
                state.value = SystemStatus.lampFor(SystemStatus.fetch())
                delay(20_000)
            }
        }
        onPauseOrDispose { job.cancel() }
    }
    return state
}
