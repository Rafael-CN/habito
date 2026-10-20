package com.rafael.habito

import androidx.compose.runtime.mutableStateOf
import android.graphics.Matrix
import android.graphics.Region
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * O marco: o ano vira o glifo. Cada dia feito das últimas 20 semanas se solta do mapa, voa numa curva
 * e pousa num ponto do glifo da cor; o glifo gira uma vez na sua simetria, o número aparece embaixo e
 * os quadrados voltam, cada um para o seu dia. Tocar adianta a volta.
 */

private class Flyer(val from: Offset, val to: Offset, val color: Color, val order: Float, val spin: Float, val bend: Float)

private fun rndF(i: Int): Float = abs((sin(i * 12.9898 + 3.1) * 43758.5453) % 1.0).toFloat()
private fun cubic(x: Float): Float { val t = x.coerceIn(0f, 1f); return if (t < .5f) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2 }
private fun outBack(x: Float): Float { val t = x.coerceIn(0f, 1f) - 1f; val c = 1.4f; return 1f + (c + 1f) * t * t * t + c * t * t }

/** Pontos dentro do glifo, numa grade que dá pelo menos [n] pontos; e o passo dessa grade. */
private fun glyphPoints(g: Glyph, size: Float, n: Int): Pair<List<Offset>, Float> {
    val p = android.graphics.Path(Glyphs.path(g)).apply { transform(Matrix().apply { setScale(size / 20f, size / 20f) }) }
    val region = Region().apply { setPath(p, Region(0, 0, size.toInt() + 1, size.toInt() + 1)) }
    var step = size / 10f
    var pts: List<Offset> = emptyList()
    repeat(24) {
        val out = ArrayList<Offset>()
        var y = step / 2
        while (y < size) { var x = step / 2; while (x < size) { if (region.contains(x.toInt(), y.toInt())) out += Offset(x, y); x += step }; y += step }
        pts = out
        if (out.size >= n) return pts to step
        step *= 0.88f
    }
    return pts to step
}

private class Plan { var flyers: List<Flyer>? = null; var dot = 0f; var glyphSize = 0f; var center = Offset.Zero }

@Composable
fun YearGlyph(u: HabitUi, grid: Rect, marco: Int, startDelay: Long = 560, onEnd: () -> Unit) {
    val pal = Tokens
    val view = LocalView.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val end by rememberUpdatedState(onEnd)
    var t by remember { mutableLongStateOf(0L) }
    var release by remember { mutableLongStateOf(Long.MAX_VALUE) } // quando a volta começa (o toque adianta)
    var started by remember { mutableStateOf(false) }
    val hold = 2200L
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(startDelay) // primeiro o quadrado de hoje se enche (e, com motion, o lançamento do marco)
        started = true
        val start = withFrameMillis { it }
        var buzzed = false
        while (true) {
            withFrameMillis { t = it - start }
            if (!buzzed && t > 1380) { buzzed = true; if (u.state.haptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
            if (release == Long.MAX_VALUE && t >= hold) release = hold
            if (release != Long.MAX_VALUE && t > release + 1050) { end(); break }
        }
    }
    // O plano dos voos é montado no primeiro desenho (precisa do tamanho da tela); não é estado: não recompõe.
    val plan = remember { Plan() }

    Canvas(
        Modifier.fillMaxSize().then(if (!started) Modifier else Modifier.pointerInput(Unit) {
            detectTapGestures { if (release == Long.MAX_VALUE || release > t) release = maxOf(t, 400L) }
        }),
    ) {
        // Monta os voos na primeira medida.
        if (!started) return@Canvas
        if (plan.flyers == null) {
            val weeks = 20
            val gap = 3 * density.density
            val known = grid.width > 0f
            val cell = if (known) (grid.width - gap * (weeks - 1)) / weeks else 12 * density.density
            val first = u.today.minusDays(((weeks - 1) * 7 + Cal.rowOf(u.today)).toLong())
            val src = ArrayList<Pair<Offset, Color>>()
            for (col in 0 until weeks) for (row in 0 until 7) {
                val d = first.plusDays((col * 7 + row).toLong())
                if (d.isAfter(u.today) || u.markOf(d) != Mark.DONE) continue
                val o = if (known) Offset(grid.left + col * (cell + gap) + cell / 2, grid.top + row * (cell + gap) + cell / 2)
                else Offset(size.width * (col + 0.5f) / weeks, size.height + 40 * density.density + row * 14 * density.density)
                src += o to pal.levels[u.level(d).coerceAtLeast(1)]
            }
            val s = min(size.width * 0.74f, size.height * 0.40f)
            val c = Offset(size.width / 2, size.height * 0.40f)
            val (pts, step) = glyphPoints(pal.glyph, s, src.size)
            // Ordem por ângulo nos dois lados: cada quadrado vai para o ponto "do mesmo lado", e o voo vira um redemoinho.
            val mapC = if (src.isEmpty()) c else Offset(src.map { it.first.x }.average().toFloat(), src.map { it.first.y }.average().toFloat())
            val srcSorted = src.sortedBy { atan2(it.first.y - mapC.y, it.first.x - mapC.x) }
            val tgtAll = pts.map { it + c - Offset(s / 2, s / 2) }.sortedBy { atan2(it.y - c.y, it.x - c.x) }
            val tgt = if (tgtAll.isEmpty()) List(srcSorted.size) { c } else List(srcSorted.size) { i -> tgtAll[(i.toLong() * tgtAll.size / srcSorted.size.coerceAtLeast(1)).toInt()] }
            plan.flyers = srcSorted.mapIndexed { i, (o, col) ->
                Flyer(o, tgt[i], col, i.toFloat() / srcSorted.size.coerceAtLeast(1), (rndF(i) - 0.5f) * 140f, 0.18f + rndF(i + 9) * 0.14f)
            }
            // Com poucos dias, cada quadrado cresce até o passo da grade: o glifo fica cheio, não ralo.
            plan.dot = step * 0.84f
            plan.glyphSize = s; plan.center = c
        }
        val fl = plan.flyers ?: return@Canvas
        val dot = plan.dot; val glyphSize = plan.glyphSize; val center = plan.center
        val cell = if (grid.width > 0f) (grid.width - 3 * density.density * 19) / 20 else dot
        val rel = release.coerceAtMost(t)
        val back = if (t >= rel) ((t - rel) / 1000f) else 0f   // 0..~1 na volta
        // Véu: a tela recua para o fundo enquanto o glifo existe.
        val veil = (if (back > 0f) 1f - cubic(back / 0.9f) else cubic(t / 320f)) * 0.95f
        drawRect(pal.bg.copy(alpha = veil))

        // Silhueta do glifo, quase transparente, para o desenho ficar nítido quando os quadrados pousam.
        val sil = ((t - 1250) / 400f).coerceIn(0f, 1f) * (1f - (back / 0.4f).coerceIn(0f, 1f))
        val sym = pal.glyph.symmetry
        val spin = if (sym > 0f) cubic((t - 1650) / 1100f) * sym else 0f
        if (sil > 0f) star(pal.levels[4].copy(alpha = 0.14f * sil), center, glyphSize * 1.04f, spin, pal.glyph)

        val breath = 1f + 0.035f * sin(((t - 1450).coerceAtLeast(0) / 900f) * PI.toFloat()).coerceAtLeast(0f) * (1f - back.coerceIn(0f, 1f))
        fl.forEachIndexed { i, f ->
            // ida: atraso pela ordem (varre o glifo), 820 ms de voo
            val goStart = 240f + f.order * 380f
            val go = cubic((t - goStart) / 820f)
            // volta: ordem inversa, 640 ms
            val retStart = f.order * 260f
            val ret = if (back > 0f) cubic(((back * 1000f) - retStart) / 640f) else 0f
            val k = go * (1f - ret)
            // alvo girando com o glifo
            val a = spin * PI.toFloat() / 180f
            val rel0 = f.to - center
            val to = center + Offset(rel0.x * cos(a) - rel0.y * sin(a), rel0.x * sin(a) + rel0.y * cos(a)) * breath
            // curva: ponto de controle deslocado para o lado, sempre no mesmo sentido
            val mid = (f.from + to) / 2f
            val dir = to - f.from
            val len = hypot(dir.x, dir.y).coerceAtLeast(1f)
            val ctrl = mid + Offset(-dir.y / len, dir.x / len) * (len * f.bend) - Offset(0f, len * 0.12f)
            val u1 = 1 - k
            val p = f.from * (u1 * u1) + ctrl * (2 * u1 * k) + to * (k * k)
            // tamanho: levanta, encolhe até o ponto do glifo, pousa com um quique
            val lift = 1f + 0.18f * sin((t / 260f).coerceIn(0f, 1f) * PI.toFloat()) * (1f - go)
            val land = if (go >= 1f && ret == 0f) outBack(((t - goStart - 820f) / 260f)) else 1f
            val sz = (cell + (dot - cell) * k) * lift * (if (go >= 1f && ret == 0f) 0.85f + 0.15f * land else 1f)
            val rot = sin(k * PI.toFloat()) * f.spin + spin * k // pousados, giram junto com o glifo
            withTransform({ translate(p.x, p.y); rotate(rot, Offset.Zero) }) {
                drawRoundRect(f.color, Offset(-sz / 2, -sz / 2), Size(sz, sz), CornerRadius(sz * 0.14f))
            }
        }

        // O número do marco aparece embaixo do glifo.
        val num = ((t - 1500) / 450f).coerceIn(0f, 1f) * (1f - (back / 0.35f).coerceIn(0f, 1f))
        if (num > 0f) {
            val style = Type.hero.copy(fontSize = 104.sp, lineHeight = 100.sp, color = pal.ink)
            val layout = measurer.measure("$marco", style)
            val sc = 0.85f + 0.15f * num
            withTransform({ scale(sc, sc, Offset(center.x, center.y + glyphSize / 2 + 70 * density.density)) }) {
                drawText(layout, topLeft = Offset(center.x - layout.size.width / 2, center.y + glyphSize / 2 + 24 * density.density), alpha = num)
            }
            val cap = measurer.measure(if (u.weekly) "semanas" else "dias seguidos", Type.body.copy(color = pal.text2))
            drawText(cap, topLeft = Offset(center.x - cap.size.width / 2, center.y + glyphSize / 2 + 24 * density.density + layout.size.height + 4 * density.density), alpha = num)
        }
    }
}
