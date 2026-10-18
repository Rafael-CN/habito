package com.rafael.habito

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime

/** Redesenha o widget logo após a virada do dia, para nunca amanhecer mostrando ontem. */
object Rollover {
    fun schedule(ctx: Context, hour: Int) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, 0, 30)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = OneTimeWorkRequestBuilder<RolloverWorker>()
            .setInitialDelay(Duration.between(now, next))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("rollover", ExistingWorkPolicy.REPLACE, req)
    }
}

class RolloverWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        HabitWidget().updateAll(applicationContext)
        Rollover.schedule(applicationContext, HabitStore.load(applicationContext).rolloverHour)
        return Result.success()
    }
}
