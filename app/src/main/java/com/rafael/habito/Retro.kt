package com.rafael.habito

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Debug: força a retrospectiva do mês passado na próxima abertura (`--ez show_retro true`). */
object RetroDebug { var force by mutableStateOf(false) }

private val PT = Locale("pt", "BR")
private val NAME = DateTimeFormatter.ofPattern("MMMM", PT)

/** Números de um mês fechado (ou em curso, contando até hoje). */
class Retro(val done: Int, val possible: Int, val longest: Int, val prevDone: Int?, val milestones: Int) {
    val pct: Int get() = if (possible == 0) 0 else done * 100 / possible
    val delta: Int? get() = prevDone?.let { done - it }
    val deltaLabel: String get() = delta?.let { if (it > 0) "+$it" else if (it == 0) "=" else "−${-it}" } ?: "—"
}

fun retroOf(u: HabitUi, m: YearMonth): Retro {
    val s = u.state
    fun count(mm: YearMonth): Pair<Int, Int> {
        var done = 0; var possible = 0
        for (day in 1..mm.lengthOfMonth()) {
            val d = mm.atDay(day)
            if (d.isAfter(u.today)) break
            possible++
            if (d in s.done) done++
        }
        return done to possible
    }
    val (done, possible) = count(m)
    var run = 0; var longest = 0
    for (day in 1..m.lengthOfMonth()) {
        val d = m.atDay(day)
        if (d.isAfter(u.today)) break
        if (d in s.done) { run++; if (run > longest) longest = run } else run = 0
    }
    val prev = m.minusMonths(1)
    val hadPrev = s.done.any { YearMonth.from(it) == prev } || s.missed.any { YearMonth.from(it) == prev }
    val ms = u.milestones.keys.count { YearMonth.from(it) == m }
    return Retro(done, possible, longest, if (hadPrev) count(prev).first else null, ms)
}

/** Houve algum registro no mês? (sem registro, não há o que resumir) */
fun hasData(u: HabitUi, m: YearMonth) = u.state.done.any { YearMonth.from(it) == m } || u.state.missed.any { YearMonth.from(it) == m }

/**
 * Retrospectiva: quando o mês vira, uma página curta sobre o mês que passou. Nome grande, quantos dias,
 * a comparação com o mês anterior em uma frase, o calendário acendendo dia a dia, e o cartão para compartilhar.
 */
@Composable
fun RetroScreen(u: HabitUi, m: YearMonth, back: Float, onClose: () -> Unit, onShare: () -> Unit) {
    val pal = Tokens
    val r = remember(u, m) { retroOf(u, m) }
    val name = m.format(NAME).replaceFirstChar { it.uppercase() }
    val prevName = m.minusMonths(1).format(NAME)
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { val s = 1f - 0.06f * back; scaleX = s; scaleY = s; alpha = 1f - 0.3f * back }
            .background(pal.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = Space.gutter, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Caps("Retrospectiva", color = pal.text3, modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(Radius.control)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_x), "Fechar", Modifier.size(20.dp), tint = pal.ink)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = 40.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text(name, style = Type.monthTitle, color = pal.ink, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${m.year}", style = Type.title.copy(fontWeight = FontWeight.Medium), color = pal.text3, modifier = Modifier.padding(bottom = 4.dp))
            }
            Rule()
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(4f)) { RollingNumber("${r.done}", pal.ink, Type.hero.copy(fontSize = 120.sp, lineHeight = 112.sp, letterSpacing = (-5).sp)) }
                Text("dias feitos\nde ${r.possible}", style = Type.title.copy(fontWeight = FontWeight.Medium, lineHeight = 20.sp), color = pal.text2, modifier = Modifier.weight(2f).padding(bottom = 14.dp))
            }
            val line = when {
                r.delta == null -> "O primeiro mês registrado."
                r.delta!! > 0 -> "${r.delta} ${if (r.delta == 1) "dia" else "dias"} a mais que em $prevName."
                r.delta!! < 0 -> "${-r.delta!!} ${if (r.delta == -1) "dia" else "dias"} a menos que em $prevName."
                else -> "O mesmo que em $prevName."
            }
            Text(line, style = Type.title.copy(fontWeight = FontWeight.Medium), color = pal.ink, modifier = Modifier.padding(top = 22.dp))
            Row(Modifier.fillMaxWidth().padding(top = 26.dp)) {
                Figure("${r.pct}%", "do mês", Type.stat, Modifier.weight(1f))
                Figure("${r.longest}", "maior sequência", Type.stat, Modifier.weight(1f))
                Figure("${r.milestones}", if (r.milestones == 1) "marco" else "marcos", Type.stat, Modifier.weight(1f))
            }
            Spacer(Modifier.height(28.dp))
            Hairline()
            Spacer(Modifier.height(Space.m))
            MiniMonth(u, m)
            Spacer(Modifier.height(28.dp))
            OutlineButton("Compartilhar o mês", R.drawable.ic_share, onShare, Modifier.fillMaxWidth())
        }
    }
}

/** O calendário do mês, sem toque, acendendo um dia depois do outro. */
@Composable
private fun MiniMonth(u: HabitUi, m: YearMonth) {
    val pal = Tokens
    val reduce = LocalReduceMotion.current
    val offset = Cal.rowOf(m.atDay(1))
    val days = m.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    val lit = remember(m) { Animatable(if (reduce) days.toFloat() else 0f) }
    LaunchedEffect(m) { if (!reduce) { delay(250); lit.animateTo(days.toFloat(), tween(days * 28)) } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1.6f)) {
                        if (day in 1..days) {
                            val d = m.atDay(day)
                            val out = d.isAfter(u.today)
                            val level = u.level(d)
                            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (lit.value - day + 1).coerceIn(0.25f, 1f) }) {
                                val cr = CornerRadius(Radius.cell.toPx())
                                drawRoundRect(if (out) pal.surface else pal.levels[level], cornerRadius = cr)
                                if (!out && u.markOf(d) == Mark.MISSED) drawCircle(pal.miss, 2.5.dp.toPx(), Offset(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()))
                            }
                            Text("$day", style = Type.caption, color = if (out) pal.text3 else pal.levelInk[level], fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
