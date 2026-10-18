package com.rafael.habito

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT = Locale("pt", "BR")
private val MONTH_NAME = DateTimeFormatter.ofPattern("MMMM", PT)
private val DOW_LONG = arrayOf("domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado")
private fun ym(m: YearMonth) = m.format(MONTH_NAME).replaceFirstChar { it.uppercase() }

/** Meses do período, do mais recente ao mais antigo, dentro da janela de 53 semanas. */
internal fun monthsIn(u: HabitUi, period: Int): List<YearMonth> {
    val out = mutableListOf<YearMonth>()
    var m = YearMonth.from(u.today)
    val first = YearMonth.from(u.start)
    while (m >= first) { if (period == 0 || m.year == period) out += m; m = m.minusMonths(1) }
    return out
}

/** Anos que aparecem na janela, do mais recente ao mais antigo. */
internal fun yearsIn(u: HabitUi): List<Int> = (u.today.year downTo u.start.year).toList()

/** Feitos no período (12 meses = a janela inteira). */
internal fun doneIn(u: HabitUi, period: Int): Int =
    u.state.done.count { !it.isBefore(u.start) && !it.isAfter(u.today) && (period == 0 || it.year == period) }

/**
 * A aba História: olhar para trás e entender o ritmo. Período, mapa do ano, meses em lista, ritmo, números e as
 * retrospectivas guardadas. Meses e retrospectivas abrem recortados a partir da linha tocada ([onMonth], [onRetro]).
 */
@Composable
internal fun HistoriaPage(
    u: HabitUi, scroll: ScrollState, period: Int, onPeriod: (Int) -> Unit,
    map: @Composable () -> Unit,
    onMonth: (YearMonth, Rect) -> Unit, onRetro: (YearMonth, Rect) -> Unit,
) {
    val pal = Tokens
    val years = remember(u.today, u.start) { yearsIn(u) }
    val options = listOf(0) + years
    val total = remember(u, period) { doneIn(u, period) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .navigationBarsPadding()
            .padding(horizontal = Space.gutter)
            .padding(top = Space.s),
    ) {
        Column(Modifier.entrance(0)) {
            Rule(index = 0)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text("História", style = Type.monthTitle, color = pal.ink, modifier = Modifier.weight(1f).padding(bottom = 4.dp))
                RollingLabel(if (total == 1) "1 dia feito" else "$total dias feitos", Type.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)), pal.text3, Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            Track(options.map { if (it == 0) "12 meses" else "$it" }, options.indexOf(period).coerceAtLeast(0), { onPeriod(options[it]) })
        }

        // Mapa (o bloco do ano, com anotação e compartilhar) vem da casca.
        Column(Modifier.padding(top = Space.section).entrance(1)) {
            Rule(index = 1)
            map()
        }

        Column(Modifier.padding(top = Space.section).entrance(2)) {
            Rule(index = 2)
            SectionHead("Meses", "toque para abrir")
            Spacer(Modifier.height(4.dp))
            monthsIn(u, period).forEachIndexed { k, m -> MonthRow(u, m, k, onMonth) }
        }

        Column(Modifier.padding(top = Space.section).entrance(3)) {
            Rule(index = 3)
            SectionHead("Ritmo", "últimas 12 semanas")
            Rhythm(u)
        }

        val returns = remember(u) { u.state.runs.filter { it.end.isAfter(u.today.minusDays(365)) } }
        if (!u.weekly && returns.size >= 2) Column(Modifier.padding(top = Space.section).entrance(4)) {
            Rule(index = 4)
            SectionHead("Voltas", "últimos 12 meses")
            Returns(u, returns)
        }

        val opened = remember(u) { u.state.letters.filter { it.opened != null }.sortedByDescending { it.opened } }
        if (opened.isNotEmpty()) Column(Modifier.padding(top = Space.section).entrance(4)) {
            Rule(index = 4)
            SectionHead("Frases", if (opened.size == 1) "1 aberta" else "${opened.size} abertas")
            opened.forEach { LetterRow(it) }
        }

        Column(Modifier.padding(top = Space.section).entrance(4)) {
            Rule(index = 4)
            SectionHead("Números", "desde ${u.start.format(SINCE)}")
            Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Figure("${u.best}", if (u.weekly) "recorde (sem.)" else "recorde", Type.number, Modifier.weight(1f))
                Figure("${u.pct30}%", "30 dias", Type.number, Modifier.weight(1f))
                Figure("${u.year}", "no ano", Type.number, Modifier.weight(1f))
            }
        }

        val closed = monthsIn(u, period).filter { it < YearMonth.from(u.today) && hasData(u, it) }.take(6)
        if (closed.isNotEmpty()) Column(Modifier.padding(top = Space.section).entrance(5)) {
            Rule(index = 5)
            SectionHead("Retrospectivas", "por mês")
            Spacer(Modifier.height(4.dp))
            closed.forEach { m -> RetroRow(u, m, onRetro) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 80.dp)) {
            Text("Hábito 4.5", style = Type.caption, color = pal.text3, modifier = Modifier.weight(1f))
            Text("um hábito, um quadrado por dia", style = Type.caption, color = pal.text3)
        }
    }
}

/**
 * Voltas: quantas vezes você voltou depois de parar, quanto tempo levou e uma linha do tempo
 * das sequências (cada trecho é uma sequência, cada fresta uma volta). Tocar num trecho conta dele.
 */
@Composable
private fun Returns(u: HabitUi, runs: List<Run>) {
    val pal = Tokens
    val (n, avg) = remember(u) { u.state.returnsIn(365) }
    val best = runs.drop(1).maxOfOrNull { it.days } ?: 0
    var picked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(-1) }
    Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Figure("$n", if (n == 1) "volta" else "voltas", Type.number, Modifier.weight(1f))
        Figure("%.1f".format(PT, avg).removeSuffix(",0") + "d", "até voltar", Type.number, Modifier.weight(1f))
        Figure("$best", "maior volta", Type.number, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 22.dp).height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        runs.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.weight((r.gapBefore + 2).toFloat()).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(pal.text3.copy(alpha = 0.5f)))
            }
            val cur = i == runs.lastIndex && u.streak > 0
            val sel = picked == i
            val h by animateFloatAsState(if (sel) 1f else 0.75f, Motion.press, label = "run")
            Box(
                Modifier.weight(maxOf(r.days, 2).toFloat()).fillMaxHeight()
                    .clickable(remember { MutableInteractionSource() }, indication = null) { picked = if (sel) -1 else i },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val bh = size.height * h
                    val c = when { cur -> pal.done; r.days >= 25 -> pal.levels[4]; else -> pal.levels[3] }
                    drawRoundRect(c, Offset(0f, (size.height - bh) / 2), Size(size.width, bh), CornerRadius(2.dp.toPx()))
                    if (sel) drawRoundRect(pal.ink, Offset(-2.dp.toPx(), (size.height - bh) / 2 - 2.dp.toPx()), Size(size.width + 4.dp.toPx(), bh + 4.dp.toPx()), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
    val r = runs.getOrNull(picked)
    val text = when {
        r == null -> ""
        picked == runs.lastIndex && u.streak > 0 -> "Agora · ${r.days} ${if (r.days == 1) "dia" else "dias"}" + (if (r.gapBefore > 0) " · depois de ${r.gapBefore} ${if (r.gapBefore == 1) "dia" else "dias"}" else "")
        else -> "${Cal.shortDate(r.start).substringAfter(", ")} – ${Cal.shortDate(r.end).substringAfter(", ")} · ${r.days} ${if (r.days == 1) "dia" else "dias"}" +
            (if (picked > 0) " · depois de ${r.gapBefore} ${if (r.gapBefore == 1) "dia" else "dias"}" else "")
    }
    Text(text, style = Type.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)), color = pal.text2, modifier = Modifier.padding(top = 10.dp).height(18.dp))
}

/** Uma frase aberta: o asterisco do marco, quando abriu e o texto. */
@Composable
private fun LetterRow(l: Letter) {
    val pal = Tokens
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(12.dp)) { star(pal.levels[4], center, size.width, glyph = pal.glyph) }
            Spacer(Modifier.width(8.dp))
            Text("dia ${l.forDay} · escrita em ${Cal.shortDate(l.written).substringAfter(", ")}", style = Type.caption, color = pal.text3)
        }
        Text("“${l.text}”", style = Type.title.copy(fontWeight = FontWeight.Medium), color = pal.ink, modifier = Modifier.padding(top = 6.dp))
    }
    Hairline()
}

/** Uma linha por mês: nome, quantos dias, uma barra fina do quanto foi feito e o minimapa do mês. */
@Composable
private fun MonthRow(u: HabitUi, m: YearMonth, index: Int, onOpen: (YearMonth, Rect) -> Unit) {
    val pal = Tokens
    val r = remember(u, m) { retroOf(u, m) }
    val rect = remember { arrayOf(Rect.Zero) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 0.985f else 1f, Motion.press, label = "row")
    // A barra cresce até a fração do mês, com um pequeno atraso por linha.
    val bar = remember(m) { Animatable(0f) }
    val pct = if (r.possible == 0) 0f else r.done.toFloat() / r.possible
    val reduce = LocalReduceMotion.current
    LaunchedEffect(pct) { if (reduce) bar.snapTo(pct) else { delay(index * 40L); bar.animateTo(pct, tween(600, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) } }
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rect[0] = it.boundsInRoot() }
            .scale(scale)
            .clickable(press, indication = null) { onOpen(m, rect[0]) },
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ym(m), style = Type.title.copy(fontWeight = FontWeight.Medium), color = pal.ink)
                Text("${r.done} de ${r.possible} dias" + if (m.year != u.today.year) " · ${m.year}" else "", style = Type.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)), color = pal.text2)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(pal.rule)) {
                    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = bar.value; transformOrigin = TransformOrigin(0f, 0.5f) }.background(pal.done))
                }
            }
            Spacer(Modifier.width(16.dp))
            MiniMonth(u, m)
        }
        Hairline()
    }
}

/** Minimapa do mês: 7 colunas de quadradinhos de 7 dp. */
@Composable
private fun MiniMonth(u: HabitUi, m: YearMonth) {
    val pal = Tokens
    val offset = Cal.rowOf(m.atDay(1))
    val rows = (offset + m.lengthOfMonth() + 6) / 7
    Canvas(Modifier.size(width = (7 * 7 + 6 * 2).dp, height = (rows * 7 + (rows - 1) * 2).dp)) {
        val c = 7.dp.toPx(); val g = 2.dp.toPx(); val r = CornerRadius(1.dp.toPx())
        for (day in 1..m.lengthOfMonth()) {
            val i = day - 1 + offset
            val d = m.atDay(day)
            val tl = Offset((i % 7) * (c + g), (i / 7) * (c + g))
            if (d.isAfter(u.today) || d.isBefore(u.start)) {
                drawRoundRect(pal.rule, tl, Size(c, c), r, style = Stroke(1.dp.toPx()))
                continue
            }
            when {
                d in u.milestones -> starCell(pal, tl, c)
                d in u.state.scars -> { drawRoundRect(pal.levels[0], tl, Size(c, c), r); stitch(pal, tl, c, c, g, vertical = false) }
                else -> {
                    drawRoundRect(pal.levels[u.level(d)], tl, Size(c, c), r)
                    if (d in u.state.tropes) tropeMark(pal, tl, c)
                    else if (u.markOf(d) == Mark.MISSED) drawCircle(pal.miss, c * 0.22f, tl + Offset(c / 2, c / 2))
                }
            }
        }
    }
}

/** O dia da semana mais forte e a tendência das últimas 8 semanas, em barras finas que crescem. */
@Composable
private fun Rhythm(u: HabitUi) {
    val pal = Tokens
    val data = remember(u) {
        val wd = IntArray(7)
        var d = u.today.minusDays(83)
        while (!d.isAfter(u.today)) { if (d in u.state.done) wd[d.dow]++; d = d.plusDays(1) }
        val ws = u.state.weekStart(u.today)
        val weeks = (7 downTo 0).map { w -> u.state.doneInWeek(ws.minusWeeks(w.toLong())) }
        wd to weeks
    }
    val (wd, weeks) = data
    val top = wd.max()
    val best = wd.indexOfFirst { it == top }
    val avg = weeks.take(7).average()
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                RollingLabel(if (top > 0) DOW_LONG[best] else "—", Type.number, pal.ink)
                Spacer(Modifier.width(10.dp))
                Text("é o seu dia mais forte", style = Type.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)), color = pal.text2, modifier = Modifier.padding(bottom = 4.dp))
            }
            Bars((0..6).map { wd[Cal.dowAt(it)] }, (0..6).map { Cal.INITIALS[it] }, max = maxOf(1, top), highlight = (0..6).indexOfFirst { Cal.dowAt(it) == best }.takeIf { top > 0 }, outlineLast = false)
        }
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                RollingLabel("%.1f".format(PT, avg), Type.number, pal.ink)
                Spacer(Modifier.width(10.dp))
                Text("dias por semana, em média", style = Type.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)), color = pal.text2, modifier = Modifier.padding(bottom = 4.dp))
            }
            Bars(weeks, weeks.mapIndexed { i, n -> if (i == 7) "agora" else "$n" }, max = 7, highlight = null, outlineLast = true)
        }
    }
}

@Composable
private fun Bars(values: List<Int>, labels: List<String>, max: Int, highlight: Int?, outlineLast: Boolean) {
    val pal = Tokens
    val reduce = LocalReduceMotion.current
    Row(Modifier.fillMaxWidth().height(84.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEachIndexed { i, v ->
            val h by animateFloatAsState((v.toFloat() / max).coerceIn(0.05f, 1f), if (reduce) tween(0) else spring(0.6f, 300f), label = "bar")
            val last = outlineLast && i == values.lastIndex
            val color by animateColorAsState(
                when { i == highlight -> pal.ink; outlineLast -> pal.levels[2]; else -> pal.levels[0] }, motion(Motion.base), label = "barc",
            )
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    val bh = size.height * h
                    val tl = Offset(0f, size.height - bh)
                    if (last) drawRoundRect(pal.levels[3], tl, Size(size.width, bh), CornerRadius(2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    else drawRoundRect(color, tl, Size(size.width, bh), CornerRadius(2.dp.toPx()))
                }
                Text(
                    labels[i], style = Type.caption, color = if (i == highlight) pal.ink else pal.text3,
                    fontWeight = if (i == highlight) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

/** Uma retrospectiva guardada: mês, quantos dias e a comparação com o anterior. */
@Composable
private fun RetroRow(u: HabitUi, m: YearMonth, onOpen: (YearMonth, Rect) -> Unit) {
    val pal = Tokens
    val r = remember(u, m) { retroOf(u, m) }
    val rect = remember { arrayOf(Rect.Zero) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val nudge by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 3f else 0f, Motion.press, label = "nudge")
    val prev = m.minusMonths(1).format(MONTH_NAME).take(3)
    val delta = r.delta
    val cmp = when { delta == null -> "primeiro mês"; delta > 0 -> "+$delta vs. $prev"; delta < 0 -> "−${-delta} vs. $prev"; else -> "igual a $prev" }
    Column(Modifier.fillMaxWidth().onGloballyPositioned { rect[0] = it.boundsInRoot() }.clickable(press, indication = null) { onOpen(m, rect[0]) }) {
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ym(m) + if (m.year != u.today.year) " ${m.year}" else "", style = Type.value, color = pal.ink, modifier = Modifier.weight(1f))
            Text("${r.done} dias · $cmp", style = Type.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)), color = pal.text2)
            Spacer(Modifier.width(12.dp))
            Icon(painterResource(R.drawable.ic_arrow_out), null, Modifier.size(16.dp).graphicsLayer { translationX = nudge.dp.toPx(); translationY = -nudge.dp.toPx() }, tint = pal.text3)
        }
        Hairline()
    }
}
