package ru.rzk.schedule.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.rzk.schedule.app
import ru.rzk.schedule.ui.theme.ScheduleTheme
import ru.rzk.schedule.work.WorkScheduler

/** Корень интерфейса: тема, переключение «главный экран ↔ настройки» и полноэкранный просмотр поверх. */
@Composable
fun AppRoot(vm: HomeViewModel) {
    val context = LocalContext.current
    val store = context.app.settings
    val settings by store.flow.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var viewerVisible by remember { mutableStateOf(false) }

    // Расписание фоновых проверок следует за настройками.
    LaunchedEffect(settings.notifications, settings.intervalMinutes) {
        WorkScheduler.apply(context, settings)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) store.update { it.copy(notifications = true) }
    }
    val enableNotifications = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else store.update { it.copy(notifications = true) }
    }

    // Иконки статус-бара и навбара: белые, пока открыт вьюер (чёрный фон),
    // и обычные — в остальном приложении.
    val view = LocalView.current
    LaunchedEffect(viewerVisible) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !viewerVisible
        controller.isAppearanceLightNavigationBars = !viewerVisible
    }

    ScheduleTheme(settings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = showSettings,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(tween(320)) { -it / 6 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(320)) { -it / 6 } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(tween(320)) { it / 4 } + fadeOut(tween(200)))
                    }
                },
                label = "screens",
            ) { settingsOpen ->
                if (settingsOpen) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        settings = settings,
                        onChange = { transform -> store.update(transform) },
                        onEnableNotifications = enableNotifications,
                        onCheckNow = { WorkScheduler.checkNow(context) },
                        onBack = { showSettings = false },
                    )
                } else {
                    HomeScreen(
                        vm = vm,
                        notificationsOn = settings.notifications,
                        onOpenSettings = { showSettings = true },
                        onEnableNotifications = enableNotifications,
                        onOpenViewer = { request ->
                            viewer = request
                            viewerVisible = true
                        },
                    )
                }
            }

            // BackHandler вынесен наружу: срабатывает только когда вьюер видим.
            BackHandler(enabled = viewerVisible) { viewerVisible = false }

            viewer?.let { request ->
                AnimatedVisibility(
                    visible = viewerVisible,
                    enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.92f),
                    exit = fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 0.92f),
                ) {
                    ViewerScreen(request = request, onClose = { viewerVisible = false })
                }
            }
        }
    }

    // Сброс request после завершения анимации закрытия — освобождаем File-ссылки.
    LaunchedEffect(viewerVisible, viewer) {
        if (!viewerVisible && viewer != null) {
            delay(220) // чуть больше длительности exit-анимации
            viewer = null
        }
    }
}