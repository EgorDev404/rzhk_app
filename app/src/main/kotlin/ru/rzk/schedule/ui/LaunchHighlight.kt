package ru.rzk.schedule.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Общие часы: анимация проигрывается один раз за запуск приложения, даже если заголовок собирается дважды. */
private object LaunchHighlightClock {
    var startedAt: Long = 0L
    var done: Boolean = false
}

private const val HIGHLIGHT_MS = 950L

/** Если экран так и не загрузился (нет сети и т. п.), подсвечиваем заголовок всё равно, но не дольше чем через это время. */
private const val MAX_WAIT_MS = 3500L

/** Небольшая пауза после загрузки, чтобы картинки успели появиться и подсветка не наложилась на их появление. */
private const val SETTLE_MS = 250L

/**
 * Заголовок экрана с подсветкой при входе в приложение.
 *
 * Пока экран загружается, это обычный текст. Когда расписание загружено ([ready]), цвет заголовка на мгновение
 * становится акцентным и возвращается к обычному, а под текстом слева направо вырастает линия. Линия остаётся
 * под заголовком насовсем. Подсветка проигрывается один раз за запуск приложения.
 */
@Composable
fun LaunchHighlightTitle(text: String, ready: Boolean) {
    val readyNow by rememberUpdatedState(ready)
    // < 0 — ещё не началось (экран грузится), 0..1 — идёт анимация, 1 — закончилась (линия остаётся).
    var p by remember { mutableStateOf(if (LaunchHighlightClock.done) 1f else -1f) }

    LaunchedEffect(Unit) {
        if (LaunchHighlightClock.done) {
            p = 1f
            return@LaunchedEffect
        }
        if (LaunchHighlightClock.startedAt == 0L) {
            // Ждём, пока экран загрузится (или пока не истечёт предельное время ожидания).
            val deadline = SystemClock.elapsedRealtime() + MAX_WAIT_MS
            while (!readyNow && SystemClock.elapsedRealtime() < deadline) delay(50)
            if (readyNow) delay(SETTLE_MS)
            // Второй экземпляр заголовка (в свёрнутой и развёрнутой шапке) мог уже запустить подсветку.
            if (LaunchHighlightClock.startedAt == 0L) LaunchHighlightClock.startedAt = SystemClock.elapsedRealtime()
        }
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - LaunchHighlightClock.startedAt
            if (elapsed >= HIGHLIGHT_MS) break
            p = (elapsed / HIGHLIGHT_MS.toFloat()).coerceIn(0f, 1f)
            withFrameNanos { }
        }
        LaunchHighlightClock.done = true
        p = 1f
    }

    val accent = MaterialTheme.colorScheme.primary
    val base = LocalContentColor.current
    // Цвет: сначала акцентный, затем плавно возвращается к обычному.
    val color = if (p < 0f || p >= 1f) Color.Unspecified else lerp(accent, base, ease(((p - 0.1f) / 0.7f).coerceIn(0f, 1f)))
    // Линия: растёт слева направо за первые 45% и дальше остаётся на всю ширину текста.
    val line = if (p < 0f) 0f else ease((p / 0.45f).coerceIn(0f, 1f))

    Box {
        Text(
            text,
            color = color,
            modifier = Modifier.drawBehind {
                if (line > 0f) {
                    val thickness = 3.dp.toPx()
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(0f, size.height - thickness - 2.dp.toPx()),
                        size = Size(size.width * line, thickness),
                        cornerRadius = CornerRadius(thickness / 2f),
                    )
                }
            },
        )
    }
}

/** Плавное ускорение и замедление (smoothstep). */
private fun ease(t: Float): Float = t * t * (3f - 2f * t)
