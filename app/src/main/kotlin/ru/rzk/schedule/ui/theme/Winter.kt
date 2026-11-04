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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.sqrt
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

    // graphicsLayer выделяет снег в отдельный слой: покадровая перерисовка не трогает остальной интерфейс.
    Canvas(Modifier.fillMaxSize().graphicsLayer { }) {
        field.resize(size.width, size.height)
        field.draw(this, flakeColor, frame)
    }
}

private const val GUST_SECONDS = 3.4f

private class SnowField(private val density: Float) {
    private val random = Random(7)
    private var width = 0f
    private var height = 0f
    private var count = 0
    private var time = 0f

    // Порывы ветра: раз в 20–40 секунд налетает сильный порыв, и снег на несколько секунд сносит в сторону.
    private var untilGust = 14f + random.nextFloat() * 10f
    private var gustLeft = 0f
    private var gustDirection = 1f

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
        untilGust -= dt
        if (gustLeft <= 0f && untilGust <= 0f) {
            gustLeft = GUST_SECONDS
            gustDirection = if (random.nextBoolean()) 1f else -1f
            untilGust = 22f + random.nextFloat() * 20f
        }
        var gust = 0f
        if (gustLeft > 0f) {
            gustLeft -= dt
            val k = (1f - gustLeft / GUST_SECONDS).coerceIn(0f, 1f)
            gust = sin(PI.toFloat() * k) * 95f * gustDirection // нарастает, достигает максимума и стихает
        }
        val wind = (sin(time * 0.17f) * 12f + sin(time * 0.05f) * 6f + gust) * density
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
    val r = min(corner, w / 2f)
    val steps = max(24, (w / 4f).toInt())

    // Верхняя кромка сугроба идёт вдоль карточки, а на закруглённых углах огибает дугу: снег лежит на карточке,
    // а не висит над ней. К самым краям сугроб сходит на нет.
    val rim = Path()
    val body = Path()
    for (i in 0..steps) {
        val x = w * i / steps
        val soft = r * 0.8f
        val taper = if (soft < 1f) 1f else smooth(x / soft) * smooth((w - x) / soft)
        val y = cardEdgeY(x, w, r) - shape.height(x / w) * taper * depth * maxHeight
        if (i == 0) {
            rim.moveTo(x, y)
            body.moveTo(x, y)
        } else {
            rim.lineTo(x, y)
            body.lineTo(x, y)
        }
    }
    // Низ закрашиваем ниже линии углов: всё, что попало на саму карточку, она закрывает собой (снег рисуется позади неё).
    val bottom = r + 2f
    body.lineTo(w, bottom)
    body.lineTo(0f, bottom)
    body.close()
    drawPath(body, fill)
    drawPath(rim, edge, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
}

/** Где проходит верхний край карточки с закруглёнными углами радиуса [r] на расстоянии [x] от левого края. */
private fun cardEdgeY(x: Float, w: Float, r: Float): Float {
    val d = when {
        x < r -> r - x
        x > w - r -> x - (w - r)
        else -> 0f
    }
    if (d <= 0f) return 0f
    return r - sqrt(max(0f, r * r - d * d))
}

// =================================================================================================
// Иней по углам экрана
// =================================================================================================

/** Один отрезок ледяной «ветки»: координаты в dp от угла, момент появления и яркость. */
private class FrostSegment(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val grow: Float, val width: Float, val fade: Float,
)

/**
 * Узор инея, как на замёрзшем окне: из угла веером растут стебли, от них под 60° отходят ветки, от веток — веточки.
 * Строится один раз (с фиксированным зерном), поэтому узор всегда одинаковый.
 */
private object Frost {
    private const val REACH = 150f

    val segments: List<FrostSegment> by lazy {
        val random = Random(2026)
        val out = ArrayList<FrostSegment>()
        for (degrees in listOf(20f, 45f, 70f)) {
            branch(out, random, 0f, 0f, Math.toRadians(degrees.toDouble()).toFloat(), 118f, order = 0)
        }
        out
    }

    private fun branch(
        out: MutableList<FrostSegment>, random: Random,
        startX: Float, startY: Float, startAngle: Float, length: Float, order: Int,
    ) {
        var x = startX
        var y = startY
        var angle = startAngle
        val step = max(5f, length / 7f)
        var travelled = 0f
        while (travelled < length) {
            angle += (random.nextFloat() - 0.5f) * 0.18f
            val nx = x + cos(angle) * step
            val ny = y + sin(angle) * step
            val reach = sqrt(nx * nx + ny * ny) / REACH
            out += FrostSegment(
                x, y, nx, ny,
                grow = reach.coerceIn(0f, 1f),
                width = if (order == 0) 1.1f else 0.8f,
                fade = (1f - travelled / length * 0.6f - order * 0.12f).coerceAtLeast(0.2f),
            )
            if (order < 2 && travelled > step * 1.5f && random.nextFloat() < 0.6f) {
                val side = if (random.nextBoolean()) 1f else -1f
                branch(out, random, nx, ny, angle + side * 1.05f + (random.nextFloat() - 0.5f) * 0.2f, length * 0.4f, order + 1)
            }
            x = nx
            y = ny
            travelled += step
        }
    }
}

/** Иней в четырёх углах и лёгкое морозное свечение по верхнему и нижнему краю. Появляется постепенно. */
@Composable
fun FrostCorners(winter: WinterState) {
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ice = if (onDark) Color(0xFFD6ECFF) else Color(0xFF6FA8E0)
    Canvas(Modifier.fillMaxSize().graphicsLayer { }) {
        val depth = winter.depth
        if (depth <= 0.02f) return@Canvas
        val glow = 110.dp.toPx()
        drawRect(Brush.verticalGradient(listOf(ice.copy(alpha = 0.10f * depth), Color.Transparent), startY = 0f, endY = glow))
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, ice.copy(alpha = 0.10f * depth)),
                startY = size.height - glow,
                endY = size.height,
            ),
        )
        for (corner in 0 until 4) {
            val sx = if (corner % 2 == 0) 1f else -1f
            val sy = if (corner < 2) 1f else -1f
            val ox = if (sx > 0f) 0f else size.width
            val oy = if (sy > 0f) 0f else size.height
            val scale = density * if (corner < 2) 0.8f else 1f // сверху чуть меньше, чтобы не спорить со статус-баром
            for (segment in Frost.segments) {
                val a = ((depth - segment.grow * 0.9f) / 0.1f).coerceIn(0f, 1f)
                if (a <= 0f) continue
                drawLine(
                    color = ice.copy(alpha = 0.34f * a * segment.fade),
                    start = Offset(ox + sx * segment.x0 * scale, oy + sy * segment.y0 * scale),
                    end = Offset(ox + sx * segment.x1 * scale, oy + sy * segment.y1 * scale),
                    strokeWidth = segment.width * density,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

// =================================================================================================
// Сосульки
// =================================================================================================

/**
 * Сосульки вдоль нижнего края карточки. Появляются вместе с зимой и вырастают с небольшими отличиями по времени.
 * Рисуются под краем карточки (за ней), поэтому сразу под карточкой нужен небольшой зазор.
 */
@Composable
fun Modifier.icicles(seed: Int, cornerRadius: Dp = 24.dp, maxLength: Dp = 11.dp): Modifier {
    val winter = LocalWinter.current ?: return this
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val body = if (onDark) Color(0xB8CFE8FF) else Color(0xCCB3D6F6)
    val shine = Color(0xB3FFFFFF)
    return this.drawBehind {
        val depth = winter.depth
        if (depth > 0.15f) drawIcicles(seed, depth, cornerRadius.toPx(), maxLength.toPx(), body, shine)
    }
}

private fun DrawScope.drawIcicles(seed: Int, depth: Float, corner: Float, maxLength: Float, body: Color, shine: Color) {
    val w = size.width
    val h = size.height
    val r = min(corner, w / 2f)
    val random = Random(seed)
    val count = (w / (18.dp.toPx())).toInt().coerceIn(6, 30)
    for (i in 0 until count) {
        // Все случайные числа берём до проверок: рисунок не «прыгает» при росте.
        val u = (i + 0.2f + 0.6f * random.nextFloat()) / count
        val short = random.nextFloat() < 0.18f
        val lengthFactor = 0.35f + 0.65f * random.nextFloat()
        val width = (2.6f + 3.2f * random.nextFloat()) * density
        val delay = random.nextFloat() * 0.5f
        val sway = (random.nextFloat() - 0.5f) * 0.5f

        val grow = smooth((depth - 0.2f - delay * 0.5f) / 0.5f)
        if (grow <= 0f) continue
        val x = r + (w - 2f * r) * u
        val length = maxLength * lengthFactor * (if (short) 0.35f else 1f) * grow
        val tipX = x + sway * width

        val icicle = Path().apply {
            moveTo(x - width / 2f, h - 1f)
            lineTo(tipX, h + length)
            lineTo(x + width / 2f, h - 1f)
            close()
        }
        drawPath(icicle, body)
        // Блик на левой грани, чтобы лёд выглядел прозрачным и гранёным.
        val glint = Path().apply {
            moveTo(x - width / 2f, h - 1f)
            lineTo(tipX, h + length)
            lineTo(x - width * 0.05f, h - 1f)
            close()
        }
        drawPath(glint, shine.copy(alpha = shine.alpha * 0.7f))
    }
}
