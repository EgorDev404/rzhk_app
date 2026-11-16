package ru.rzk.schedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import ru.rzk.schedule.ui.theme.NewYearTree
import ru.rzk.schedule.ui.theme.LocalNewYear
import androidx.compose.ui.unit.dp
import ru.rzk.schedule.ui.theme.snowCap
import androidx.compose.ui.zIndex
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

private enum class Tab(val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Lessons("Расписание", Icons.Outlined.Today, Icons.Rounded.Today),
    Bells("Звонки", Icons.Outlined.Notifications, Icons.Rounded.Notifications),
}

/**
 * Основная оболочка: нижняя навигация и два экрана — расписание уроков (главный) и звонки.
 *
 * Переключение вкладки может прийти извне (тап по виджету): [pendingTab] из MainActivity.
 * Переход к конкретному дню в расписании обрабатывается внутри [HomeScreen] через
 * [HomeViewModel.pendingDay], поэтому задержек не требуется.
 */
@Composable
fun MainShell(
    vm: HomeViewModel,
    pendingTab: MutableStateFlow<Int?>,
    notificationsOn: Boolean,
    bellsFull: Boolean,
    onBellsFullChange: (Boolean) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenViewer: (ViewerRequest) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }

    // Внешний запрос на конкретную вкладку (тап по виджету).
    val pendingTabValue by pendingTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTabValue) {
        pendingTabValue?.let { target ->
            if (target in Tab.entries.indices) tab = target
            pendingTab.value = null
        }
    }

    // С «Звонков» кнопка «назад» возвращает на главный экран, а не закрывает приложение.
    BackHandler(enabled = tab != 0) { tab = 0 }

    // Широкий или низкий экран (планшет, телефон в горизонтали): боковая панель вместо нижней.
    val useRail = useNavigationRail()
    val barInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!useRail) {
                // Зимой на верхней кромке нижней панели лежит снег.
                NavigationBar(modifier = Modifier.snowCap(seed = 77, cornerRadius = 0.dp, maxHeight = 9.dp)) {
                    Tab.entries.forEachIndexed { index, item ->
                        val selected = tab == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = { tab = index },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(item.title) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { outer ->
        // Верхний отступ не нужен: у каждого экрана своя шапка, она сама учитывает строку состояния.
        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    windowInsets = barInsets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
                ) {
                    Spacer(Modifier.weight(1f))
                    Tab.entries.forEachIndexed { index, item ->
                        val selected = tab == index
                        NavigationRailItem(
                            selected = selected,
                            onClick = { tab = index },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(item.title) },
                            alwaysShowLabel = true,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(bottom = outer.calculateBottomPadding())
                    .then(
                        if (useRail) {
                            // Слева отступ уже дала панель, справа — вырез камеры и системные кнопки.
                            Modifier
                                .consumeWindowInsets(barInsets.only(WindowInsetsSides.Start))
                                .windowInsetsPadding(barInsets.only(WindowInsetsSides.End))
                        } else Modifier,
                    ),
            ) {
                // Обе вкладки собраны заранее и лежат друг на друге: переключение на пружинной физике iOS
                // мгновенно откликается и не пересобирает экраны (120/144 Гц без рывков).
                val tabProgress = animateFloatAsState(
                    targetValue = if (tab == 0) 0f else 1f,
                    animationSpec = Motion.iosSnappySpring(),
                    label = "tab",
                )
                // «Звонки» собираем сразу на следующем кадре после показа главного экрана, чтобы при тапе по вкладке
                // она открылась мгновенно без ожидания.
                var bellsReady by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    withFrameNanos { }
                    bellsReady = true
                }

                TabLayer(fraction = { 1f - tabProgress.value }, active = tab == 0) {
                    HomeScreen(
                        vm = vm,
                        notificationsOn = notificationsOn,
                        onOpenBookmarks = onOpenBookmarks,
                        onOpenSettings = onOpenSettings,
                        onEnableNotifications = onEnableNotifications,
                        onOpenViewer = onOpenViewer,
                    )
                }
                if (bellsReady || tab == 1) {
                    TabLayer(fraction = { tabProgress.value }, active = tab == 1) {
                        BellsScreen(
                            fullDay = bellsFull,
                            onFullDayChange = onBellsFullChange,
                            onOpenSettings = onOpenSettings,
                        )
                    }
                }

                // Новогодняя ёлка в правом нижнем углу над панелью вкладок: её можно трогать (ветки, шары, звезда).
                LocalNewYear.current?.let { newYear ->
                    NewYearTree(
                        newYear,
                        Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp).zIndex(2f),
                    )
                }
            }
        }
    }
}

/**
 * Слой вкладки. Плавное наложение с лёгким масштабированием (как в iOS): обе вкладки остаются видимыми в момент
 * перехода, поэтому нет пустого провала яркости посередине анимации.
 */
@Composable
private fun TabLayer(fraction: () -> Float, active: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(if (active) 1f else 0f)
            .graphicsLayer {
                val f = fraction().coerceIn(0f, 1f)
                alpha = f
                val scale = 0.97f + 0.03f * f
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (active) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                        .clearAndSetSemantics { }
                },
            ),
    ) { content() }
}
