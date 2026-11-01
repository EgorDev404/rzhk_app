package ru.rzk.schedule.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.rzk.schedule.ui.theme.SeedPresets

/**
 * Выбор цвета приложения: семь готовых кружков и в конце пипетка, которая открывает полную палитру.
 * Остальные оттенки схемы (фон, карточки, кнопки) подбираются сами, см. [ru.rzk.schedule.ui.theme.SeedColors].
 */
@Composable
fun SeedColorChooser(selected: Int, onSelect: (Int) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val isPreset = SeedPresets.any { it.argb == selected }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Цвет приложения",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeedPresets.forEach { preset ->
                ColorDot(Color(preset.argb), selected == preset.argb, preset.name) { onSelect(preset.argb) }
            }
            PipetteDot(custom = if (isPreset) null else Color(selected)) { showPicker = true }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = selected,
            onDismiss = { showPicker = false },
            onConfirm = {
                onSelect(it)
                showPicker = false
            },
        )
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .semantics { contentDescription = description }
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Пипетка: если выбран свой цвет, кружок заливается им и обводится; иначе обычная кнопка. */
@Composable
private fun PipetteDot(custom: Color?, onClick: () -> Unit) {
    val base = custom ?: MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(base)
            .border(
                width = if (custom != null) 3.dp else 1.dp,
                color = if (custom != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .semantics { contentDescription = "Выбрать свой цвет" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Colorize,
            contentDescription = null,
            tint = if (custom != null) {
                if (custom.luminance() > 0.5f) Color.Black else Color.White
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Полная палитра: квадрат «насыщенность × яркость» и полоса оттенка. Можно выбрать абсолютно любой цвет. */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by remember { mutableStateOf(start[0]) }
    var saturation by remember { mutableStateOf(start[1]) }
    var brightness by remember { mutableStateOf(start[2]) }
    val current = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой цвет") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SaturationBrightnessBox(hue, saturation, brightness) { s, v ->
                    saturation = s
                    brightness = v
                }
                HueBar(hue) { hue = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(current))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Text(
                        String.format("#%06X", current and 0xFFFFFF),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text("Выбрать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SaturationBrightnessBox(hue: Float, saturation: Float, brightness: Float, onChange: (Float, Float) -> Unit) {
    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val update by rememberUpdatedState(onChange)
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun pick(p: Offset) = update(
                        (p.x / size.width).coerceIn(0f, 1f),
                        1f - (p.y / size.height).coerceIn(0f, 1f),
                    )
                    val down = awaitFirstDown()
                    pick(down.position)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            if (it.pressed) {
                                pick(it.position)
                                it.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val center = Offset(saturation * size.width, (1f - brightness) * size.height)
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 13.dp.toPx(), center = center, style = Stroke(5.dp.toPx()))
            drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        }
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val update by rememberUpdatedState(onChange)
    val rainbow = remember {
        listOf(0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000).map { Color(it) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun pick(p: Offset) = update((p.x / size.width).coerceIn(0f, 1f) * 359.9f)
                    val down = awaitFirstDown()
                    pick(down.position)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            if (it.pressed) {
                                pick(it.position)
                                it.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(rainbow))
            val center = Offset(hue / 359.9f * size.width, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 14.dp.toPx(), center = center, style = Stroke(5.dp.toPx()))
            drawCircle(Color.White, radius = 12.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        }
    }
}
