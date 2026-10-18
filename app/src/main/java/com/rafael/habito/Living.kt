package com.rafael.habito

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * A tela viva: o número da sequência se enche com o material da cor até o próximo marco, e atrás dele um campo
 * quase imperceptível respira no mesmo material. Tudo para com "reduzir movimento".
 */

private fun rnd(i: Int): Float = abs((sin(i * 12.9898 + 7.7) * 43758.5453) % 1.0).toFloat()

/** Milissegundos desde que a tela apareceu, avançando a cada quadro (parado em "reduzir movimento"). */
@Composable
fun rememberFrameTime(): State<Long> {
    val t = remember { mutableLongStateOf(0L) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t.longValue = it - start }
    }
    return t
}

/** Pétalas que caem e assentam: a hora em que cada uma nasceu. */
class PetalState { val born = ArrayList<Long>() }

/**
 * O número vira recipiente: o texto (desenhado por [content]) fica num tom de vidro e o material da cor
 * o enche de baixo até [level] (0..1). O texto serve de máscara: nada vaza para fora dos algarismos.
 */
fun Modifier.materialFill(pal: Palette, level: () -> Float, time: () -> Long, petals: PetalState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        val top = h * 0.1f; val bot = h * 0.94f
        val surf = bot - (bot - top) * level().coerceIn(0f, 1f)
        val t = time()
        drawMaterial(pal, t, surf, top, bot, petals, BlendMode.SrcAtop)
    }

/** O tom de vidro do número vazio: um véu de tinta sobre o fundo, opaco (vira máscara do material). */
fun vessel(pal: Palette): Color = lerp(pal.bg, pal.ink, 0.13f)

private fun DrawScope.drawMaterial(pal: Palette, t: Long, surf: Float, top: Float, bot: Float, petals: PetalState, blend: BlendMode) {
    val w = size.width; val h = bot - top
    val c = pal.levels.drop(1) // tons 1..4
    val tf = t.toFloat()
    when (pal.glyph) {
        Glyph.ESTRELA_MAR, Glyph.CRISTAL -> {
            val wave = Path().apply {
                moveTo(0f, bot + 20f)
                var x = 0f
                while (x <= w) { lineTo(x, surf + sin(x / 28f + tf / 500f) * 6 * density + sin(x / 13f - tf / 300f) * 2.5f * density); x += 6f }
                lineTo(w, bot + 20f); close()
            }
            drawPath(wave, Brush.verticalGradient(listOf(c[3], c[1]), startY = surf, endY = bot), blendMode = blend)
            if (pal.glyph == Glyph.CRISTAL) for (k in 0 until 28) {
                val x = rnd(k) * w; val y = surf + rnd(k + 9) * (bot - surf); val s = 4 * density
                drawLine(Color.White.copy(alpha = 0.4f), Offset(x - s, y), Offset(x + s, y), 1 * density, blendMode = blend)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(x, y - s), Offset(x, y + s), 1 * density, blendMode = blend)
            }
        }
        Glyph.FLOR -> {
            val n = 420
            val want = ((bot - surf) / h * n).toInt().coerceIn(0, n)
            while (petals.born.size < want) petals.born.add(t + petals.born.size % 6 * 40L)
            while (petals.born.size > want) petals.born.removeAt(petals.born.size - 1)
            petals.born.forEachIndexed { k, born ->
                val rest = bot - (k.toFloat() / n) * h * 0.98f - rnd(k + 7) * 10 * density
                val age = (t - born).coerceAtLeast(0) / 1000f
                val fall = top - 60 * density + 0.5f * 2200f * age * age
                val y = minOf(fall, rest)
                val x = rnd(k) * w + if (y < rest) sin(tf / 300f + k) * 6f else 0f
                val r = rnd(k + 1) * 360f + if (y < rest) age * 120f else 0f
                withTransform({ translate(x, y); rotate(r, Offset.Zero) }) {
                    drawOval(c[1 + (rnd(k + 2) * 3).toInt().coerceAtMost(2)], Offset(-5 * density, -8 * density), Size(10 * density, 16 * density), blendMode = blend)
                }
            }
        }
        Glyph.BRILHO -> {
            drawRect(c[0], Offset(0f, surf), Size(w, bot - surf + 20f), blendMode = blend)
            for (k in 0 until 60) {
                val y = top + rnd(k + 99) * h
                if (y < surf) continue
                val tw = 0.5f + 0.5f * sin(tf / 400f + rnd(k + 3) * 6f)
                star(c[3], Offset(rnd(k) * w, y), (0.6f + rnd(k + 5) * 1.6f) * 8 * density * (0.6f + tw * 0.6f), glyph = Glyph.BRILHO)
            }
        }
        Glyph.ASTERISCO -> {
            drawRect(c[1], Offset(0f, surf + 22 * density), Size(w, bot), blendMode = blend)
            var x = 0f
            while (x < w) {
                val hg = (26 + rnd(x.toInt()) * 26) * density; val sw = sin(tf / 600f + x / 30f) * 6 * density
                val p = Path().apply { moveTo(x, surf + 34 * density); quadraticBezierTo(x + sw / 2, surf + 22 * density - hg / 2, x + sw, surf + 34 * density - hg) }
                drawPath(p, c[2], style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * density, cap = StrokeCap.Round), blendMode = blend)
                x += 4 * density
            }
        }
        Glyph.SOL -> {
            drawRect(Brush.verticalGradient(listOf(c[3].copy(alpha = 0f), c[3], c[2]), startY = surf - 30 * density, endY = bot), Offset(0f, surf - 30 * density), Size(w, bot - surf + 30 * density), blendMode = blend)
            for (k in 0 until 40) {
                val span = (bot - surf).coerceAtLeast(1f)
                val y = bot - ((tf / 20f + rnd(k + 3) * 400f) % span)
                drawCircle(Color(0xFFFFF6D5).copy(alpha = 0.6f), 1.6f * density, Offset(rnd(k) * w, y), blendMode = blend)
            }
        }
        Glyph.CARIMBO -> {
            val n = 10
            for (k in 0 until n) {
                val y1 = bot - (k + 1) * h / n
                if (y1 + h / n < surf) break
                drawRect(c[if (k % 2 == 1) 2 else 3], Offset(0f, maxOf(y1, surf)), Size(w, h / n + 1), blendMode = blend)
            }
            for (k in 0 until 160) drawRect(Color.Black.copy(alpha = 0.15f), Offset(rnd(k) * w, surf + rnd(k + 4) * (bot - surf)), Size(1.6f * density, 1.6f * density), blendMode = blend)
        }
        Glyph.LAPIS -> {
            var k = 0
            while (bot - k * 7 * density > surf - 20 * density) {
                val y = bot - k * 7 * density
                drawLine(c[3], Offset(-10f, y + 6 * density + sin(k.toFloat()) * 2f), Offset(w + 10f, y - 22 * density + cos(k.toFloat()) * 2f), 2.2f * density, cap = StrokeCap.Round, blendMode = blend)
                k++
            }
        }
    }
}

/** O campo vivo atrás do número: o material da cor, quase sem opacidade. */
fun DrawScope.livingField(pal: Palette, t: Long, dark: Boolean) {
    val w = size.width; val h = size.height
    val c = pal.levels.drop(1)
    val tf = t.toFloat()
    val a = if (dark) 0.2f else 0.26f
    when (pal.glyph) {
        Glyph.FLOR -> for (k in 0 until 50) {
            val y = (rnd(k + 41) * h + tf * 0.02f * (0.75f + rnd(k + 42) * 1.5f)) % (h + 20f) - 10f
            val x = rnd(k + 40) * w + sin(tf / 900f + k) * 8 * density; val s = 0.5f + rnd(k + 44)
            withTransform({ translate(x, y); rotate(rnd(k + 43) * 360f + tf / 40f, Offset.Zero) }) {
                drawOval(c[1].copy(alpha = a), Offset(-(3 * s + 1) * density, -(5 * s + 2) * density), Size((6 * s + 2) * density, (10 * s + 4) * density))
            }
        }
        Glyph.ESTRELA_MAR -> for (k in 0 until 15) {
            val p = Path(); var x = 0f
            while (x <= w) { val y = 50 * density + k * 28 * density + sin(x / 40f + tf / 900f + k) * 8 * density + sin(x / 17f - tf / 1300f) * 3 * density; if (x == 0f) p.moveTo(x, y) else p.lineTo(x, y); x += 8f }
            drawPath(p, c[2].copy(alpha = a), style = androidx.compose.ui.graphics.drawscope.Stroke(1.4f * density))
        }
        Glyph.CRISTAL -> {
            val g = 0.5f + 0.5f * sin(tf / 3000f)
            for (k in 0 until 20) {
                val x = rnd(k) * w; val y = 40 * density + rnd(k + 2) * (h - 40 * density); val l = (8 + g * 34 * rnd(k + 5)) * density
                for (s in 0 until 6) { val th = s * PI / 3; drawLine(c[3].copy(alpha = a), Offset(x, y), Offset(x + cos(th).toFloat() * l, y + sin(th).toFloat() * l), 1 * density) }
            }
        }
        Glyph.BRILHO -> for (k in 0 until 50) {
            star(c[3].copy(alpha = a), Offset(rnd(k + 40) * w, rnd(k + 41) * h), (5 + 6 * (0.5f + 0.5f * sin(tf / 500f + k))) * density, glyph = Glyph.BRILHO)
        }
        Glyph.SOL -> for (k in 0 until 7) {
            val an = 2.3f + k * 0.2f + sin(tf / 2500f) * 0.08f
            drawLine(c[3].copy(alpha = a * 0.8f), Offset(w + 20f, -20f), Offset(w + 20f + cos(an) * 900f, -20f + sin(an) * 900f), 18 * density)
        }
        Glyph.ASTERISCO -> { var x = 0f
            while (x < w) {
                val hg = (40 + rnd(x.toInt()) * 80) * density; val sw = sin(tf / 900f + x / 40f) * 10 * density
                val p = Path().apply { moveTo(x, h); quadraticBezierTo(x + sw / 2, h - hg / 2, x + sw, h - hg) }
                drawPath(p, c[2].copy(alpha = a), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * density, cap = StrokeCap.Round))
                x += 7 * density
            }
        }
        Glyph.CARIMBO -> {
            for (k in 0 until 700) drawRect(c[2].copy(alpha = a), Offset(rnd(k) * w, rnd(k + 1) * h), Size(1.5f * density, 1.5f * density))
            for (k in 0 until 10) drawArc(
                c[3].copy(alpha = a), 110f, 140f, false, Offset(w * 0.85f - (30 + k * 22) * density, 110 * density - (30 + k * 22) * density),
                Size((60 + k * 44) * density, (60 + k * 44) * density), style = androidx.compose.ui.graphics.drawscope.Stroke(0.9f * density),
            )
        }
        Glyph.LAPIS -> for (k in 0 until 170) {
            val x = rnd(k) * w; val y = rnd(k + 1) * h; val an = rnd(k + 2) * 2 * PI
            drawLine(c[2].copy(alpha = a), Offset(x, y), Offset(x + cos(an).toFloat() * 14 * density, y + sin(an).toFloat() * 14 * density), 0.6f * density)
        }
    }
}

