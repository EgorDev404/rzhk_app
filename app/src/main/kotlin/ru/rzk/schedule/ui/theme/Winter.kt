package ru.rzk.schedule.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.data.Settings
import java.time.LocalDate
import java.time.Month
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Зимнее оформление: снегопад поверх всего приложения и сугробы на карточках с расписанием.
 * Сезон: с 1 по 20 декабря (затем начнётся новогоднее оформление).
 */
object Winter {
    fun isSeason(date: LocalDate): Boolean = date.month == Month.DECEMBER && date.dayOfMonth in 1..20

    /**
     * Включено ли зимнее оформление. Пользователь может выключить его переключателем («Зимнее оформление»);
     * разработчик может показывать его независимо от даты на устройстве.
     */
    fun isActive(settings: Settings, today: LocalDate = Dates.today()): Boolean =
        settings.winter && (settings.devWinter || isSeason(today))
}

/** Общее состояние зимы: сколько снега успело скопиться на карточках (0..1). */
@Stable
class WinterState(val reduceMotion: Boolean) {
    var depth: Float by mutableStateOf(if (reduceMotion) 1f else 0f)
}

/** null — зимнего оформления сейчас нет. */
val LocalWinter = staticCompositionLocalOf<WinterState?> { null }

// =================================================================================================
// Снегопад
// =================================================================================================

/** Падающий снег на весь экран. Не перехватывает касания. Пока приложение свёрнуто, кадры не рисуются. */
@Composable
fun SnowFall(winter: WinterState) {
    val density = LocalDensity.current.density
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // На светлом фоне белый снег не виден, поэтому он слегка голубой.
    val flakeColor = if (onDark) Color.White else Color(0xFF8DBBEB)
    val field = remember { SnowField(density) }
    var frame by remember { mutableStateOf(0L) }

    if (!winter.reduceMotion) {
        LaunchedEffect(field) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    field.step(dt)
                    frame = now
                }
            }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        field.resize(size.width, size.height)
        field.draw(this, flakeColor, frame)
    }
}

private class SnowField(private val density: Float) {
    private val random = Random(7)
    private var width = 0f
    private var height = 0f
    private var count = 0
    private var time = 0f

    private var baseX = FloatArray(0)
    private var y = FloatArray(0)
    private var speed = FloatArray(0)
    private var radius = FloatArray(0)
    private var phase = FloatArray(0)
    private var freq = FloatArray(0)
    private var amp = FloatArray(0)
    private var alpha = FloatArray(0)
    private var layer = FloatArray(0)

    fun resize(w: Float, h: Float) {
        if (w == width && h == height) return
        width = w
        height = h
        // Плотность снега пропорциональна площади экрана.
        val flakes = ((w * h) / (density * density * 360f * 800f) * 70f).toInt().coerceIn(45, 140)
        count = flakes
        baseX = FloatArray(flakes); y = FloatArray(flakes); speed = FloatArray(flakes)
        radius = FloatArray(flakes); phase = FloatArray(flakes); freq = FloatArray(flakes)
        amp = FloatArray(flakes); alpha = FloatArray(flakes); layer = FloatArray(flakes)
        for (i in 0 until flakes) spawn(i, initial = true)
    }

    private fun spawn(i: Int, initial: Boolean) {
        val l = random.nextFloat()
        layer[i] = l
        radius[i] = (1.1f + 3.0f * l * l) * density
        speed[i] = (26f + 60f * l + random.nextFloat() * 12f) * density
        amp[i] = (5f + 18f * random.nextFloat()) * (0.5f + 0.5f * l) * density
        freq[i] = 0.5f + 1.1f * random.nextFloat()
        phase[i] = random.nextFloat() * (2f * PI.toFloat())
        alpha[i] = 0.4f + 0.55f * l
        baseX[i] = random.nextFloat() * width
        // В начале часть снежинок уже в воздухе, остальные подтягиваются сверху: снег как будто только начинается.
        y[i] = if (initial) -height * 0.6f + random.nextFloat() * height * 0.9f
        else -radius[i] * 2f - random.nextFloat() * height * 0.15f
    }

    fun step(dt: Float) {
        if (count == 0 || dt <= 0f) return
        time += dt
        // Лёгкий ветер: медленно меняется по направлению и силе.
        val wind = (sin(time * 0.17f) * 12f + sin(time * 0.05f) * 6f) * density
        for (i in 0 until count) {
            y[i] += speed[i] * dt
            phase[i] += freq[i] * dt
            baseX[i] += wind * (0.3f + layer[i]) * dt
            if (baseX[i] < -60f * density) baseX[i] += width + 120f * density
            if (baseX[i] > width + 60f * density) baseX[i] -= width + 120f * density
            if (y[i] - radius[i] > height) spawn(i, initial = false)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun draw(scope: DrawScope, color: Color, frame: Long) {
        with(scope) {
            for (i in 0 until count) {
                val px = baseX[i] + sin(phase[i]) * amp[i]
                val py = y[i]
                if (py < -radius[i] * 3f) continue
                val a = alpha[i]
                val r = radius[i]
                if (r > 3.0f * density) {
                    // Крупные снежинки рисуем кристалликами из шести лучей, они медленно вращаются.
                    val rot = time * (0.4f + layer[i]) + phase[i]
                    val arm = r * 1.9f
                    for (k in 0 until 3) {
                        val angle = rot + k * (PI.toFloat() / 3f)
                        val dx = cos(angle) * arm
                        val dy = sin(angle) * arm
                        drawLine(
                            color.copy(alpha = a),
                            Offset(px - dx, py - dy),
                            Offset(px + dx, py + dy),
                            strokeWidth = 1.3f * density,
                            cap = StrokeCap.Round,
                        )
                    }
                    drawCircle(color.copy(alpha = a), radius = r * 0.45f, center = Offset(px, py))
                } else {
                    if (r > 2f * density) drawCircle(color.copy(alpha = a * 0.22f), radius = r * 1.9f, center = Offset(px, py))
                    drawCircle(color.copy(alpha = a), radius = r, center = Offset(px, py))
                }
            }
        }
    }
}

// =================================================================================================
// Сугробы на карточках
// =================================================================================================

/**
 * Снежная шапка вдоль верхнего края карточки: по мере того как идёт снег ([WinterState.depth] растёт),
 * мини-сугробы поднимаются. Форма у каждой карточки своя (зависит от [seed]). Рисуется над краем карточки,
 * поэтому над ней нужен небольшой отступ (см. [maxHeight]).
 */
@Composable
fun Modifier.snowCap(seed: Int, cornerRadius: Dp = 24.dp, maxHeight: Dp = 12.dp): Modifier {
    val winter = LocalWinter.current ?: return this
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fill = if (onDark) Color(0xFFF2F8FF) else Color(0xFFFAFDFF)
    // На светлом фоне белому сугробу нужен голубоватый контур.
    val edge = if (onDark) Color(0x40FFFFFF) else Color(0x883F7FBF)
    val shape = remember(seed) { DriftShape(seed) }
    return this.drawBehind {
        val depth = winter.depth
        if (depth > 0.01f) drawDrift(shape, depth, cornerRadius.toPx(), maxHeight.toPx(), fill, edge)
    }
}

/** Случайный, но стабильный для данной карточки профиль сугробов: несколько «холмиков» разной ширины. */
private class DriftShape(seed: Int) {
    private val centers: FloatArray
    private val widths: FloatArray
    private val heights: FloatArray
    private val peak: Float

    init {
        val random = Random(seed)
        val bumps = 6
        centers = FloatArray(bumps) { 0.04f + 0.92f * random.nextFloat() }
        widths = FloatArray(bumps) { 0.035f + 0.085f * random.nextFloat() }
        heights = FloatArray(bumps) { 0.45f + 0.55f * random.nextFloat() }
        var best = 0.001f
        for (i in 0..64) best = max(best, raw(i / 64f))
        peak = best
    }

    private fun raw(u: Float): Float {
        var sum = 0f
        for (i in centers.indices) {
            val d = (u - centers[i]) / widths[i]
            sum += heights[i] * exp(-d * d)
        }
        return sum
    }

    /** Высота сугроба в точке u (0..1 вдоль карточки), от 0 до 1. */
    fun height(u: Float): Float = 0.32f + 0.68f * min(1f, raw(u) / peak)
}

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun DrawScope.drawDrift(
    shape: DriftShape,
    depth: Float,
    corner: Float,
    maxHeight: Float,
    fill: Color,
    edge: Color,
) {
    val w = size.width
    val steps = max(16, (w / 5f).toInt())
    // Сугроб рисуется строго внутри верхнего края карточки, не вылезая за её границы.
    val path = Path()
    path.moveTo(0f, 0f)
    for (i in 0..steps) {
        val x = w * i / steps
        // К закруглённым углам сугроб сходит на нет.
        val taper = smooth(x / (corner * 1.4f)) * smooth((w - x) / (corner * 1.4f))
        val h = shape.height(x / w) * taper * depth * maxHeight
        path.lineTo(x, -h)
    }
    path.lineTo(w, 0f)
    path.close()
    drawPath(path, fill)
    drawPath(path, edge, style = Stroke(width = 1.dp.toPx()))
}
