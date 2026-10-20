package com.rafael.habito

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/** Sequências que merecem um momento: uma semana, um mês, cem dias, um ano. */
val MILESTONES = setOf(7, 30, 100, 365)

/** Na meta semanal, os marcos são em semanas: um mês, um trimestre, um semestre, um ano. */
val WEEK_MILESTONES = setOf(4, 12, 26, 52)

fun isMilestone(streak: Int, weekly: Boolean = false) = streak in (if (weekly) WEEK_MILESTONES else MILESTONES)

/**
 * Vibrações fora do app (widget, notificação), onde não há View para pedir haptics.
 * O Android descarta vibração de toque vinda de app em segundo plano; o uso "notificação"
 * é o permitido ali, e respeita a intensidade de vibração de notificações que o usuário escolheu.
 */
object Haptics {
    private fun vibrate(ctx: Context, effect: VibrationEffect) {
        val v = ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
        }
    }

    /** Marcar = clique firme; desmarcar ou "não fiz" = toque leve. */
    fun mark(ctx: Context, done: Boolean) {
        vibrate(ctx, VibrationEffect.createPredefined(if (done) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK))
    }

    /** Três pulsos crescentes para os marcos. */
    fun milestone(ctx: Context) {
        val v = ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        val timings = longArrayOf(0, 36, 70, 36, 70, 70)
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, intArrayOf(0, 140, 0, 190, 0, 255), -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrate(ctx, effect)
    }
}
