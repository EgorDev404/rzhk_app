package ru.rzk.schedule

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.rzk.schedule.ui.AppRoot
import ru.rzk.schedule.ui.HomeViewModel
import ru.rzk.schedule.work.Notifier
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(application as ScheduleApp) as T
        }
    }

    /** Длительность анимации иконки — синхронизирована с ic_splash.xml и themes.xml. */
    private val splashAnimationDurationMs = 650L

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Ставим splash ДО super.onCreate — иначе система успеет показать пустое окно.
        val splash = installSplashScreen()

        // 2. Условие удержания: пока false — splash висит, анимация доигрывает.
        var splashFinished = false
        splash.setKeepOnScreenCondition { !splashFinished }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 3. Таймер стартует сразу после super.onCreate.
        //    Он НЕ блокирует UI-поток, поэтому Compose успевает отрисовать первый кадр
        //    под сплешем, и переход происходит мгновенно и без мигания.
        Handler(Looper.getMainLooper()).postDelayed(
            { splashFinished = true },
            splashAnimationDurationMs
        )

        // 4. Обработка deep link из уведомления (только при холодном старте).
        if (savedInstanceState == null) handle(intent)

        // 5. Контент ставится сразу — он рендерится под сплешем.
        //    К моменту снятия splash пользователь видит готовый UI.
        setContent { AppRoot(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Нажали на уведомление — открываем нужный день. */
    private fun handle(intent: Intent?) {
        val day = intent?.getStringExtra(Notifier.EXTRA_DAY)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (day != null) viewModel.showDay(day)
    }
}