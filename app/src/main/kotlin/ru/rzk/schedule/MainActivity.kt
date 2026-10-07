package ru.rzk.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.activity.viewModels
import ru.rzk.schedule.ui.AppRoot
import ru.rzk.schedule.ui.HomeViewModel
import ru.rzk.schedule.work.Notifier
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(application as ScheduleApp) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { AppRoot(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Нажали на уведомление — открываем нужный день. */
    private fun handle(intent: Intent?) {
        val day = intent?.getStringExtra(Notifier.EXTRA_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (day != null) viewModel.showDay(day)
    }
}
