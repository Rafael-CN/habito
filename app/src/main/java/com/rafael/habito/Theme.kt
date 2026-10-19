package com.rafael.habito

import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipPath
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
        glyph = if (t < 0.5f) a.glyph else b.glyph, gesture = if (t < 0.5f) a.gesture else b.gesture,
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
fun HabitTheme(palette: String = "verde", mode: Int = 0, paper: Boolean = false, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val reduce = LocalReduceMotion.current
    val t by animateFloatAsState(if (dark) 1f else 0f, if (reduce) snap() else tween(Motion.theme), label = "theme")
    val target = remember(palette, paper, t) { lerpPalette(paletteFor(palette, false, paper), paletteFor(palette, true, paper), t) }
    val shown = remember { mutableStateOf(target) }
    val from = remember { mutableStateOf(target) }
    val swap = remember { Animatable(1f) }
    val lastName = remember { mutableStateOf(palette) }
    // Tinta: com a folha de ajustes aberta, o quadro de antes fica guardado e a cor nova o atravessa num círculo.
    val layer = rememberGraphicsLayer()
    val recorded = remember { mutableStateOf(palette) }
    val ink = remember { Animatable(1f) }
    val inkFrom = remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    LaunchedEffect(palette) {
        if (lastName.value == palette) return@LaunchedEffect
        lastName.value = palette
        from.value = shown.value
        val origin = InkFx.origin
        if (!reduce && origin != null && InkFx.armed) {
            inkFrom.value = origin; InkFx.origin = null
            swap.snapTo(1f)
            ink.snapTo(0f); ink.animateTo(1f, tween(900, easing = androidx.compose.animation.core.CubicBezierEasing(0.5f, 0f, 0.15f, 1f)))
            inkFrom.value = null
        } else if (reduce) swap.snapTo(1f) else { swap.snapTo(0f); swap.animateTo(1f, tween(Motion.slow)) }
        recorded.value = palette
    }
    val p = if (swap.value >= 1f) target else lerpPalette(from.value, target, swap.value)
    SideEffect { shown.value = p }
    val scheme = if (dark) darkColorScheme(primary = p.ink, background = p.bg, surface = p.bg, onSurface = p.ink, outline = p.line)
    else lightColorScheme(primary = p.ink, background = p.bg, surface = p.bg, onSurface = p.ink, outline = p.line)
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.drawWithContent {
                    val changing = palette != recorded.value
                    val o = inkFrom.value
                    if (!changing && InkFx.armed) {
                        // guarda o quadro (só com os ajustes abertos, onde a cor pode mudar)
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    } else if (changing && InkFx.armed && !reduce && (o != null || InkFx.origin != null)) {
                        drawContent()
                        val c = o ?: InkFx.origin ?: center
                        val k = if (o == null) 0f else ink.value
                        val r = InkFx.hypot0(c, size.width, size.height) * k
                        val hole = androidx.compose.ui.graphics.Path().apply {
                            addRect(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size))
                            addOval(androidx.compose.ui.geometry.Rect(c, r))
                            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                        }
                        clipPath(hole) { drawLayer(layer) }
                    } else drawContent()
                },
            ) { content() }
        }
    }
}

/** Qualquer texto do Material sem estilo próprio também sai em Archivo. */
private val typography = androidx.compose.material3.Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = Sans), bodyMedium = bodyMedium.copy(fontFamily = Sans), bodySmall = bodySmall.copy(fontFamily = Sans),
        labelLarge = labelLarge.copy(fontFamily = Sans), labelMedium = labelMedium.copy(fontFamily = Sans), labelSmall = labelSmall.copy(fontFamily = Sans),
        titleMedium = titleMedium.copy(fontFamily = Sans),
    )
}
