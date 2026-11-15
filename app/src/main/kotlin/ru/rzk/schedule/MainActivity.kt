package ru.rzk.schedule

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import ru.rzk.schedule.ui.AppRoot
import ru.rzk.schedule.ui.HomeViewModel
import ru.rzk.schedule.work.Notifier
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Вкладка, которую нужно открыть при следующем показе MainShell (тап по виджету). */
    val pendingTab = MutableStateFlow<Int?>(null)

    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(application as ScheduleApp) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Ставим splash ДО super.onCreate — иначе система успеет показать пустое окно.
        val splash = installSplashScreen()

        // 2. Плавное растворение сплеша с лёгким масштабированием в стиле iOS, без искусственной задержки в 650 мс.
        splash.setOnExitAnimationListener { provider ->
            val splashView = provider.view
            val fade = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f)
            val scaleX = ObjectAnimator.ofFloat(splashView, View.SCALE_X, 1f, 1.06f)
            val scaleY = ObjectAnimator.ofFloat(splashView, View.SCALE_Y, 1f, 1.06f)
            val interpolator = DecelerateInterpolator(1.8f)
            listOf(fade, scaleX, scaleY).forEach {
                it.duration = 220L
                it.interpolator = interpolator
                it.start()
            }
            fade.doOnEnd { provider.remove() }
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 3. Подбираем цвет иконок статус-бара под текущую тему (светлая/тёмная).
        applyStatusBarAppearance()

        // 4. Обработка deep link из уведомления (только при холодном старте).
        if (savedInstanceState == null) handle(intent)

        // 5. Контент ставится сразу — как только Compose соберёт первый кадр, сплеш мгновенно и мягко уйдёт.
        setContent { AppRoot(viewModel, pendingTab) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /**
     * Делает иконки статус-бара контрастными:
     *  - светлая тема  → тёмные иконки (isAppearanceLightStatusBars = true)
     *  - тёмная тема   → светлые иконки (isAppearanceLightStatusBars = false)
     * Прозрачный фон статус-бара задан в themes.xml.
     */
    private fun applyStatusBarAppearance() {
        val isDark = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !isDark
    }

    /** Нажали на уведомление — открываем нужный день. Тапнули по виджету — открываем вкладку «Звонки». */
    private fun handle(intent: Intent?) {
        val day = intent?.getStringExtra(Notifier.EXTRA_DAY)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (day != null) viewModel.showDay(day)

        val tab = intent?.getIntExtra(EXTRA_OPEN_TAB, -1) ?: -1
        if (tab >= 0) pendingTab.value = tab
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_SCHEDULE = 0
        const val TAB_BELLS = 1
    }
}