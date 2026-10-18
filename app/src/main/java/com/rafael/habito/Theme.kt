package com.rafael.habito

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.lerp

/** Mistura duas paletas: 0 = [a], 1 = [b]. */
fun lerpPalette(a: Palette, b: Palette, t: Float): Palette = when {
    t <= 0f -> a
    t >= 1f -> b
    else -> Palette(
        bg = lerp(a.bg, b.bg, t), surface = lerp(a.surface, b.surface, t), track = lerp(a.track, b.track, t),
        thumb = lerp(a.thumb, b.thumb, t), thumbEdge = lerp(a.thumbEdge, b.thumbEdge, t), rule = lerp(a.rule, b.rule, t),
        line = lerp(a.line, b.line, t), ink = lerp(a.ink, b.ink, t), text2 = lerp(a.text2, b.text2, t), text3 = lerp(a.text3, b.text3, t),
        done = lerp(a.done, b.done, t), onDone = lerp(a.onDone, b.onDone, t), miss = lerp(a.miss, b.miss, t),
        gold = lerp(a.gold, b.gold, t), onInk2 = lerp(a.onInk2, b.onInk2, t),
        levels = a.levels.zip(b.levels) { x, y -> lerp(x, y, t) },
        levelInk = a.levelInk.zip(b.levelInk) { x, y -> lerp(x, y, t) },
    )
}

/** O tema efetivo: [mode] 0 segue o sistema, 1 força claro, 2 força escuro. */
@Composable
fun isDark(mode: Int): Boolean = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }

/**
 * Paleta do tema (sistema ou escolhido) com a cor de mapa escolhida, exposta em [Tokens] e mapeada no Material só
 * para o cursor e a seleção de texto. Trocar claro/escuro ou a cor do mapa faz crossfade em vez de piscar.
 */
@Composable
fun HabitTheme(palette: String = "verde", mode: Int = 0, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val reduce = LocalReduceMotion.current
    val t by animateFloatAsState(if (dark) 1f else 0f, if (reduce) snap() else tween(Motion.theme), label = "theme")
    val target = remember(palette, t) { lerpPalette(paletteFor(palette, false), paletteFor(palette, true), t) }
    val shown = remember { mutableStateOf(target) }
    val from = remember { mutableStateOf(target) }
    val swap = remember { Animatable(1f) }
    val lastName = remember { mutableStateOf(palette) }
    LaunchedEffect(palette) {
        if (lastName.value == palette) return@LaunchedEffect
        lastName.value = palette
        from.value = shown.value
        if (reduce) swap.snapTo(1f) else { swap.snapTo(0f); swap.animateTo(1f, tween(Motion.slow)) }
    }
    val p = if (swap.value >= 1f) target else lerpPalette(from.value, target, swap.value)
    SideEffect { shown.value = p }
    val scheme = if (dark) darkColorScheme(primary = p.ink, background = p.bg, surface = p.bg, onSurface = p.ink, outline = p.line)
    else lightColorScheme(primary = p.ink, background = p.bg, surface = p.bg, onSurface = p.ink, outline = p.line)
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme = scheme, typography = typography, content = content) }
}

/** Qualquer texto do Material sem estilo próprio também sai em Archivo. */
private val typography = androidx.compose.material3.Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = Sans), bodyMedium = bodyMedium.copy(fontFamily = Sans), bodySmall = bodySmall.copy(fontFamily = Sans),
        labelLarge = labelLarge.copy(fontFamily = Sans), labelMedium = labelMedium.copy(fontFamily = Sans), labelSmall = labelSmall.copy(fontFamily = Sans),
        titleMedium = titleMedium.copy(fontFamily = Sans),
    )
}
