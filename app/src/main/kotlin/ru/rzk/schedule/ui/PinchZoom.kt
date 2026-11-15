package ru.rzk.schedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.rzk.schedule.ui.theme.DriftShape
import ru.rzk.schedule.ui.theme.LocalNewYear
import ru.rzk.schedule.ui.theme.LocalWinter
import ru.rzk.schedule.ui.theme.cardEdgeY
import ru.rzk.schedule.ui.theme.drawCane
import ru.rzk.schedule.ui.theme.smooth
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * «Отслоение» фото щипком двумя пальцами, как в Telegram / iOS Photos.
 *
 * Пока два пальца на карточке, картинка отрывается от неё и летает поверх всего интерфейса: следует за пальцами
 * и увеличивается. Фон затемняется. А если на карточке лежал зимний сугроб, висели сосульки или леденцовые трости —
 * при отрыве карточки они красиво осыпаются вниз!
 */
val LocalPinchZoom = staticCompositionLocalOf<PinchZoomState?> { null }

@Stable
class PinchZoomState(private val scope: CoroutineScope) {
    /** Кто сейчас «оторван» (ключ карточки); null — ничего не летает. */
    var owner: Any? by mutableStateOf(null)
        private set
    var bitmap: ImageBitmap? by mutableStateOf(null)
        private set
    /** Где карточка была на экране (в координатах всего окна). */
    var bounds: Rect by mutableStateOf(Rect.Zero)
        private set
    var cornerRadius: Float by mutableStateOf(0f)
        private set
    var scale: Float by mutableStateOf(1f)
        private set
    /** Смещение центра картинки от её исходного места. */
    var offset: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Пальцы сейчас на картинке (а не идёт возврат). */
    var holding: Boolean by mutableStateOf(false)
        private set

    /** Зерно карточки, с которой сейчас осыпается снег (и счётчик запуска лавины). */
    var snowBurstId: Int by mutableStateOf(0)
        private set
    var snowBurstSeed: Int by mutableStateOf(0)
        private set
    var snowBurstDepth: Float by mutableStateOf(0f)
        private set
    var snowBurstBounds: Rect by mutableStateOf(Rect.Zero)
        private set
    var snowBurstNewYear: Boolean by mutableStateOf(false)
        private set

    private var releasing: Job? = null

    val visible: Boolean get() = owner != null

    /** Заходит ли картинка под строку состояния (верхнюю системную панель высотой [topInset] пикселей). */
    fun coversTop(topInset: Float): Boolean =
        visible && bounds.center.y + offset.y - bounds.height * scale / 2f <= topInset

    /** Заходит ли картинка под системную панель внизу высотой [bottomInset] пикселей. */
    fun coversBottom(bottomInset: Float, screenHeight: Float): Boolean =
        visible && bounds.center.y + offset.y + bounds.height * scale / 2f >= screenHeight - bottomInset

    /** Насколько затемнён фон: чем больше увеличение, тем темнее. */
    val dim: Float get() = ((scale - 1f) / 1.5f).coerceIn(0f, 1f) * 0.7f

    fun begin(
        owner: Any,
        bitmap: ImageBitmap,
        bounds: Rect,
        cornerRadius: Float,
        seed: Int = 0,
        snowDepth: Float = 0f,
        hasNewYear: Boolean = false,
    ) {
        // Если картинку «поймали» во время возврата — продолжаем с того места, где она сейчас.
        val resume = this.owner == owner && releasing?.isActive == true
        releasing?.cancel()
        releasing = null
        if (!resume) {
            scale = 1f
            offset = Offset.Zero
            this.bitmap = bitmap
            if (snowDepth > 0.02f || hasNewYear) {
                snowBurstSeed = seed
                snowBurstDepth = snowDepth
                snowBurstBounds = bounds
                snowBurstNewYear = hasNewYear
                snowBurstId++
            }
        }
        this.owner = owner
        this.bounds = bounds
        this.cornerRadius = cornerRadius
        holding = true
    }

    /** Подменяет картинку на более чёткую, когда она загрузилась. */
    fun upgrade(owner: Any, sharper: ImageBitmap) {
        if (this.owner == owner) bitmap = sharper
    }

    /**
     * Применяет движение пальцев. Все точки — в координатах карточки. Точка картинки под прежним центром
     * между пальцами остаётся под новым, поэтому картинка «прилипает» к пальцам.
     */
    fun update(zoom: Float, centroid: Offset, previousCentroid: Offset, size: Size) {
        if (!centroid.isSpecified || !previousCentroid.isSpecified) return
        val target = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val z = target / scale
        val center = Offset(size.width / 2f, size.height / 2f)
        offset = (centroid - center) - (previousCentroid - center - offset) * z
        scale = target
    }

    /** На пружине возвращает картинку на место и убирает её из оверлея. */
    fun release() {
        if (owner == null) return
        holding = false
        releasing?.cancel()
        val startScale = scale
        val startOffset = offset
        releasing = scope.launch {
            animate(0f, 1f, animationSpec = Motion.iosSpring(stiffness = 460f)) { t, _ ->
                scale = startScale + (1f - startScale) * t
                offset = startOffset * (1f - t)
            }
            owner = null
            bitmap = null
            scale = 1f
            offset = Offset.Zero
        }
    }

    private companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 6f
    }
}

/** Создаёт состояние и выдаёт его всем карточкам ниже по дереву. Оверлей рисуется отдельно: [PinchZoomOverlay]. */
@Composable
fun rememberPinchZoomState(): PinchZoomState {
    val scope = rememberCoroutineScope()
    return remember { PinchZoomState(scope) }
}

/** Слой поверх всего интерфейса: затемнение, осыпающийся снег и летающая картинка. Ставится последним в корне приложения. */
@Composable
fun PinchZoomOverlay(state: PinchZoomState) {
    val density = LocalDensity.current
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val avalanche = remember { SnowAvalanche() }
    var avalancheFrame by remember { mutableStateOf(0L) }

    LaunchedEffect(state.snowBurstId) {
        if (state.snowBurstId > 0) {
            avalanche.spawn(
                bounds = state.snowBurstBounds,
                seed = state.snowBurstSeed,
                depth = state.snowBurstDepth,
                cornerPx = state.cornerRadius,
                density = density.density,
                hasCanes = state.snowBurstNewYear,
            )
            var last = 0L
            while (avalanche.isAlive) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    avalanche.step(dt)
                    avalancheFrame = now
                }
            }
        }
    }

    val bitmap = state.bitmap
    val showZoom = state.visible && bitmap != null
    if (!showZoom && !avalanche.isAlive) return

    // Назад, пока картинка в воздухе, просто возвращает её на место.
    BackHandler(enabled = showZoom) { state.release() }

    Box(
        Modifier.fillMaxSize().then(
            if (showZoom) {
                Modifier.pointerInput(state) {
                    // Пока картинка оторвана, остальной интерфейс недоступен: новые касания (например, третьим пальцем)
                    // поглощаются здесь и до кнопок не доходят. Два пальца, которые держат картинку, этот слой не затрагивает.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            // Страховка: если картинка вдруг «зависла» без пальцев на ней, любое касание вернёт её на место.
                            if (!state.holding && event.changes.any { it.pressed && !it.previousPressed }) state.release()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            } else Modifier,
        ),
    ) {
        if (showZoom) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.dim)))
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset { IntOffset(state.bounds.left.roundToInt(), state.bounds.top.roundToInt()) }
                    .size(with(density) { state.bounds.width.toDp() }, with(density) { state.bounds.height.toDp() })
                    .graphicsLayer {
                        scaleX = state.scale
                        scaleY = state.scale
                        translationX = state.offset.x
                        translationY = state.offset.y
                        shape = RoundedCornerShape(state.cornerRadius)
                        clip = true
                        // Тень появляется, когда картинка поднялась над экраном.
                        shadowElevation = 16.dp.toPx() * (state.scale - 1f).coerceIn(0f, 1f)
                    },
            )
        }
        // Лавина снега, сосулек и леденцов, осыпающихся с поднятой карточки поверх экрана.
        if (avalanche.isAlive) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { }) {
                avalanche.draw(this, onDark, avalancheFrame)
            }
        }
    }
}

/**
 * Физика осыпающегося сугроба, сосулек и леденцовых тростей, когда карточку расписания отрывают двумя пальцами.
 * Сугроб распадается на десятки пушистых снежных комочков и снежинок, сосульки откалываются и падают вниз,
 * а леденцы кувыркаются.
 */
private class SnowAvalanche {
    private var count = 0
    private var x = FloatArray(0)
    private var y = FloatArray(0)
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    private var radius = FloatArray(0)
    private var life = FloatArray(0)
    private var maxLife = FloatArray(0)
    private var delay = FloatArray(0)
    // 0 = комочек снега, 1 = осколок сосульки, 2 = левая трость, 3 = правая трость
    private var kind = IntArray(0)
    private var icicleLen = FloatArray(0)
    private var angle = FloatArray(0)
    private var spin = FloatArray(0)
    private var density = 1f

    var isAlive: Boolean by mutableStateOf(false)
        private set

    fun spawn(
        bounds: Rect,
        seed: Int,
        depth: Float,
        cornerPx: Float,
        density: Float,
        hasCanes: Boolean,
    ) {
        this.density = density
        val random = Random(seed xor System.nanoTime().toInt())
        val w = bounds.width
        if (w <= 1f) return
        val r = min(cornerPx, w / 2f)
        val maxHeight = 12f * density
        val shape = DriftShape(seed)

        val snowBalls = if (depth > 0.02f) 96 else 0
        val iciclesCount = if (depth > 0.15f) (w / (18f * density)).toInt().coerceIn(6, 30) else 0
        val canesCount = if (hasCanes) 2 else 0
        val total = snowBalls + iciclesCount + canesCount
        if (total == 0) return

        x = FloatArray(total)
        y = FloatArray(total)
        vx = FloatArray(total)
        vy = FloatArray(total)
        radius = FloatArray(total)
        life = FloatArray(total)
        maxLife = FloatArray(total)
        delay = FloatArray(total)
        kind = IntArray(total)
        icicleLen = FloatArray(total)
        angle = FloatArray(total)
        spin = FloatArray(total)

        var idx = 0

        // 1. Сугроб вдоль верхней кромки карточки рассыпается на множество комочков и снежной пыли.
        for (i in 0 until snowBalls) {
            val u = (i + random.nextFloat()) / snowBalls
            val px = u * w
            val soft = r * 0.8f
            val taper = if (soft < 1f) 1f else smooth(px / soft) * smooth((w - px) / soft)
            val h = shape.height(u) * taper * depth * maxHeight
            val edgeY = cardEdgeY(px, w, r)
            x[idx] = bounds.left + px
            y[idx] = bounds.top + edgeY - h * (0.25f + 0.75f * random.nextFloat())
            // Края разлетаются чуть в стороны, середина осыпается водопадом вниз.
            vx[idx] = ((u - 0.5f) * 85f + (random.nextFloat() - 0.5f) * 75f) * density
            vy[idx] = (-25f + random.nextFloat() * 90f) * density
            val clump = random.nextFloat()
            radius[idx] = (1.8f + 4.8f * clump * clump) * density * (0.6f + 0.4f * depth)
            val dur = 0.75f + 0.65f * random.nextFloat()
            life[idx] = dur
            maxLife[idx] = dur
            // Снег начинает сыпаться чуть неравномерно (за первые 120 мс) — получается живой обвал сугроба.
            delay[idx] = random.nextFloat() * 0.12f
            kind[idx] = 0
            idx++
        }

        // 2. Сосульки вдоль нижнего края карточки откалываются и падают вниз.
        if (iciclesCount > 0) {
            val icicleRand = Random(seed * 31 + 7)
            val maxLen = 11f * density
            for (i in 0 until iciclesCount) {
                val u = (i + 0.2f + 0.6f * icicleRand.nextFloat()) / iciclesCount
                val short = icicleRand.nextFloat() < 0.18f
                val lengthFactor = 0.35f + 0.65f * icicleRand.nextFloat()
                val width = (2.6f + 3.2f * icicleRand.nextFloat()) * density
                val d = icicleRand.nextFloat() * 0.5f
                icicleRand.nextFloat() // sway

                val grow = smooth((depth - 0.2f - d * 0.5f) / 0.5f)
                if (grow <= 0f) continue
                val px = r + (w - 2f * r) * u
                val length = maxLen * lengthFactor * (if (short) 0.35f else 1f) * grow

                x[idx] = bounds.left + px
                y[idx] = bounds.bottom - 1f
                vx[idx] = (random.nextFloat() - 0.5f) * 35f * density
                vy[idx] = (20f + random.nextFloat() * 60f) * density
                radius[idx] = width
                icicleLen[idx] = length
                angle[idx] = 0f
                spin[idx] = (random.nextFloat() - 0.5f) * 140f
                val dur = 0.7f + 0.4f * random.nextFloat()
                life[idx] = dur
                maxLife[idx] = dur
                delay[idx] = random.nextFloat() * 0.09f
                kind[idx] = 1
                idx++
            }
        }

        // 3. Леденцовые трости в новогоднем сезоне тоже срываются с краёв и падают, покачиваясь.
        if (hasCanes) {
            val inset = 46f * density
            for (side in 0..1) {
                x[idx] = if (side == 0) bounds.left + inset else bounds.right - inset
                y[idx] = bounds.top
                vx[idx] = (if (side == 0) -35f else 35f) * density
                vy[idx] = -40f * density
                angle[idx] = if (side == 0) -8f else 8f
                spin[idx] = if (side == 0) -110f else 110f
                val dur = 0.85f
                life[idx] = dur
                maxLife[idx] = dur
                delay[idx] = 0f
                kind[idx] = if (side == 0) 2 else 3
                idx++
            }
        }

        count = idx
        isAlive = count > 0
    }

    fun step(dt: Float) {
        if (!isAlive || dt <= 0f) return
        val gravity = 980f * density
        var anyAlive = false
        for (i in 0 until count) {
            if (life[i] <= 0f) continue
            if (delay[i] > 0f) {
                delay[i] -= dt
                anyAlive = true
                continue
            }
            life[i] -= dt
            if (life[i] <= 0f) continue
            anyAlive = true
            vy[i] += gravity * dt
            // Лёгкое сопротивление воздуха и покачивание снежных хлопьев при падении.
            vx[i] *= (1f - 1.2f * dt).coerceAtLeast(0f)
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            angle[i] += spin[i] * dt
        }
        isAlive = anyAlive
    }

    @Suppress("UNUSED_PARAMETER")
    fun draw(scope: DrawScope, onDark: Boolean, frame: Long) {
        val snowColor = if (onDark) Color(0xFFF2F8FF) else Color(0xFFD2E8FF)
        val snowRim = if (onDark) Color(0x55FFFFFF) else Color(0x883F7FBF)
        val icicleBody = if (onDark) Color(0xD8CFE8FF) else Color(0xDDB3D6F6)
        with(scope) {
            for (i in 0 until count) {
                if (life[i] <= 0f) continue
                val a = (life[i] / (maxLife[i] * 0.45f)).coerceIn(0f, 1f)
                when (kind[i]) {
                    0 -> {
                        val r = radius[i] * (0.65f + 0.35f * (life[i] / maxLife[i]))
                        val center = Offset(x[i], y[i])
                        drawCircle(snowRim.copy(alpha = snowRim.alpha * a * 0.6f), radius = r * 1.35f, center = center)
                        drawCircle(snowColor.copy(alpha = a), radius = r, center = center)
                    }
                    1 -> {
                        val w = radius[i]
                        val len = icicleLen[i]
                        val cx = x[i]
                        val cy = y[i]
                        rotate(degrees = angle[i], pivot = Offset(cx, cy)) {
                            val path = Path().apply {
                                moveTo(cx - w / 2f, cy)
                                lineTo(cx, cy + len)
                                lineTo(cx + w / 2f, cy)
                                close()
                            }
                            drawPath(path, icicleBody.copy(alpha = icicleBody.alpha * a))
                        }
                    }
                    2, 3 -> {
                        val hookRight = kind[i] == 2
                        drawCane(
                            baseX = x[i],
                            hookToRight = hookRight,
                            tilt = angle[i],
                            topY = y[i],
                            alpha = a,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Жест «щипок двумя пальцами» для карточки с картинкой. Одним пальцем ничего не перехватывает (нажатие и прокрутка
 * работают как обычно); как только на карточке второй палец, события забираем себе.
 *
 * @param bitmap картинка, которая сейчас показана в карточке (null — ещё не загрузилась, жест не включается)
 * @param loadSharp загрузка более чёткой версии; подменяет картинку на лету
 */
fun Modifier.pinchPeek(
    state: PinchZoomState,
    owner: Any,
    seed: Int,
    cornerRadiusPx: Float,
    bitmap: () -> ImageBitmap?,
    loadSharp: suspend () -> ImageBitmap?,
): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val winter = LocalWinter.current
    val newYear = LocalNewYear.current

    // Если карточка исчезла с экрана, пока её держали (например, расписание обновилось), картинку нужно вернуть.
    DisposableEffect(owner, state) {
        onDispose { if (state.owner == owner && state.holding) state.release() }
    }

    this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(owner, state, winter, newYear) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var zooming = false
                try {
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }

                    if (!zooming && pressed >= 2) {
                        val current = bitmap()
                        val layout = coordinates
                        if (current != null && layout != null && layout.isAttached) {
                            zooming = true
                            // Берём полный прямоугольник карточки, а не видимую его часть: boundsInRoot() обрезает его
                            // по границам прокручиваемой области, и картинка при отрыве «сжималась» бы.
                            val fullBounds = Rect(layout.positionInRoot(), layout.size.toSize())
                            val currentDepth = winter?.cardDepth(seed) ?: 0f
                            // Стряхиваем сугроб и сосульки с этой карточки и запускаем снежную лавину!
                            winter?.shakeOff(seed)
                            state.begin(
                                owner = owner,
                                bitmap = current,
                                bounds = fullBounds,
                                cornerRadius = cornerRadiusPx,
                                seed = seed,
                                snowDepth = currentDepth,
                                hasNewYear = newYear != null,
                            )
                            scope.launch { loadSharp()?.let { state.upgrade(owner, it) } }
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }

                    if (zooming) {
                        if (pressed >= 2) {
                            // Двигаем только по тем пальцам, что были на экране и в прошлом кадре: у только что
                            // прикоснувшегося пальца ещё нет «прежней» позиции.
                            val continuing = event.changes.count { it.pressed && it.previousPressed }
                            if (continuing >= 2) {
                                state.update(
                                    zoom = event.calculateZoom(),
                                    centroid = event.calculateCentroid(),
                                    previousCentroid = event.calculateCentroid(useCurrent = false),
                                    size = Size(size.width.toFloat(), size.height.toFloat()),
                                )
                            }
                            event.changes.forEach { it.consume() }
                        } else {
                            // Отпустили палец: картинка едет назад, остаток жеста «съедаем», чтобы он не стал
                            // прокруткой или нажатием.
                            event.changes.forEach { it.consume() }
                            state.release()
                            while (true) {
                                val rest = awaitPointerEvent()
                                rest.changes.forEach { it.consume() }
                                if (rest.changes.none { it.pressed }) break
                            }
                            return@awaitEachGesture
                        }
                    }
                } while (event.changes.any { it.pressed })
                } finally {
                    // Жест оборвался (карточка пропала, система отменила касание): картинка не должна остаться в воздухе.
                    if (zooming && state.holding) state.release()
                }
            }
        }
}
