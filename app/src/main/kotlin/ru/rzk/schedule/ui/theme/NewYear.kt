package ru.rzk.schedule.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.data.Settings
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Новогоднее оформление: гирлянда, ёлка, леденцовые трости, счётчик дней. Снег и остальная зима остаются.
 * Сезон: с 20 декабря по 9 января.
 */
object NewYear {
    fun isSeason(date: LocalDate): Boolean =
        (date.monthValue == 12 && date.dayOfMonth >= 20) || (date.monthValue == 1 && date.dayOfMonth <= 9)

    /** Пользователь может отключить оформление переключателем; разработчик может показывать его независимо от даты. */
    fun isActive(settings: Settings, today: LocalDate = Dates.today()): Boolean =
        settings.newYear && (settings.devNewYear || isSeason(today))
}

/** Общее состояние новогоднего оформления: режим мигания гирлянды (его переключает нажатие на ёлку). */
@Stable
class NewYearState(val reduceMotion: Boolean) {
    var garlandMode: Int by mutableStateOf(0)
        private set

    fun nextGarlandMode() {
        garlandMode = (garlandMode + 1) % GARLAND_MODES
    }

    companion object {
        const val GARLAND_MODES = 4
    }
}

/** null — новогоднего оформления сейчас нет. */
val LocalNewYear = staticCompositionLocalOf<NewYearState?> { null }

private val BulbColors = listOf(
    Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047),
    Color(0xFF1E88E5), Color(0xFFEC407A), Color(0xFF8E24AA),
)

private fun softStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Яркость лампочки номер [index] в момент [t] (секунды) для режима [mode]: от 0 (погасла) до 1 (горит).
 * 0 — плавное мерцание, 1 — бегущие огни, 2 — поочерёдное мигание, 3 — ровное свечение.
 */
private fun bulbLevel(mode: Int, t: Float, index: Int): Float = when (mode) {
    0 -> 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 2.2f + index * 1.7f))
    1 -> {
        val wave = 0.5f + 0.5f * sin(t * 4.2f - index * 0.65f)
        0.25f + 0.75f * wave * wave * wave
    }
    2 -> 0.2f + 0.8f * softStep(0.5f + 0.5f * sin(t * 5f + (index % 2) * PI.toFloat()))
    else -> 0.88f + 0.12f * sin(t * 2.5f)
}

// =================================================================================================
// Гирлянда
// =================================================================================================

/** Цветная мигающая гирлянда вдоль верхней части экрана, сразу под строкой состояния. Касаний не перехватывает. */
@Composable
fun Garland(state: NewYearState) {
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val band = with(density) { (topInset + 46.dp.toPx()).toDp() }
    var frame by remember { mutableStateOf(0L) }

    if (!state.reduceMotion) {
        LaunchedEffect(Unit) {
            var begin = 0L
            while (true) {
                withFrameNanos { now ->
                    if (begin == 0L) begin = now
                    frame = now - begin // время от старта, а не от включения телефона: так не теряется точность
                }
            }
        }
    }

    // Отдельный слой только под полосу гирлянды: покадровая перерисовка не трогает остальной интерфейс.
    Canvas(Modifier.fillMaxWidth().height(band).graphicsLayer { }) {
        drawGarland(frame / 1_000_000_000f, topInset, state.garlandMode)
    }
}

private fun DrawScope.drawGarland(t: Float, topInset: Float, mode: Int) {
    val d = density
    val w = size.width
    val y0 = topInset + 3f * d
    val swags = max(4, (w / (96f * d)).roundToInt())
    val swagWidth = w / swags
    val droop = 15f * d

    // Провод: цепочка провисающих дуг.
    val wire = Path()
    wire.moveTo(0f, y0)
    for (s in 0 until swags) {
        val x0 = s * swagWidth
        wire.quadraticBezierTo(x0 + swagWidth / 2f, y0 + 2f * droop, x0 + swagWidth, y0)
    }
    drawPath(wire, Color(0xFF173B2B), style = Stroke(width = 1.5f * d))

    // Лампочки: по три на каждую дугу.
    var index = 0
    for (s in 0 until swags) {
        for (k in 1..3) {
            val u = k / 4f
            val x = s * swagWidth + u * swagWidth
            val y = y0 + 4f * droop * u * (1f - u)
            drawBulb(x, y, BulbColors[index % BulbColors.size], bulbLevel(mode, t, index))
            index++
        }
    }
}

private fun DrawScope.drawBulb(x: Float, y: Float, color: Color, level: Float) {
    val d = density
    drawRoundRect(
        Color(0xFF263238),
        topLeft = Offset(x - 2f * d, y - 0.5f * d),
        size = Size(4f * d, 4f * d),
        cornerRadius = CornerRadius(1f * d),
    )
    val center = Offset(x, y + 8.5f * d)
    val r = 4.2f * d
    drawCircle(color.copy(alpha = 0.26f * level), radius = r * 2.7f, center = center)
    drawCircle(color.copy(alpha = 0.42f * level), radius = r * 1.7f, center = center)
    drawCircle(lerp(Color(0xFF37474F), color, level), radius = r, center = center)
    drawCircle(Color.White.copy(alpha = 0.6f * level), radius = r * 0.3f, center = Offset(x - r * 0.3f, center.y - r * 0.35f))
}

// =================================================================================================
// Леденцовые трости
// =================================================================================================

/**
 * Две леденцовые трости торчат из-за верхних углов карточки и тянутся крючками навстречу друг другу;
 * основания утоплены в сугроб (его рисует [snowCap], поэтому эта модификация должна стоять в цепочке раньше).
 * Над карточкой нужен отступ около 34 dp.
 */
@Composable
fun Modifier.candyCanes(): Modifier {
    LocalNewYear.current ?: return this
    return this.drawBehind {
        val inset = 46.dp.toPx()
        drawCane(baseX = inset, hookToRight = true, tilt = -8f)
        drawCane(baseX = size.width - inset, hookToRight = false, tilt = 8f)
    }
}

private fun DrawScope.drawCane(baseX: Float, hookToRight: Boolean, tilt: Float) {
    val d = density
    val thickness = 7f * d
    val shaft = 20f * d
    val radius = 7f * d
    val dir = if (hookToRight) 1f else -1f

    // Путь вдоль оси трости: от основания (уходит за карточку) вверх и крючком в сторону.
    val path = Path().apply {
        moveTo(0f, 8f * d)
        lineTo(0f, -shaft)
        if (hookToRight) {
            arcTo(Rect(0f, -shaft - radius, 2f * radius, -shaft + radius), 180f, 180f, false)
        } else {
            arcTo(Rect(-2f * radius, -shaft - radius, 0f, -shaft + radius), 0f, -180f, false)
        }
        lineTo(dir * 2f * radius, -shaft + 7f * d)
    }

    withTransform({
        translate(left = baseX, top = 0f)
        rotate(degrees = tilt, pivot = Offset(0f, 4f * d))
    }) {
        // Контур, белая основа, красные косые полосы, блик.
        drawPath(path, Color(0x40455A64), style = Stroke(width = thickness + 2f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color(0xFFFAFAFA), style = Stroke(width = thickness, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val measure = PathMeasure()
        measure.setPath(path, false)
        val length = measure.length
        val stripe = 5.5f * d
        val period = 11f * d
        var s = 4f * d
        while (s + stripe < length - thickness * 0.4f) {
            val p0 = measure.getPosition(s)
            val t0 = measure.getTangent(s)
            val p1 = measure.getPosition(s + stripe)
            val t1 = measure.getTangent(s + stripe)
            val half = thickness / 2f
            val n0 = Offset(-t0.y * half, t0.x * half)
            val n1 = Offset(-t1.y * half, t1.x * half)
            val lean = thickness * 0.65f // сдвиг вдоль трости даёт «косую» полосу
            val stripePath = Path().apply {
                moveTo(p0.x + n0.x, p0.y + n0.y)
                lineTo(p1.x + n1.x, p1.y + n1.y)
                lineTo(p1.x - n1.x + t1.x * lean, p1.y - n1.y + t1.y * lean)
                lineTo(p0.x - n0.x + t0.x * lean, p0.y - n0.y + t0.y * lean)
                close()
            }
            drawPath(stripePath, Color(0xFFD32F2F))
            s += period
        }
        translate(left = -thickness * 0.22f) {
            drawPath(path, Color.White.copy(alpha = 0.45f), style = Stroke(width = 1.3f * d, cap = StrokeCap.Round))
        }
    }
}

// =================================================================================================
// Ёлка, с которой можно играть
// =================================================================================================

private val OrnamentColors = listOf(
    Color(0xFFC62828), Color(0xFFFBC02D), Color(0xFF1565C0),
    Color(0xFFD81B60), Color(0xFFB0BEC5), Color(0xFF00897B),
)

/** Места шаров на ёлке (в условных единицах 64×84). */
private val OrnamentSpots = listOf(Offset(24f, 40f), Offset(41f, 46f), Offset(19f, 60f), Offset(33f, 57f), Offset(46f, 64f))

/** Места мелких огоньков на ветках. */
private val TreeLights = listOf(
    Offset(28f, 18f), Offset(36f, 24f), Offset(25f, 34f), Offset(40f, 36f), Offset(22f, 50f),
    Offset(34f, 47f), Offset(43f, 55f), Offset(27f, 63f), Offset(38f, 67f),
)

/**
 * Маленькая ёлка на экране. Нажатия:
 *  • на ветки: ёлка качается, с неё сыплется снег, а гирлянда меняет режим мигания;
 *  • на шар: шар меняет цвет;
 *  • на звезду: вспышка и фейерверк искр.
 */
@Composable
fun NewYearTree(state: NewYearState, modifier: Modifier = Modifier) {
    val ornaments = remember { mutableStateListOf(0, 1, 2, 3, 4) }
    val sparks = remember { Sparks() }
    val shake = remember { Animatable(0f) }
    val starFlash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var frame by remember { mutableStateOf(0L) }

    if (!state.reduceMotion) {
        LaunchedEffect(Unit) {
            var begin = 0L
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (begin == 0L) begin = now
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    sparks.step(dt)
                    frame = now - begin
                }
            }
        }
    }

    Canvas(
        modifier
            .size(64.dp, 84.dp)
            .pointerInput(state) {
                detectTapGestures { tap ->
                    val u = size.width / 64f
                    sparks.unit = u
                    val ornament = OrnamentSpots.indexOfFirst { (tap - it * u).getDistance() < 10f * u }
                    when {
                        ornament >= 0 -> {
                            ornaments[ornament] = (ornaments[ornament] + 1) % OrnamentColors.size
                            sparks.burst(tap.x, tap.y, 9, listOf(OrnamentColors[ornaments[ornament]], Color.White), 55f)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        (tap - Offset(32f * u, 7f * u)).getDistance() < 10f * u -> {
                            sparks.burst(32f * u, 7f * u, 30, listOf(Color(0xFFFFD54F), Color.White, Color(0xFFFF8A65)), 95f)
                            scope.launch {
                                starFlash.snapTo(1f)
                                starFlash.animateTo(0f, tween(900))
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        else -> {
                            state.nextGarlandMode()
                            sparks.puff(14f * u, 50f * u, 14f * u, 14)
                            scope.launch {
                                shake.snapTo(0f)
                                shake.animateTo(7f, tween(70))
                                shake.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = Spring.StiffnessLow))
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            },
    ) {
        val u = size.width / 64f
        sparks.unit = u
        val t = frame / 1_000_000_000f
        rotate(degrees = shake.value, pivot = Offset(32f * u, 80f * u)) {
            drawTree(u, t, state.garlandMode, ornaments, starFlash.value)
        }
        sparks.draw(this)
    }
}

private fun DrawScope.drawTree(u: Float, t: Float, mode: Int, ornaments: List<Int>, starFlash: Float) {
    // Ствол.
    drawRoundRect(
        Color(0xFF6D4C41), topLeft = Offset(28f * u, 70f * u), size = Size(8f * u, 11f * u),
        cornerRadius = CornerRadius(1.5f * u),
    )
    // Три яруса: верхний, средний, нижний (вершина, низ, полуширина).
    val tiers = listOf(Triple(6f, 34f, 15f), Triple(18f, 52f, 21f), Triple(32f, 71f, 28f))
    for ((apex, base, half) in tiers) {
        val right = 32f + half
        val seg = 2f * half / 4f
        val path = Path().apply {
            moveTo(32f * u, apex * u)
            lineTo(right * u, (base - 3f) * u)
            for (k in 0 until 4) {
                val xr = right - k * seg
                val xl = xr - seg
                quadraticBezierTo((xr + xl) / 2f * u, (base + 3f) * u, xl * u, (base - 3f) * u)
            }
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFF43A047), Color(0xFF1B5E20)), startY = apex * u, endY = base * u))
        // Снег на кончиках веток.
        for (k in 0..4) drawCircle(Color.White.copy(alpha = 0.92f), radius = 1.7f * u, center = Offset((right - k * seg) * u, (base - 3f) * u))
    }
    // Мелкие огоньки (тот же режим мигания, что у гирлянды).
    for ((i, spot) in TreeLights.withIndex()) {
        val color = BulbColors[(i + 2) % BulbColors.size]
        val level = bulbLevel(mode, t, i + 30)
        drawCircle(color.copy(alpha = 0.35f * level), radius = 3.2f * u, center = spot * u)
        drawCircle(lerp(Color(0xFF37474F), color, level), radius = 1.3f * u, center = spot * u)
    }
    // Шары.
    for ((i, spot) in OrnamentSpots.withIndex()) {
        val color = OrnamentColors[ornaments[i] % OrnamentColors.size]
        val c = spot * u
        drawRoundRect(Color(0xFFFFD54F), topLeft = Offset(c.x - 1.2f * u, c.y - 4.6f * u), size = Size(2.4f * u, 1.8f * u), cornerRadius = CornerRadius(0.6f * u))
        drawCircle(color, radius = 3.3f * u, center = c)
        drawCircle(Color.White.copy(alpha = 0.6f), radius = 0.9f * u, center = Offset(c.x - 1.1f * u, c.y - 1.2f * u))
    }
    // Звезда: пятиконечная, со свечением, которое вспыхивает после нажатия.
    val pulse = 0.85f + 0.15f * sin(t * 3f)
    val starCenter = Offset(32f * u, 6.5f * u)
    drawCircle(Color(0xFFFFE082).copy(alpha = (0.22f + 0.4f * starFlash) * pulse), radius = (9f + 6f * starFlash) * u, center = starCenter)
    val star = Path()
    for (k in 0 until 10) {
        val angle = (-90f + k * 36f) * PI.toFloat() / 180f
        val radius = if (k % 2 == 0) 6f * u else 2.7f * u
        val x = starCenter.x + cos(angle) * radius
        val y = starCenter.y + sin(angle) * radius
        if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    drawPath(star, Color(0xFFFFD54F))
}

/** Искры и снежок от нажатий на ёлку. Небольшой набор частиц, который переиспользуется по кругу. */
private class Sparks {
    var unit = 1f
    private val cap = 80
    private val px = FloatArray(cap)
    private val py = FloatArray(cap)
    private val vx = FloatArray(cap)
    private val vy = FloatArray(cap)
    private val life = FloatArray(cap)
    private val total = FloatArray(cap)
    private val dotSize = FloatArray(cap)
    private val argb = IntArray(cap)
    private val snow = BooleanArray(cap)
    private var cursor = 0
    private val random = Random(5)

    private fun add(x: Float, y: Float, dx: Float, dy: Float, seconds: Float, s: Float, color: Int, isSnow: Boolean) {
        val i = cursor
        cursor = (cursor + 1) % cap
        px[i] = x; py[i] = y; vx[i] = dx; vy[i] = dy
        life[i] = seconds; total[i] = seconds; dotSize[i] = s; argb[i] = color; snow[i] = isSnow
    }

    fun burst(cx: Float, cy: Float, count: Int, palette: List<Color>, speed: Float) {
        repeat(count) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val v = speed * (0.4f + 0.6f * random.nextFloat()) * unit
            add(
                cx, cy, cos(angle) * v, sin(angle) * v,
                0.7f + 0.5f * random.nextFloat(), (1.2f + 1.6f * random.nextFloat()) * unit,
                palette[random.nextInt(palette.size)].toArgb(), false,
            )
        }
    }

    fun puff(left: Float, right: Float, top: Float, count: Int) {
        repeat(count) {
            add(
                left + (right - left) * random.nextFloat(), top + 20f * unit * random.nextFloat(),
                (random.nextFloat() - 0.5f) * 18f * unit, (10f + 22f * random.nextFloat()) * unit,
                1.1f + 0.7f * random.nextFloat(), (1f + 1.1f * random.nextFloat()) * unit,
                0xFFFFFFFF.toInt(), true,
            )
        }
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        for (i in 0 until cap) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            px[i] += vx[i] * dt
            py[i] += vy[i] * dt
            if (!snow[i]) vy[i] += 90f * unit * dt // искры падают, снег плывёт ровно
            vx[i] *= (1f - 1.4f * dt).coerceAtLeast(0f)
        }
    }

    fun draw(scope: DrawScope) {
        with(scope) {
            for (i in 0 until cap) {
                if (life[i] <= 0f) continue
                val alpha = (life[i] / total[i]).coerceIn(0f, 1f)
                val color = Color(argb[i]).copy(alpha = alpha)
                if (snow[i]) {
                    drawCircle(color, radius = dotSize[i], center = Offset(px[i], py[i]))
                } else {
                    // Искра: маленький крестик с точкой.
                    val s = dotSize[i] * 1.8f
                    drawLine(color, Offset(px[i] - s, py[i]), Offset(px[i] + s, py[i]), strokeWidth = 1.2f * unit, cap = StrokeCap.Round)
                    drawLine(color, Offset(px[i], py[i] - s), Offset(px[i], py[i] + s), strokeWidth = 1.2f * unit, cap = StrokeCap.Round)
                    drawCircle(color, radius = dotSize[i] * 0.7f, center = Offset(px[i], py[i]))
                }
            }
        }
    }
}

// =================================================================================================
// Счётчик дней до Нового года
// =================================================================================================

/** Плашка «До Нового года: N дней». В последний день считает часы и минуты, а в январе поздравляет. */
@Composable
fun NewYearCountdown(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(ZonedDateTime.now(Dates.zone)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now(Dates.zone)
            delay(20_000)
        }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Celebration, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(countdownText(now), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun countdownText(now: ZonedDateTime): String {
    if (now.monthValue == 1) return "С Новым ${now.year} годом!"
    val newYear = LocalDate.of(now.year + 1, 1, 1)
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), newYear)
    if (days >= 2) return "До Нового года: $days ${plural(days, "день", "дня", "дней")}"
    val left = Duration.between(now, newYear.atStartOfDay(now.zone))
    val hours = left.toHours()
    val minutes = left.minusHours(hours).toMinutes()
    return "До Нового года: $hours ч $minutes мин"
}

private fun plural(n: Long, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1L -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
