package com.rafael.habito

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Garante os agendamentos (virada do dia e lembrete) sempre que o processo sobe. */
class HabitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val s = HabitStore.load(this@HabitApp)
            Rollover.schedule(this@HabitApp, s.rolloverHour)
            Reminder.schedule(this@HabitApp, s)
            AutoBackup.schedule(this@HabitApp, s)
        }
        CrashLog.install(this)
    }
}
