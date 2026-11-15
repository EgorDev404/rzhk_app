package ru.rzk.schedule

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.rzk.schedule.data.ScheduleRepository
import ru.rzk.schedule.data.SettingsStore
import ru.rzk.schedule.data.SiteClient
import ru.rzk.schedule.ui.theme.NightMode
import ru.rzk.schedule.work.Notifier
import ru.rzk.schedule.work.WorkScheduler

/** Общие объекты приложения (простая «ручная» инъекция зависимостей — без лишних фреймворков). */
class ScheduleApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsStore
        private set

    val repository: ScheduleRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ScheduleRepository(this, SiteClient())
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        // Заставка следующего запуска должна быть в той же теме, что выбрана в приложении.
        NightMode.apply(this, settings.current.theme)

        // Всю тяжёлую фоновую инициализацию (канал уведомлений, WorkManager, Firebase, очистку кэша)
        // уводим с главного потока и даём интерфейсу спокойно отрисовать первые кадры без микрофризов.
        appScope.launch(Dispatchers.IO) {
            Notifier.createChannel(this@ScheduleApp)
            delay(600)
            WorkScheduler.apply(this@ScheduleApp, settings.current)
            repository.cleanup()
        }
    }
}

val Context.app: ScheduleApp get() = applicationContext as ScheduleApp
