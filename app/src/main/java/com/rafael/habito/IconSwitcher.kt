package com.rafael.habito

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * O ícone do app acompanha a cor do mapa: cada cor tem um activity-alias com o próprio ícone, e só um fica ligado.
 * Com "Ícone na cor" desligado, volta o ícone original.
 */
object IconSwitcher {
    private const val ORIGINAL = "IconOriginal"
    private fun alias(name: String) = "Icon" + name.replaceFirstChar { it.uppercase() }

    fun apply(ctx: Context, s: HabitState) {
        val pm = ctx.packageManager
        val want = if (s.iconFollows && s.palette in Accents.NAMES) alias(s.palette) else ORIGINAL
        val all = listOf(ORIGINAL) + Accents.NAMES.map(::alias)
        fun cn(cls: String) = ComponentName(ctx.packageName, "${ctx.packageName}.$cls")
        fun enabled(cls: String) = when (pm.getComponentEnabledSetting(cn(cls))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> cls == ORIGINAL // estado do manifesto
        }
        // Liga o novo antes de desligar os outros: nunca fica sem ícone no lançador.
        if (!enabled(want)) pm.setComponentEnabledSetting(cn(want), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        all.filter { it != want && enabled(it) }.forEach {
            pm.setComponentEnabledSetting(cn(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
