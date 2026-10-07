package ru.rzk.schedule.ui

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Полноэкранный просмотр: свайп между страницами, щипок и двойной тап для увеличения.
 * Фон всегда чёрный, поэтому панели закрыты градиентными плашками — белый текст
 * не сливается с белым PDF.
 */
@Composable
fun ViewerScreen(request: ViewerRequest, onClose: () -> Unit) {
    // Защита от пустого списка: нечего показывать.
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
        ) { page ->
            ZoomableImage(file = request.pages[page], onZoomChanged = { zoomed = it })
        }

        // Верхняя панель: градиент из чёрного в прозрачный.
        // Так текст и кнопка «Закрыть» читаются на любом фоне.
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xCC000000), Color(0x00000000)),
                    ),
                )
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

        // Нижние точки: тоже на градиентной плашке снизу.
        if (request.pages.size > 1) {
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x00000000), Color(0xCC000000)),
                        ),
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(request.pages.size) { index ->
                        val active = index == pagerState.currentPage
                        Box(
                            Modifier.size(if (active) 10.dp else 7.dp)
                                .background(
                                    if (active) Color.White else Color(0x80FFFFFF),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File, onZoomChanged: (Boolean) -> Unit) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.Default) {
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
        }
    }

    // Анимируемые значения: и жест, и двойной тап двигают их.
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

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
            // Двойной тап: плавная анимация зума и смещения.
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (scale.value > 1.01f) {
                                // Выход из зума — обратно к 1x, в центр.
                                launch { scale.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
                                launch { offsetX.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
                                launch { offsetY.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
                                onZoomChanged(false)
                            } else {
                                // Вход в зум — точка тапа остаётся под пальцем.
                                val target = 2.5f
                                val targetX = clampX((box.width / 2f - tap.x) * (target - 1f), target)
                                val targetY = clampY((box.height / 2f - tap.y) * (target - 1f), target)
                                launch { scale.animateTo(target, tween(320, easing = FastOutSlowInEasing)) }
                                launch { offsetX.animateTo(targetX, tween(320, easing = FastOutSlowInEasing)) }
                                launch { offsetY.animateTo(targetY, tween(320, easing = FastOutSlowInEasing)) }
                                onZoomChanged(true)
                            }
                        }
                    },
                )
            }
            // Щипок и панорамирование: мгновенно, без анимации — палец ведёт.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale.value > 1.01f) {
                            val newScale = (scale.value * event.calculateZoom()).coerceIn(1f, 6f)
                            val pan = event.calculatePan()
                            scope.launch {
                                scale.snapTo(newScale)
                                if (newScale <= 1.01f) {
                                    offsetX.snapTo(0f)
                                    offsetY.snapTo(0f)
                                } else {
                                    offsetX.snapTo(clampX(offsetX.value + pan.x, newScale))
                                    offsetY.snapTo(clampY(offsetY.value + pan.y, newScale))
                                }
                                onZoomChanged(newScale > 1.01f)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Расписание",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                },
            )
        } else {
            Box(Modifier.fillMaxSize().shimmer())
        }
    }
}