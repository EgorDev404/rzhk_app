package ru.rzk.schedule

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.rzk.schedule.data.ScheduleRepository
import ru.rzk.schedule.data.SettingsStore
import ru.rzk.schedule.data.SiteClient
import ru.rzk.schedule.ui.theme.NightMode
import ru.rzk.schedule.work.Notifier
import ru.rzk.schedule.work.WorkScheduler

/** Общие объекты приложения (простая «ручная» инъекция зависимостей — без лишних фреймворков). */
class ScheduleApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: ScheduleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        // Заставка следующего запуска должна быть в той же теме, что выбрана в приложении.
        NightMode.apply(this, settings.current.themeMode)
        repository = ScheduleRepository(this, SiteClient())
        Notifier.createChannel(this)
        WorkScheduler.apply(this, settings.current)
        CoroutineScope(Dispatchers.IO).launch { repository.cleanup() }
    }
}

val Context.app: ScheduleApp get() = applicationContext as ScheduleApp
