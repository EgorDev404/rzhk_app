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

/** Общие часы: анимация проигрывается один раз за запуск приложения, даже если заголовок собирается дважды. */
private object LaunchHighlightClock {
    var startedAt: Long = 0L
}

private const val HIGHLIGHT_MS = 950L

/**
 * Заголовок экрана, который при входе в приложение быстро «вспыхивает»: цвет на мгновение акцентный и возвращается
 * к обычному, а под текстом пробегает тонкая линия и гаснет. Дальше (при переходах между экранами) — обычный текст.
 */
@Composable
fun LaunchHighlightTitle(text: String) {
    var p by remember { mutableStateOf(1f) } // 1 = подсветка не нужна (уже проиграна или закончилась)
    LaunchedEffect(Unit) {
        if (LaunchHighlightClock.startedAt == 0L) LaunchHighlightClock.startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - LaunchHighlightClock.startedAt
            if (elapsed >= HIGHLIGHT_MS) {
                p = 1f
                break
            }
            p = elapsed / HIGHLIGHT_MS.toFloat()
            withFrameNanos { }
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val base = LocalContentColor.current
    // Цвет: сначала акцентный, затем плавно возвращается к обычному.
    val color = if (p >= 1f) Color.Unspecified else lerp(accent, base, ease(((p - 0.1f) / 0.7f).coerceIn(0f, 1f)))
    // Линия: растёт слева направо за первые 45%, потом гаснет к концу.
    val grow = ease((p / 0.45f).coerceIn(0f, 1f))
    val fade = 1f - ease(((p - 0.6f) / 0.4f).coerceIn(0f, 1f))

    Box {
        Text(
            text,
            color = color,
            modifier = Modifier.drawBehind {
                if (p < 1f) {
                    val thickness = 3.dp.toPx()
                    drawRoundRect(
                        color = accent.copy(alpha = fade),
                        topLeft = Offset(0f, size.height - thickness - 2.dp.toPx()),
                        size = Size(size.width * grow, thickness),
                        cornerRadius = CornerRadius(thickness / 2f),
                    )
                }
            },
        )
    }
}

/** Плавное ускорение и замедление (smoothstep). */
private fun ease(t: Float): Float = t * t * (3f - 2f * t)
