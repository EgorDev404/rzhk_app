package ru.rzk.schedule.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import ru.rzk.schedule.data.Settings
import ru.rzk.schedule.data.ThemeMode

/** Проверяет, внесено ли приложение в whitelist оптимизации батареи. */
private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onEnableNotifications: () -> Unit,
    onCheckNow: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }

    // Состояние «работа без ограничений батареи разрешена».
    // Перечитывается при каждом возвращении экрана в активное состояние.
    var batteryUnrestricted by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    LifecycleResumeEffect(Unit) {
        batteryUnrestricted = isBatteryOptimizationIgnored(context)
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- уведомления ----
            SettingsCard(title = "Уведомления") {
                ListItem(
                    headlineContent = { Text("Сообщать о расписании") },
                    supportingContent = { Text("Когда оно выложено на сайте или изменилось") },
                    trailingContent = {
                        Switch(
                            checked = settings.notifications,
                            onCheckedChange = { on ->
                                if (on) onEnableNotifications() else onChange { it.copy(notifications = false) }
                            },
                        )
                    },
                    colors = transparentItem(),
                )
                Text(
                    "Как часто проверять сайт",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                val options = listOf(15, 30, 60)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    options.forEachIndexed { index, minutes ->
                        SegmentedButton(
                            selected = settings.intervalMinutes == minutes,
                            onClick = { onChange { it.copy(intervalMinutes = minutes) } },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            enabled = settings.notifications,
                        ) { Text(if (minutes == 60) "1 час" else "$minutes мин") }
                    }
                }
                Text(
                    "Android сам решает, когда запускать проверку, поэтому уведомление может прийти с небольшой задержкой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                FilledTonalButton(
                    onClick = {
                        onCheckNow()
                        scope.launch { snackbar.showSnackbar("Проверяю сайт…") }
                    },
                    enabled = settings.notifications,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) { Text("Проверить сейчас") }
            }

            // ---- если уведомления не приходят ----
            SettingsCard(title = "Если уведомления не приходят") {
                Text(
                    if (batteryUnrestricted) {
                        "Готово: системе разрешено работать в фоне без ограничений батареи. " +
                            "Уведомления будут приходить вовремя."
                    } else {
                        "На некоторых телефонах (Xiaomi, Huawei, Samsung и др.) система выключает фоновые задачи. " +
                            "Разрешите приложению работать без ограничений батареи."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                if (batteryUnrestricted) {
                    // Залитая «успешная» кнопка, недоступная для нажатия.
                    FilledTonalButton(
                        onClick = { },
                        enabled = false,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Ограничения сняты")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) { Text("Разрешить работу в фоне") }
                }
            }

            // ---- оформление ----
            SettingsCard(title = "Оформление") {
                Text(
                    "Тема",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                val themes = listOf(ThemeMode.System to "Система", ThemeMode.Light to "Светлая", ThemeMode.Dark to "Тёмная")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    themes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { onChange { it.copy(theme = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, themes.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ListItem(
                        headlineContent = { Text("Цвета из обоев") },
                        supportingContent = { Text("Material You: палитра подстраивается под ваши обои") },
                        trailingContent = {
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = { on -> onChange { it.copy(dynamicColor = on) } },
                            )
                        },
                        colors = transparentItem(),
                    )
                }
            }

            // ---- о приложении ----
            SettingsCard(title = "О приложении") {
                Text(
                    "Данное приложение скачивает свежие официальные PDF файлы расписания с сервера колледжа ОГБПОУ \"РЖК\" (https://rzn-jd62.gosuslugi.ru)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "ℹ️ При ошибках загрузки, в первую очередь проверьте ваше подключение к сети, состояние VPN, частные DNS-сервера.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    "Версия $version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun transparentItem() = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            content()
        }
    }
}