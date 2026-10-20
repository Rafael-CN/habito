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
fun rememberFrameTime(active: Boolean = true): State<Long> {
    val t = remember { mutableLongStateOf(0L) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(reduce, active) {
        if (reduce || !active) return@LaunchedEffect
        val start = withFrameMillis { it } - t.longValue
        var last = 0L
        while (true) withFrameMillis { if (it - last >= 32) { last = it; t.longValue = it - start } }
    }
    return t
}

/** O Hoje está à vista (página 0, sem folha, mês ou retrospectiva por cima): só assim o que é contínuo anda. */
val LocalHojeActive = androidx.compose.runtime.compositionLocalOf { true }

/** Pétalas que caem e assentam: a hora em que cada uma nasceu. */
class PetalState { val born = ArrayList<Long>() }

/**
 * O número vira recipiente: o texto (desenhado por [content]) fica num tom de vidro e o material da cor
 * o enche de baixo até [level] (0..1). O texto serve de máscara: nada vaza para fora dos algarismos.
 */
fun Modifier.materialFill(pal: Palette, level: () -> Float, time: () -> Long, petals: PetalState, tilt: () -> Float = { 0f }): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        val top = h * 0.1f; val bot = h * 0.94f
        val surf = bot - (bot - top) * level().coerceIn(0f, 1f)
        val t = time()
        // Nada passa da superfície: só a crista da onda pode subir um pouco. Assim o número nunca parece mais cheio
        // do que o dia em relação ao marco.
        // inclinado (graus): o material gira em volta do meio da superfície e cresce um pouco para não abrir os cantos
        val a = tilt()
        if (a == 0f) clipRect(top = surf - 8 * density) { drawMaterial(pal, t, surf, top, bot, petals, BlendMode.SrcAtop) }
        else withTransform({ rotate(a, Offset(size.width / 2, surf)); scale(1.18f, 1.18f, Offset(size.width / 2, surf)) }) {
            clipRect(left = -size.width, right = size.width * 2, top = surf - 8 * density, bottom = size.height * 2) {
                drawMaterial(pal, t, surf, top, bot + 40 * density, petals, BlendMode.SrcAtop)
            }
        }
    }

/** O tom de vidro do número vazio: um véu de tinta sobre o fundo, opaco (vira máscara do material). */
fun vessel(pal: Palette): Color = lerp(pal.bg, pal.ink, 0.13f)

private fun DrawScope.drawMaterial(pal: Palette, t: Long, surf: Float, top: Float, bot: Float, petals: PetalState, blend: BlendMode) {
    val w = size.width; val h = bot - top
    val c = pal.levels.drop(1) // tons 1..4
    val tf = t.toFloat()
    val d = density
    when (pal.glyph) {
        // Água: onda calma; no azul, a água é mais funda e uns poucos cristais flutuam nela.
        Glyph.ESTRELA_MAR, Glyph.CRISTAL -> {
            val ice = pal.glyph == Glyph.CRISTAL
            val wave = Path().apply {
                moveTo(0f, bot + 20f)
                var x = 0f
                while (x <= w) { lineTo(x, surf + sin(x / 28f + tf / 650f) * 5 * d + sin(x / 13f - tf / 420f) * 2 * d); x += 6f }
                lineTo(w, bot + 20f); close()
            }
            drawPath(wave, Brush.verticalGradient(listOf(if (ice) c[2] else c[3], c[1]), startY = surf, endY = bot), blendMode = blend)
            if (ice) for (k in 0 until 7) {
                val x = rnd(k) * w; val y = surf + (0.2f + rnd(k + 9) * 0.7f) * (bot - surf) + sin(tf / 1400f + k) * 3 * d
                star(Color.White.copy(alpha = 0.22f), Offset(x, y), (5 + rnd(k + 3) * 4) * d, tf / 90f + k * 40f, Glyph.CRISTAL, blend)
            }
        }
        // Pétalas caindo e assentando.
        Glyph.FLOR -> {
            val n = 420
            val want = ((bot - surf) / h * n).toInt().coerceIn(0, n)
            while (petals.born.size < want) petals.born.add(t + petals.born.size % 6 * 40L)
            while (petals.born.size > want) petals.born.removeAt(petals.born.size - 1)
            petals.born.forEachIndexed { k, born ->
                val rest = bot - (k.toFloat() / n) * h * 0.98f - rnd(k + 7) * 10 * d
                val age = (t - born).coerceAtLeast(0) / 1000f
                val fall = top - 60 * d + 0.5f * 2200f * age * age
                val y = minOf(fall, rest)
                val x = rnd(k) * w + if (y < rest) sin(tf / 300f + k) * 6f else 0f
                val r = rnd(k + 1) * 360f + if (y < rest) age * 120f else 0f
                withTransform({ translate(x, y); rotate(r, Offset.Zero) }) {
                    drawOval(c[1 + (rnd(k + 2) * 3).toInt().coerceAtMost(2)], Offset(-5 * d, -8 * d), Size(10 * d, 16 * d), blendMode = blend)
                }
            }
        }
        // Céu de noite: tom médio que escurece para baixo e poucas estrelas pequenas piscando devagar.
        Glyph.BRILHO -> {
            drawRect(Brush.verticalGradient(listOf(c[2], c[1]), startY = surf, endY = bot), Offset(0f, surf), Size(w, bot - surf + 20f), blendMode = blend)
            for (k in 0 until 16) {
                val y = surf + (0.08f + rnd(k + 99) * 0.84f) * (bot - surf)
                val tw = 0.5f + 0.5f * sin(tf / 900f + rnd(k + 3) * 6f)
                star(c[3].copy(alpha = 0.45f + 0.4f * tw), Offset(rnd(k) * w, y), (4 + rnd(k + 5) * 5) * d * (0.8f + tw * 0.3f), glyph = Glyph.BRILHO, blendMode = blend)
            }
        }
        // Grama: um campo que enche e uma franja de folhas curtas balançando na superfície.
        Glyph.ASTERISCO -> {
            drawRect(Brush.verticalGradient(listOf(c[2], c[1]), startY = surf + 6 * d, endY = bot), Offset(0f, surf + 6 * d), Size(w, bot), blendMode = blend)
            var x = 0f
            while (x < w) {
                val hg = (8 + rnd(x.toInt()) * 10) * d; val sw = sin(tf / 900f + x / 30f) * 3 * d
                val p = Path().apply { moveTo(x, surf + 10 * d); quadraticBezierTo(x + sw / 2, surf + 10 * d - hg / 2, x + sw, surf + 10 * d - hg) }
                drawPath(p, c[2], style = androidx.compose.ui.graphics.drawscope.Stroke(2f * d, cap = StrokeCap.Round), blendMode = blend)
                x += 4 * d
            }
        }
        // Âmbar: mel morno. A superfície brilha um pouco e escurece para o fundo; poucas bolhas sobem devagar.
        Glyph.SOL -> {
            drawRect(Brush.verticalGradient(listOf(c[2], c[1]), startY = surf, endY = bot), Offset(0f, surf), Size(w, bot - surf + 20f), blendMode = blend)
            drawRect(Brush.verticalGradient(listOf(c[3].copy(alpha = 0.55f), c[3].copy(alpha = 0f)), startY = surf, endY = surf + 26 * d), Offset(0f, surf), Size(w, 26 * d), blendMode = blend)
            val span = (bot - surf).coerceAtLeast(1f)
            for (k in 0 until 12) {
                val y = bot - ((tf / 45f + rnd(k + 3) * 400f) % span)
                drawCircle(c[3].copy(alpha = 0.35f), (1 + rnd(k + 6) * 1.6f) * d, Offset(rnd(k) * w, y), blendMode = blend)
            }
        }
        // Barro: camadas largas e macias, com bordas onduladas e um degradê entre elas.
        Glyph.ESPIRAL -> {
            val layers = 4
            for (k in 0 until layers) {
                val y0 = bot - (k + 1) * h / layers
                val edge = Path().apply {
                    moveTo(0f, bot + 20f); lineTo(0f, y0)
                    var x = 0f
                    while (x <= w) { lineTo(x, y0 + sin(x / 36f + k * 1.7f) * 3 * d); x += 8f }
                    lineTo(w, bot + 20f); close()
                }
                val col = if (k % 2 == 0) c[2] else c[1]
                drawPath(edge, Brush.verticalGradient(listOf(col, lerp(col, c[3], 0.35f)), startY = y0, endY = y0 + h / layers), blendMode = blend)
            }
        }
        // Grafite: hachura a lápis em diagonal, espaçada, sobre um cinza claro.
        Glyph.LAPIS -> {
            drawRect(c[0], Offset(0f, surf), Size(w, bot - surf + 20f), blendMode = blend)
            var k = 0
            while (bot - k * 6 * d > surf - 30 * d) {
                val y = bot - k * 6 * d
                drawLine(c[3].copy(alpha = 0.85f), Offset(-10f, y + 8 * d), Offset(w + 10f, y - 18 * d), 1.6f * d, cap = StrokeCap.Round, blendMode = blend)
                k++
            }
        }
    }
}

/**
 * O campo vivo atrás do número: o material da cor, sempre sutil (opacidade baixa, nada de brilho forte) e com
 * as bordas sumindo aos poucos. Cada cor tem uma ideia só, desenhada com calma.
 */
fun DrawScope.livingField(pal: Palette, t: Long, dark: Boolean) {
    val w = size.width; val h = size.height
    val c = pal.levels.drop(1)
    val tf = t.toFloat()
    val d = density
    val a = if (dark) 0.15f else 0.2f
    when (pal.glyph) {
        // Rosa: pétalas caindo devagar.
        Glyph.FLOR -> for (k in 0 until 50) {
            val y = (rnd(k + 41) * h + tf * 0.02f * (0.75f + rnd(k + 42) * 1.5f)) % (h + 20f) - 10f
            val x = rnd(k + 40) * w + sin(tf / 900f + k) * 8 * d; val s = 0.5f + rnd(k + 44)
            withTransform({ translate(x, y); rotate(rnd(k + 43) * 360f + tf / 40f, Offset.Zero) }) {
                drawOval(c[1].copy(alpha = a * 1.2f), Offset(-(3 * s + 1) * d, -(5 * s + 2) * d), Size((6 * s + 2) * d, (10 * s + 4) * d))
            }
        }
        // Oceano: linhas de maré.
        Glyph.ESTRELA_MAR -> for (k in 0 until 9) {
            val p = Path(); var x = 0f
            while (x <= w) { val y = 50 * d + k * 44 * d + sin(x / 40f + tf / 900f + k) * 8 * d + sin(x / 17f - tf / 1300f) * 3 * d; if (x == 0f) p.moveTo(x, y) else p.lineTo(x, y); x += 8f }
            drawPath(p, c[2].copy(alpha = a * 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.4f * d))
        }
        // Azul: neve fina — cristais pequenos descendo devagar, quase transparentes.
        Glyph.CRISTAL -> for (k in 0 until 18) {
            val y = (rnd(k + 2) * h + tf * 0.012f * (0.6f + rnd(k + 7))) % (h + 30f) - 15f
            val x = rnd(k) * w + sin(tf / 1600f + k) * 10 * d
            star(c[3].copy(alpha = a * 0.75f), Offset(x, y), (6 + rnd(k + 5) * 6) * d, tf / 120f + k * 30f, Glyph.CRISTAL)
        }
        // Violeta: céu com poucas estrelas pequenas, piscando devagar.
        Glyph.BRILHO -> for (k in 0 until 26) {
            val tw = 0.5f + 0.5f * sin(tf / 1100f + k * 1.3f)
            star(c[3].copy(alpha = a * (0.4f + 0.6f * tw)), Offset(rnd(k + 40) * w, rnd(k + 41) * h), (3 + rnd(k + 44) * 4) * d * (0.85f + 0.3f * tw), glyph = Glyph.BRILHO)
        }
        // Âmbar: um sol baixo no canto. Halo radial e raios que nascem dele e somem aos poucos, girando devagar.
        Glyph.SOL -> {
            val sun = Offset(w + 30 * d, -30 * d)
            val reach = h * 1.25f
            drawCircle(Brush.radialGradient(listOf(c[2].copy(alpha = a * 0.9f), c[2].copy(alpha = 0f)), center = sun, radius = reach * 0.7f), reach * 0.7f, sun)
            val sway = sin(tf / 5000f) * 0.06f
            for (k in 0 until 9) {
                val an = 1.75f + k * 0.17f + sway
                // três larguras sobrepostas, cada uma bem fraca: a borda do raio vira um degradê, sem corte
                for (half in floatArrayOf(0.075f, 0.05f, 0.028f)) {
                    val ray = Path().apply {
                        moveTo(sun.x, sun.y)
                        lineTo(sun.x + cos(an - half) * reach, sun.y + sin(an - half) * reach)
                        lineTo(sun.x + cos(an + half) * reach, sun.y + sin(an + half) * reach)
                        close()
                    }
                    drawPath(ray, Brush.radialGradient(listOf(c[3].copy(alpha = a * 0.13f), c[3].copy(alpha = a * 0.05f), c[3].copy(alpha = 0f)), center = sun, radius = reach))
                }
            }
        }
        // Verde: folhas pequenas levadas pelo vento, atravessando a tela na diagonal.
        Glyph.ASTERISCO -> for (k in 0 until 16) {
            val speed = 0.018f * (0.7f + rnd(k + 3) * 0.6f)
            val p = (rnd(k) * (w + h) + tf * speed) % (w + h + 60 * d) - 30 * d
            val x = p; val y = rnd(k + 1) * h * 0.9f + p * 0.25f + sin(tf / 1200f + k) * 10 * d
            val s = (0.7f + rnd(k + 2) * 0.6f) * d
            withTransform({ translate(x, y % (h + 20f)); rotate(rnd(k + 4) * 360f + tf / 30f * (if (k % 2 == 0) 1 else -1), Offset.Zero) }) {
                drawOval(c[2].copy(alpha = a * 1.1f), Offset(-3.5f * s, -7 * s), Size(7 * s, 14 * s))
                drawLine(c[3].copy(alpha = a * 0.8f), Offset(0f, -6 * s), Offset(0f, 6 * s), 0.8f * d)
            }
        }
        // Terracota: estratos de barro, faixas largas e macias que derivam devagar, com bordas que somem.
        Glyph.ESPIRAL -> for (k in 0 until 4) {
            val yc = h * (0.18f + k * 0.22f)
            val band = 34 * d
            val p = Path(); var x = -20f
            p.moveTo(x, yc + band)
            while (x <= w + 20f) { p.lineTo(x, yc + sin(x / 70f + tf / 4000f + k * 1.9f) * 10 * d); x += 10f }
            x = w + 20f
            while (x >= -20f) { p.lineTo(x, yc + band + sin(x / 60f + tf / 3500f + k) * 8 * d); x -= 10f }
            p.close()
            val col = if (k % 2 == 0) c[2] else c[1]
            drawPath(p, Brush.verticalGradient(listOf(col.copy(alpha = 0f), col.copy(alpha = a * 0.5f), col.copy(alpha = 0f)), startY = yc - 12 * d, endY = yc + band + 12 * d))
        }
        // Grafite: papel pautado de caderno, com a margem; linhas que somem nas pontas.
        Glyph.LAPIS -> {
            val gap = 28 * d
            var y = 40 * d
            val fade = Brush.horizontalGradient(listOf(c[2].copy(alpha = 0f), c[2].copy(alpha = a * 0.8f), c[2].copy(alpha = a * 0.8f), c[2].copy(alpha = 0f)))
            while (y < h) { drawRect(fade, Offset(0f, y), Size(w, 0.8f * d)); y += gap }
            drawRect(Brush.verticalGradient(listOf(c[3].copy(alpha = 0f), c[3].copy(alpha = a * 0.7f), c[3].copy(alpha = 0f))), Offset(36 * d, 0f), Size(0.8f * d, h))
        }
    }
}
