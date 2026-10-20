package com.rafael.habito

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * Motion design (4.8, versão de teste; protótipo em design/motion): os momentos do app tratados como num vídeo
 * de lançamento. O som de cada momento vem de Sounds; aqui ficam o que é compartilhado entre telas (partículas,
 * o lançamento do marco, o voo das semanas até o mapa) e a fonte do número com largura e peso animáveis.
 */

/** Tempos do lançamento do marco: o swell e o impacto (o som do marco tem o impacto em 1,25 s). */
const val LAUNCH_RISE = 1250L
const val LAUNCH_TOTAL = 2300L
/** A régua acendendo depois do feito: começa em [LADDER_START] ms, um traço a cada [LADDER_STEP] ms. */
const val LADDER_START = 220L
const val LADDER_STEP = 55L

class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float,
    val size: Float, var rot: Float, val vr: Float, val color: Color, val glyph: Boolean,
)

/** Estado do motion compartilhado entre as telas. Pulsos são contadores: cada incremento dispara a animação uma vez. */
class FxState {
    var feito by mutableIntStateOf(0)
    var naoFiz by mutableIntStateOf(0)
    var launchAt by mutableLongStateOf(0L)      // uptime do começo do lançamento do marco; 0 = nenhum
    var flightAt by mutableLongStateOf(0L)      // uptime do começo do voo do ano até o mapa
    var frame by mutableIntStateOf(0)           // muda a cada quadro com partículas vivas: redesenha a camada
    var bursts by mutableIntStateOf(0)
    var openAt by mutableLongStateOf(0L)        // abertura do app
    var scrub by androidx.compose.runtime.mutableFloatStateOf(0f) // voltar no tempo: quantos dias atrás o número mostra

    var todayCell: Rect = Rect.Zero
    var number: Rect = Rect.Zero
    var miniGrid: Rect = Rect.Zero
    var heatOrigin: Offset = Offset.Unspecified   // canto do primeiro quadrado do mapa da História (raiz)
    var heatStep = 0f
    var heatCell = 0f
    var heatStart: LocalDate = LocalDate.now()
    var flight: List<FlightCell> = emptyList()

    val particles = ArrayList<Particle>()

    fun burst(at: Offset, n: Int, spread: Float, colors: List<Color>) {
        for (i in 0 until n) {
            val a = Random.nextFloat() * 2 * PI.toFloat()
            val v = (0.6f + Random.nextFloat()) * spread
            particles += Particle(
                at.x, at.y, cos(a) * v, sin(a) * v - spread * 0.4f, 0.7f + Random.nextFloat() * 0.6f,
                2.5f + Random.nextFloat() * 4.5f, Random.nextFloat() * 6f, (Random.nextFloat() - 0.5f) * 0.2f, colors[i % colors.size], i % 5 == 0,
            )
        }
        bursts++
    }
}

/** Um quadrado do mini ano a caminho do mapa: de onde sai, qual dia é (para achar o lugar no mapa) e a cor. */
class FlightCell(val from: Rect, val date: LocalDate, val color: Color, val col: Int)

val LocalFx = staticCompositionLocalOf { FxState() }

/** O número grande com largura (wdth) e peso (wght) quaisquer; quantizado e em cache para não criar fonte por quadro. */
private val heroFaces = HashMap<Int, FontFamily>()

@OptIn(ExperimentalTextApi::class)
fun heroFace(width: Float, weight: Float): FontFamily {
    val w = (width.coerceIn(62f, 125f) / 3f).toInt() * 3
    val g = (weight.coerceIn(100f, 900f) / 25f).toInt() * 25
    return heroFaces.getOrPut(w * 1000 + g) {
        FontFamily(Font(R.font.archivo, FontWeight(g), variationSettings = FontVariation.Settings(FontVariation.weight(g), FontVariation.width(w.toFloat()))))
    }
}

private val OUT = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private fun ease(x: Float) = OUT.transform(x.coerceIn(0f, 1f))

/**
 * A camada por cima de tudo: partículas, o véu e o clarão do marco, e os quadrados voando até o mapa.
 * Não recebe toque.
 */
@Composable
fun FxLayer(fx: FxState, pal: Palette) {
    // partículas: anda enquanto houver alguma viva
    LaunchedEffect(fx.bursts) {
        var last = withFrameMillis { it }
        while (fx.particles.isNotEmpty()) {
            withFrameMillis { now ->
                val dt = ((now - last) / 16.67f).coerceIn(0.2f, 3f); last = now
                val it = fx.particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    p.vx *= 0.965f.pow(dt); p.vy = p.vy * 0.965f.pow(dt) + 0.09f * dt
                    p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt; p.life -= 0.016f * dt
                    if (p.life <= 0f) it.remove()
                }
                fx.frame++
            }
        }
    }
    val launch = remember { Animatable(0f) }
    LaunchedEffect(fx.launchAt) {
        if (fx.launchAt == 0L) return@LaunchedEffect
        launch.snapTo(0f); launch.animateTo(LAUNCH_TOTAL.toFloat(), tween(LAUNCH_TOTAL.toInt(), easing = LinearEasing)); fx.launchAt = 0L
    }
    val flight = remember { Animatable(1f) }
    LaunchedEffect(fx.flightAt) {
        if (fx.flightAt == 0L) return@LaunchedEffect
        flight.snapTo(0f); flight.animateTo(1f, tween(760, easing = LinearEasing)); fx.flight = emptyList(); fx.flightAt = 0L
    }

    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") fx.frame
        // ----- marco: o véu escurece em volta do número, um brilho cresce atrás, e no impacto um anel se abre
        if (fx.launchAt != 0L && fx.number != Rect.Zero) {
            val t = launch.value
            val c = fx.number.center
            val rise = (t / LAUNCH_RISE).coerceIn(0f, 1f)
            val after = ((t - LAUNCH_RISE) / 900f).coerceIn(0f, 1f)
            val veil = if (t < LAUNCH_RISE) rise.pow(1.6f) * 0.62f else 0.62f * (1f - ease(after))
            if (veil > 0f) drawRect(
                Brush.radialGradient(
                    0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = veil * 0.15f), 1f to Color.Black.copy(alpha = veil),
                    center = c, radius = (max(size.width, size.height) * 0.75f).coerceAtLeast(1f),
                ),
            )
            val glow = if (t < LAUNCH_RISE) rise.pow(2f) * 0.28f else 0.28f * (1f - ease(after))
            if (glow > 0f) drawCircle(
                Brush.radialGradient(listOf(pal.levels[4].copy(alpha = glow), Color.Transparent), center = c, radius = (fx.number.width * (0.7f + 0.4f * rise + after)).coerceAtLeast(1f)),
                radius = fx.number.width * (0.7f + 0.4f * rise + after), center = c,
            )
            if (t >= LAUNCH_RISE) {
                // o glifo da cor gira para dentro no canto do número
                val g = ((t - LAUNCH_RISE) / 760f).coerceIn(0f, 1f)
                val ga = if (t < LAUNCH_RISE + 700) 1f else 1f - ((t - LAUNCH_RISE - 700) / 300f).coerceIn(0f, 1f)
                if (ga > 0f) {
                    val back = 1f + 2.4f * (g - 1f) * (g - 1f) * (g - 1f) + 1.4f * (g - 1f) * (g - 1f)
                    star(
                        pal.levels[4].copy(alpha = ga * g.coerceAtMost(0.25f) * 4f), Offset(fx.number.right + 14 * density, fx.number.top + 26 * density),
                        30 * density * back.coerceAtLeast(0f), turn = -150f * (1f - ease(g)), glyph = pal.glyph,
                    )
                }
                val k = ((t - LAUNCH_RISE) / 700f).coerceIn(0f, 1f)
                if (k < 1f) drawCircle(
                    pal.levels[4].copy(alpha = 0.55f * (1f - k)), radius = size.width * (0.15f + 0.9f * ease(k)), center = c,
                    style = Stroke(width = (6f * (1f - k) + 1f) * density),
                )
            }
        }
        // ----- o ano vira mapa: cada quadrado do mini ano voa até o seu dia no mapa, da coluna mais nova à mais velha
        if (fx.flightAt != 0L && fx.flight.isNotEmpty() && fx.heatOrigin != Offset.Unspecified) {
            val p = flight.value
            for (f in fx.flight) {
                val idx = ChronoUnit.DAYS.between(fx.heatStart, f.date).toInt()
                if (idx < 0) continue
                val to = Offset(fx.heatOrigin.x + (idx / 7) * fx.heatStep, fx.heatOrigin.y + (idx % 7) * fx.heatStep)
                val delay = (19 - f.col) * 0.012f
                val k = ease(((p - delay) / 0.72f).coerceIn(0f, 1f))
                val size0 = f.from.width; val size1 = fx.heatCell
                val s = size0 + (size1 - size0) * k
                // curva: sobe um pouco no meio do caminho
                val x = f.from.left + (to.x - f.from.left) * k
                val y = f.from.top + (to.y - f.from.top) * k - sin(k * PI.toFloat()) * 26f * density
                val fade = if (p > 0.88f) 1f - (p - 0.88f) / 0.12f else 1f
                drawRoundRect(f.color.copy(alpha = f.color.alpha * fade), Offset(x, y), Size(s, s), CornerRadius(Radius.cell.toPx()))
            }
        }
        // ----- partículas
        for (q in fx.particles) {
            val a = (q.life * 1.6f).coerceIn(0f, 1f)
            withTransform({ translate(q.x, q.y); rotate(q.rot * 57.3f, Offset.Zero) }) {
                if (q.glyph) star(q.color.copy(alpha = a), Offset.Zero, q.size * 2.6f * density, glyph = pal.glyph)
                else drawRect(q.color.copy(alpha = a), Offset(-q.size * density / 2, -q.size * density / 2), Size(q.size * density, q.size * density))
            }
        }
    }
}

/** Progresso (ms) do lançamento do marco para quem desenha o número; 0 fora dele. */
@Composable
fun launchClock(fx: FxState): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(fx.launchAt) {
        if (fx.launchAt == 0L) { a.snapTo(0f); return@LaunchedEffect }
        a.snapTo(0.001f); a.animateTo(LAUNCH_TOTAL.toFloat(), tween(LAUNCH_TOTAL.toInt(), easing = LinearEasing)); a.snapTo(0f)
    }
    return a.value
}

/**
 * Abertura: o glifo da cor surge numa varredura que gira, encolhe e some, enquanto a tela entra por baixo.
 * Uma vez por processo, só com animações ligadas.
 */
@Composable
fun OpeningGlyph(pal: Palette, onStart: () -> Unit, onEnd: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        onStart()
        t.animateTo(1000f, tween(1000, easing = LinearEasing))
        onEnd()
    }
    Canvas(Modifier.fillMaxSize()) {
        val ms = t.value
        val bgA = if (ms < 620f) 1f else 1f - ease((ms - 620f) / 320f)
        drawRect(pal.bg.copy(alpha = bgA))
        val spin = ease(ms / 560f)
        val shrink = ((ms - 480f) / 300f).coerceIn(0f, 1f)
        val grow = 1f + 0.12f * (1f - ease(ms / 200f)) // o ícone do splash sai em 1,12×
        val len = min(size.width, size.height) * 0.22f * grow * (1f - 0.85f * shrink * shrink)
        val a = 1f - shrink * shrink
        if (a <= 0f) return@Canvas
        val sym = pal.glyph.symmetry.takeIf { it > 0f } ?: 90f
        star(pal.levels[3].copy(alpha = a), center, len, turn = sym * spin, glyph = pal.glyph)
    }
}

/** Para a cor nova nascer da amostra tocada: os centros das amostras (raiz) e a origem do último toque. */
object InkFx {
    val centers = HashMap<String, Offset>()
    @Volatile var origin: Offset? = null
    @Volatile var armed = false // a folha de ajustes está aberta: vale a pena guardar o quadro para a tinta
    fun hypot0(a: Offset, w: Float, h: Float) = max(max(hypot(a.x, a.y), hypot(w - a.x, a.y)), max(hypot(a.x, h - a.y), hypot(w - a.x, h - a.y)))
}

fun uptime() = SystemClock.uptimeMillis()

/** A abertura com o glifo só uma vez por processo (voltar ao app não repete). */
object OpenOnce { @Volatile var done = false }

/** Segurar para marcar: quanto tempo o dedo fica no botão até valer. */
const val HOLD_MS = 600

/** Para o gesto de segurar saber se vibra e se soa, e em que cor. */
data class HoldFeedback(val haptics: Boolean, val sounds: Boolean, val palette: String)

/** A sequência como ela estava no fim do dia [d] (tropeços e descansos não quebram, como no app). */
fun streakAt(u: HabitUi, d: java.time.LocalDate): Int = u.state.streakAsOf(d)

/** A hora do dia (com minutos), atualizada a cada minuto. */
@Composable
fun rememberHour(): Float {
    val h = remember { androidx.compose.runtime.mutableFloatStateOf(hourNow()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); h.floatValue = hourNow() } }
    return h.floatValue
}

private fun hourNow(): Float { val t = java.time.LocalTime.now(); return t.hour + t.minute / 60f }

/**
 * A luz do dia, bem de leve, no alto do Hoje: manhã fria, tarde morna, fim de tarde alaranjado, noite anil.
 * Os tons se misturam devagar entre as horas.
 */
fun skyTint(hour: Float, dark: Boolean): Color {
    val keys = listOf(
        0f to Color(0xFF3C4690), 5.5f to Color(0xFF3C4690), 7.5f to Color(0xFF7FA6CC), 11f to Color(0xFF9BB4C8),
        14f to Color(0xFFCDB98A), 17f to Color(0xFFE0A060), 19f to Color(0xFFC86A4A), 20.5f to Color(0xFF4A4E9A), 24f to Color(0xFF3C4690),
    )
    val i = keys.indexOfLast { it.first <= hour }.coerceIn(0, keys.size - 2)
    val (h0, c0) = keys[i]; val (h1, c1) = keys[i + 1]
    val c = androidx.compose.ui.graphics.lerp(c0, c1, ((hour - h0) / (h1 - h0)).coerceIn(0f, 1f))
    return c.copy(alpha = if (dark) 0.10f else 0.07f)
}

/** Quanto o quadrado de hoje acende como lanterna: nada até as 17 h, cresce até as 22 h. */
fun lanternLevel(hour: Float): Float = ((hour - 17f) / 5f).coerceIn(0f, 1f)
