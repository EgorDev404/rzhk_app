package ru.rzk.schedule.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.rzk.schedule.data.LoadStage
import java.io.File

internal const val PAGE_RATIO = 1.414f // A4 в альбомной ориентации — пока не знаем реальный размер

// ---- мерцание (skeleton) ------------------------------------------------------------------------

@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = lerp(base, MaterialTheme.colorScheme.onSurface, 0.10f)
    val progress = rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerX",
    )
    return drawBehind {
        drawRect(base)
        val x = progress.value * size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x, 0f),
                end = Offset(x + size.width * 0.55f, size.height * 0.3f),
            ),
        )
    }
}

// ---- загрузка ------------------------------------------------------------------------------------

private fun stageText(stage: LoadStage) = when (stage) {
    LoadStage.Connecting -> "Подключаюсь к сайту колледжа…"
    LoadStage.Searching -> "Ищу файл с расписанием…"
    LoadStage.Rendering -> "Готовлю картинку…"
}

@Composable
fun LoadingPlaceholder(stage: LoadStage) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PulsingDot()
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 2 }) togetherWith fadeOut(tween(140))
                },
                label = "stage",
            ) { current ->
                Text(stageText(current), style = MaterialTheme.typography.titleMedium)
            }
        }
        LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(PAGE_RATIO).shimmer())
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { index ->
                Box(
                    Modifier.fillMaxWidth(if (index == 0) 0.7f else 0.45f).height(12.dp)
                        .clip(RoundedCornerShape(50)).shimmer(),
                )
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val scale = rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.7f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dotScale",
    )
    Box(
        Modifier.size(14.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

// ---- страница расписания ------------------------------------------------------------------------

private class LoadedImage(val bitmap: ImageBitmap, val ratio: Float)

private fun decode(file: File, sample: Int): LoadedImage? {
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
    return LoadedImage(bitmap.asImageBitmap(), bitmap.width.toFloat() / bitmap.height)
}

/** Уже декодированные страницы держим в памяти: при возврате на вкладку заглушка не мелькает. */
private object ImageCache {
    private val cache = object : LruCache<String, LoadedImage>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: LoadedImage): Int = value.bitmap.width * value.bitmap.height * 4
    }

    operator fun get(path: String): LoadedImage? = cache.get(path)
    operator fun set(path: String, image: LoadedImage) {
        cache.put(path, image)
    }
}

/** Карточка с картинкой расписания: пока декодируется — мерцает, потом плавно проявляется. */
@Composable
fun SchedulePageCard(file: File, label: String, onClick: () -> Unit) {
    val loaded by produceState<LoadedImage?>(ImageCache[file.path], file) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { decode(file, sample = 2) }?.also { ImageCache[file.path] = it }
        }
    }
    val imageAlpha by animateFloatAsState(if (loaded != null) 1f else 0f, tween(450), label = "imageAlpha")

    // Нажимная подсветка отключена: карточка занимает почти весь экран, и при начале свайпа по ней
    // на долю секунды вспыхивал серый слой. Нажатие по-прежнему открывает просмотр на весь экран.
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(loaded?.ratio ?: PAGE_RATIO).animateContentSize()) {
            Box(Modifier.fillMaxSize().shimmer().graphicsLayer { this.alpha = 1f - imageAlpha })
            loaded?.let { image ->
                Image(
                    bitmap = image.bitmap,
                    contentDescription = label,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = imageAlpha },
                )
            }
        }
    }
}

// ---- пустые состояния и баннеры -----------------------------------------------------------------

@Composable
fun StatusState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(104.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        actions()
    }
}

@Composable
fun InfoBanner(
    icon: ImageVector,
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = container, contentColor = content, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
