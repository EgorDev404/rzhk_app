package ru.rzk.schedule.ui

import android.graphics.BitmapFactory
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.withContext
import java.io.File

/** Полноэкранный просмотр: свайп между страницами, щипок и двойной тап для увеличения. */
@Composable
fun ViewerScreen(request: ViewerRequest, onClose: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = request.startPage) { request.pages.size }
    var zoomed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(file = request.pages[page], onZoomChanged = { zoomed = it })
        }

        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = Color.White)
            }
            Text(
                text = request.title + if (request.pages.size > 1) " · ${pagerState.currentPage + 1}/${request.pages.size}" else "",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        if (request.pages.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(request.pages.size) { index ->
                    Box(
                        Modifier.size(if (index == pagerState.currentPage) 10.dp else 7.dp)
                            .background(if (index == pagerState.currentPage) Color.White else Color(0x80FFFFFF), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File, onZoomChanged: (Boolean) -> Unit) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.Default) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = box.width * (s - 1f) / 2f
        val maxY = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomChanged(false)
                    } else {
                        scale = 2.5f
                        offset = Offset.Zero
                        onZoomChanged(true)
                    }
                })
            }
            .pointerInput(Unit) {
                // Не трогаем одиночное касание, пока не увеличено: так работает свайп между страницами.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1.01f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            scale = newScale
                            offset = if (newScale <= 1.01f) Offset.Zero else clamp(offset + event.calculatePan(), newScale)
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
            Image(
                bitmap = bitmap,
                contentDescription = "Расписание",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
        } else {
            Box(Modifier.fillMaxSize().shimmer())
        }
    }
}
