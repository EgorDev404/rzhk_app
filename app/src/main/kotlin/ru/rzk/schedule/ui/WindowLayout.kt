package ru.rzk.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Окно «низкое»: меньше 480dp по высоте. Это телефон в горизонтальной ориентации
 * (или очень маленькое окно в режиме разделённого экрана). Здесь экономим вертикальное место.
 */
@Composable
fun isCompactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < 480

/** Вместо нижней панели навигации показываем боковую: окно широкое (≥600dp) или низкое. */
@Composable
fun useNavigationRail(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp >= 600 || config.screenHeightDp < 480
}

/** Две колонки рядом: широкое окно, которое при этом шире, чем выше (альбомная ориентация). */
@Composable
fun useTwoPane(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp >= 600 && config.screenWidthDp > config.screenHeightDp
}

/**
 * Колонка, ограниченная по ширине и выровненная по центру. На телефоне в портрете ведёт себя
 * как обычная колонка на всю ширину; на широких экранах не даёт контенту растягиваться.
 */
@Composable
fun CenteredColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 840.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth(),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
