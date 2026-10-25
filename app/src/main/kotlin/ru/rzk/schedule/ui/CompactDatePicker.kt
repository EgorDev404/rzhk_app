package ru.rzk.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.rzk.schedule.data.Dates
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val WEEKDAYS = listOf("П", "В", "С", "Ч", "П", "С", "В")

/**
 * Компактный выбор даты для низкого экрана (телефон в горизонтали): слева выбранная дата и кнопки,
 * справа сетка месяца. Стандартный Material-календарь здесь не помещается и обрезается.
 */
@Composable
fun CompactDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    val today = remember { Dates.today() }
    val ru = remember { Locale("ru") }
    val scheme = MaterialTheme.colorScheme

    val offset = month.atDay(1).dayOfWeek.value - 1 // понедельник — первый день недели
    val cells: List<LocalDate?> = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(0.92f),
        ) {
            Row(
                Modifier.height(IntrinsicSize.Min).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ---- левая часть: что выбрано и кнопки ----
                Column(Modifier.width(208.dp).fillMaxHeight()) {
                    Text("Выберите дату", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        selected.format(DateTimeFormatter.ofPattern("d MMM yyyy", ru)),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.weight(1f))
                    Row {
                        TextButton(onClick = onDismiss) { Text("Отмена") }
                        TextButton(onClick = { onConfirm(selected) }) { Text("Показать") }
                    }
                }

                // ---- правая часть: месяц ----
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
                        }
                        Text(
                            month.month.getDisplayName(JavaTextStyle.FULL_STANDALONE, ru)
                                .replaceFirstChar { it.uppercase() } + " ${month.year}",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { month = month.plusMonths(1) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Следующий месяц")
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        WEEKDAYS.forEach { label ->
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Всегда 6 строк, чтобы окно не прыгало по высоте при смене месяца.
                    repeat(6) { row ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { col ->
                                val date = cells.getOrNull(row * 7 + col)
                                Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                                    if (date != null) {
                                        val isSelected = date == selected
                                        val ring = if (date == today && !isSelected) {
                                            Modifier.border(1.dp, scheme.primary, CircleShape)
                                        } else Modifier
                                        Box(
                                            Modifier.size(34.dp)
                                                .clip(CircleShape)
                                                .then(if (isSelected) Modifier.background(scheme.primary) else Modifier)
                                                .then(ring)
                                                .clickable { selected = date },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                date.dayOfMonth.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isSelected) scheme.onPrimary else scheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
