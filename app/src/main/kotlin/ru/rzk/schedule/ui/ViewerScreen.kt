package ru.rzk.schedule.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

/**
 * Полноэкранный просмотр: свайп между страницами, щипок и двойной тап для увеличения.
 * Фон всегда чёрный, поэтому панели закрыты градиентными плашками — белый текст
 * не сливается с белым PDF.
 *
 * Зум управляется обычными [mutableFloatStateOf] и обновляется прямо в обработчике жеста:
 * никаких корутин на каждое движение пальца. [Animatable] используется только для двойного тапа.
 */
@Composable
fun ViewerScreen(request: ViewerRequest, onClose: () -> Unit) {
    if (request.pages.isEmpty()) {
        onClose()
        return
    }

    val pagerState = rememberPagerState(initialPage = request.startPage) { request.pages.size }
    var zoomed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            ZoomableImage(file = request.pages[page], onZoomChanged = { zoomed = it })
        }

        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(colors = listOf(Color(0xCC000000), Color(0x00000000))))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = Color.White)
                }
                Text(
                    text = request.title + if (request.pages.size > 1) " · ${pagerState.currentPage + 1}/${request.pages.size}" else "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }

        if (request.pages.size > 1) {
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(colors = listOf(Color(0x00000000), Color(0xCC000000))))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(request.pages.size) { index ->
                        val active = index == pagerState.currentPage
                        Box(
                            Modifier.size(if (active) 10.dp else 7.dp)
                                .background(if (active) Color.White else Color(0x80FFFFFF), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File, onZoomChanged: (Boolean) -> Unit) {
    // SAMPLE_BIG = 1 — полное разрешение JPEG (1500px). При зуме до 2.5x картинка остаётся резкой.
    val image by produceState<ImageBitmap?>(initialValue = null, file) {
        value = PageImageCache.load(file, PageImageCache.SAMPLE_BIG)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    val animScale = remember { Animatable(1f) }
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }

    fun clampX(x: Float, s: Float): Float {
        val max = box.width * (s - 1f) / 2f
        return x.coerceIn(-max, max)
    }

    fun clampY(y: Float, s: Float): Float {
        val max = box.height * (s - 1f) / 2f
        return y.coerceIn(-max, max)
    }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        scope.launch {
                            val targetScale = if (scale > 1.01f) 1f else 2.5f
                            val targetX: Float
                            val targetY: Float
                            if (targetScale == 1f) {
                                targetX = 0f; targetY = 0f
                            } else {
                                targetX = clampX((box.width / 2f - tap.x) * (targetScale - 1f), targetScale)
                                targetY = clampY((box.height / 2f - tap.y) * (targetScale - 1f), targetScale)
                            }
                            animScale.snapTo(scale); animX.snapTo(offsetX); animY.snapTo(offsetY)
                            launch { animScale.animateTo(targetScale, tween(320, easing = FastOutSlowInEasing)) }
                            launch { animX.animateTo(targetX, tween(320, easing = FastOutSlowInEasing)) }
                            launch { animY.animateTo(targetY, tween(320, easing = FastOutSlowInEasing)) }
                            scale = targetScale; offsetX = targetX; offsetY = targetY
                            onZoomChanged(targetScale > 1.01f)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1.01f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            val pan = event.calculatePan()
                            scale = newScale
                            if (newScale <= 1.01f) {
                                offsetX = 0f; offsetY = 0f
                            } else {
                                offsetX = clampX(offsetX + pan.x, newScale)
                                offsetY = clampY(offsetY + pan.y, newScale)
                            }
                            onZoomChanged(newScale > 1.01f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            val useAnim = animScale.isRunning || animX.isRunning || animY.isRunning
            val s = if (useAnim) animScale.value else scale
            val tx = if (useAnim) animX.value else offsetX
            val ty = if (useAnim) animY.value else offsetY
            Image(
                bitmap = bitmap,
                contentDescription = "Расписание",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = s; scaleY = s
                    translationX = tx; translationY = ty
                },
            )
        } else {
            Box(Modifier.fillMaxSize().shimmer())
        }
    }
}