package ru.rzk.schedule.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import ru.rzk.schedule.ui.theme.LocalWinter
import ru.rzk.schedule.ui.theme.WinterState
import ru.rzk.schedule.ui.theme.Winter
import ru.rzk.schedule.ui.theme.SnowFall
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import ru.rzk.schedule.app
import ru.rzk.schedule.ui.theme.NightMode
import ru.rzk.schedule.ui.theme.ScheduleTheme
import ru.rzk.schedule.work.WorkScheduler

/** Корень интерфейса: тема, переключение «главный экран ↔ настройки» и полноэкранный просмотр поверх. */
@Composable
fun AppRoot(vm: HomeViewModel, pendingTab: MutableStateFlow<Int?>) {
    val context = LocalContext.current
    val store = context.app.settings
    val settings by store.flow.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var viewerVisible by remember { mutableStateOf(false) }

    // Расписание фоновых проверок следует за настройками.
    LaunchedEffect(settings.notifications, settings.devMode) {
        WorkScheduler.apply(context, settings)
    }

    // Приветственная плашка: показываем один раз. Считаем её показанной сразу, как только она появилась,
    // поэтому после перезапуска приложения она не вернётся, даже если её закрыли свайпом или не нажали «Понятно».
    var showIntro by remember { mutableStateOf(!store.introShown()) }
    LaunchedEffect(Unit) {
        if (showIntro) store.markIntroShown()
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

    // Выбор темы в приложении запоминается и в системе: так заставка следующего запуска будет в той же теме.
    LaunchedEffect(settings.theme) { NightMode.apply(context, settings.theme) }

    val pinch = rememberPinchZoomState()

    // Когда белое фото расписания, оторванное щипком, заезжает под строку состояния или нижнюю панель,
    // значки в них должны стать тёмными, иначе их не видно.
    val density = LocalDensity.current
    val rootView = LocalView.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()
    val overTop by remember(topInset) { derivedStateOf { pinch.coversTop(topInset) } }
    val overBottom by remember(bottomInset) {
        derivedStateOf { pinch.coversBottom(bottomInset, rootView.height.toFloat()) }
    }

    // Зимнее оформление: снегопад и сугробы (1–20 декабря или принудительно из режима разработчика).
    val winterActive = Winter.isActive(settings)
    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }
    val winter = remember(winterActive) { if (winterActive) WinterState(reduceMotion) else null }
    LaunchedEffect(winter) {
        // Сугробы растут примерно полминуты после включения.
        if (winter != null && !winter.reduceMotion) {
            animate(0f, 1f, animationSpec = tween(30_000, easing = LinearOutSlowInEasing)) { value, _ ->
                winter.depth = value
            }
        }
    }

    ScheduleTheme(settings, darkBars = viewerVisible, statusBarOverLight = overTop, navBarOverLight = overBottom) {
        CompositionLocalProvider(LocalPinchZoom provides pinch, LocalWinter provides winter) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val spec = tween(220, easing = FastOutSlowInEasing)
            AnimatedContent(
                targetState = showSettings,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(spec) { it / 3 } + fadeIn(spec)) togetherWith
                            (slideOutHorizontally(spec) { -it / 4 } + fadeOut(spec))
                    } else {
                        (slideInHorizontally(spec) { -it / 4 } + fadeIn(spec)) togetherWith
                            (slideOutHorizontally(spec) { it / 3 } + fadeOut(spec))
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
                        onBack = { showSettings = false },
                    )
                } else {
                    MainShell(
                        vm = vm,
                        pendingTab = pendingTab,
                        notificationsOn = settings.notifications,
                        bellsFull = settings.bellsFull,
                        onBellsFullChange = { full -> store.update { it.copy(bellsFull = full) } },
                        onOpenSettings = { showSettings = true },
                        onEnableNotifications = enableNotifications,
                        onOpenViewer = { request ->
                            viewer = request
                            viewerVisible = true
                        },
                    )
                }
            }

            // Снегопад поверх экранов, но под просмотром расписания на весь экран.
            if (winter != null && !viewerVisible) SnowFall(winter)

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

            // Фото, «отслоённое» щипком двух пальцев, летает поверх всего интерфейса.
            PinchZoomOverlay(pinch)
        }
        }
    }

    if (showIntro) {
        ScheduleTheme(settings, darkBars = false) {
            IntroSheet(onDismiss = { showIntro = false })
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