package ru.rzk.schedule.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * «Отслоение» фото щипком двумя пальцами, как в Telegram.
 *
 * Пока два пальца на карточке, картинка отрывается от неё и летает поверх всего интерфейса: следует за пальцами
 * и увеличивается. Фон затемняется. Стоит отпустить хотя бы один палец, как картинка плавно возвращается на место.
 * Обычные нажатие (открыть на весь экран) и прокрутка одним пальцем работают как раньше.
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

    private var releasing: Job? = null

    val visible: Boolean get() = owner != null

    /** Насколько затемнён фон: чем больше увеличение, тем темнее. */
    val dim: Float get() = ((scale - 1f) / 1.5f).coerceIn(0f, 1f) * 0.7f

    fun begin(owner: Any, bitmap: ImageBitmap, bounds: Rect, cornerRadius: Float) {
        // Если картинку «поймали» во время возврата — продолжаем с того места, где она сейчас.
        val resume = this.owner == owner && releasing?.isActive == true
        releasing?.cancel()
        releasing = null
        if (!resume) {
            scale = 1f
            offset = Offset.Zero
            this.bitmap = bitmap
        }
        this.owner = owner
        this.bounds = bounds
        this.cornerRadius = cornerRadius
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

    /** Плавно возвращает картинку на место и убирает её из оверлея. */
    fun release() {
        if (owner == null) return
        releasing?.cancel()
        val startScale = scale
        val startOffset = offset
        releasing = scope.launch {
            animate(0f, 1f, animationSpec = tween(300, easing = FastOutSlowInEasing)) { t, _ ->
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

/** Слой поверх всего интерфейса: затемнение и летающая картинка. Ставится последним в корне приложения. */
@Composable
fun PinchZoomOverlay(state: PinchZoomState) {
    val bitmap = state.bitmap ?: return
    if (!state.visible) return
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.dim)))
        Image(
            bitmap = bitmap,
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
    cornerRadiusPx: Float,
    bitmap: () -> ImageBitmap?,
    loadSharp: suspend () -> ImageBitmap?,
): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(owner, state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var zooming = false
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }

                    if (!zooming && pressed >= 2) {
                        val current = bitmap()
                        val layout = coordinates
                        if (current != null && layout != null && layout.isAttached) {
                            zooming = true
                            state.begin(owner, current, layout.boundsInRoot(), cornerRadiusPx)
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
                if (zooming) state.release()
            }
        }
}
