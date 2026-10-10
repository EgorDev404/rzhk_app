package ru.rzk.schedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
    onOpenSettings: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenViewer: (ViewerRequest) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val stateHolder = rememberSaveableStateHolder()

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
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
        },
    ) { outer ->
        // Верхний отступ не нужен: у каждого экрана своя шапка, она сама учитывает строку состояния.
        Box(Modifier.fillMaxSize().padding(bottom = outer.calculateBottomPadding())) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(300)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(200)))
                },
                label = "tabs",
            ) { current ->
                stateHolder.SaveableStateProvider(current) {
                    if (current == 0) {
                        HomeScreen(
                            vm = vm,
                            notificationsOn = notificationsOn,
                            onOpenSettings = onOpenSettings,
                            onEnableNotifications = onEnableNotifications,
                            onOpenViewer = onOpenViewer,
                        )
                    } else {
                        BellsScreen(
                            fullDay = bellsFull,
                            onFullDayChange = onBellsFullChange,
                            onOpenSettings = onOpenSettings,
                        )
                    }
                }
            }
        }
    }
}