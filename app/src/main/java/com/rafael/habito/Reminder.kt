package com.rafael.habito

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/**
 * Lembretes em qualquer horário (até três). Cada um pode avisar só com o dia pendente. Não avisa com hoje feito,
 * em dia de descanso nem com a meta semanal já cumprida. Ação "Feito" marca sem abrir o app.
 */
object Reminder {
    private const val WORK = "reminder"
    private const val CHANNEL = "lembrete"
    private const val NOTIFICATION_ID = 1
    const val KEY_MIN = "min"

    /** Agenda só o próximo lembrete (hoje ou amanhã); o trabalho, ao rodar, agenda o seguinte. */
    fun schedule(ctx: Context, s: HabitState) {
        val wm = WorkManager.getInstance(ctx)
        val now = LocalDateTime.now()
        val next = next(s.reminders, now)
        if (next == null) { wm.cancelUniqueWork(WORK); return }
        val (at, rem) = next
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, at))
            .setInputData(workDataOf(KEY_MIN to rem.minutes))
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }

    /** O próximo horário estritamente depois de [now], hoje ou amanhã. */
    fun next(list: List<Rem>, now: LocalDateTime): Pair<LocalDateTime, Rem>? {
        if (list.isEmpty()) return null
        val at = { r: Rem, plus: Long -> now.toLocalDate().plusDays(plus).atTime(r.minutes / 60, r.minutes % 60) }
        return list.map { at(it, 0) to it }.filter { it.first.isAfter(now) }.minByOrNull { it.first }
            ?: list.minBy { it.minutes }.let { at(it, 1) to it }
    }

    /** Se este lembrete deve aparecer agora. */
    fun shouldNotify(s: HabitState, rem: Rem): Boolean {
        val mark = s.markOf(s.today)
        return when {
            mark == Mark.DONE -> false
            rem.onlyPending && mark != null -> false
            !s.weekly && s.today.dow in s.rest -> false
            s.weekly && s.doneThisWeek >= s.goal -> false
            else -> true
        }
    }

    fun notify(ctx: Context, s: HabitState, morning: Boolean = false) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lembrete do dia", NotificationManager.IMPORTANCE_DEFAULT))

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val markDone = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, MarkDoneReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val streak = s.streak()
        val unit = "$streak " + if (s.weekly) (if (streak == 1) "semana" else "semanas") else (if (streak == 1) "dia" else "dias")
        // De manhã, um convite; mais tarde, o aviso para não perder a sequência.
        val text = when {
            morning && streak > 0 && !s.weekly -> "Bom dia. Hoje é o dia ${streak + 1} da sequência."
            morning -> "Bom dia. Hoje conta."
            streak > 0 -> "Ainda dá tempo de manter a sequência de $unit."
            else -> "Ainda dá tempo de marcar hoje."
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_grid)
            .setColor(0xFF12813F.toInt())
            .setContentTitle(s.taskName)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_check, "Feito", markDone)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
    }

    fun dismiss(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val s = HabitStore.load(applicationContext)
        val min = inputData.getInt(Reminder.KEY_MIN, -1)
        val rem = s.reminders.firstOrNull { it.minutes == min }
        if (rem != null && Reminder.shouldNotify(s, rem)) Reminder.notify(applicationContext, s, morning = rem.minutes < 12 * 60)
        Reminder.schedule(applicationContext, s)
        return Result.success()
    }
}

class MarkDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = HabitStore.load(context)
                HabitStore.setMark(context, s.today, Mark.DONE)
                HabitWidget().updateAll(context)
                Reminder.dismiss(context)
            } finally {
                pending.finish()
            }
        }
    }
}
