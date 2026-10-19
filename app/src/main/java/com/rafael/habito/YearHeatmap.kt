package com.rafael.habito

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val CELL = 14.dp
private val GAP = 3.dp
private val STEP = 17.dp
private val MONTH_H = 34.dp
private val LABEL_W = 30.dp
private val FADE_W = 18.dp

/** "feito", "tropeço", "fora · voltou 15 out", "não fiz", "descanso" ou "sem registro". */
fun dayState(u: HabitUi, d: LocalDate): String = when {
    d in u.state.scars -> u.state.runs.firstOrNull { it.start.isAfter(d) }?.let { "fora · voltou ${Cal.shortDate(it.start).substringAfter(", ")}" } ?: "fora"
    d in u.state.tropes -> "tropeço"
    u.markOf(d) == Mark.DONE -> "feito"
    u.markOf(d) == Mark.MISSED -> "não fiz"
    u.state.isRest(d) -> "descanso"
    else -> "sem registro"
}

/** Marco numa célula pequena: o quadrado vira asterisco, um pouco maior que ele. */
fun DrawScope.starCell(pal: Palette, tl: Offset, size: Float, turn: Float = 0f, scale: Float = 1f, alpha: Float = 1f) =
    star(pal.levels[4].copy(alpha = alpha), tl + Offset(size / 2, size / 2), size * Motion.STAR * scale, turn, pal.glyph)

/**
 * Costura: a linha dourada atravessa a lacuna de uma quebra (encostando nos vizinhos pela fresta [gap])
 * e um ponto curto a cruza no meio. [vertical] segue a coluna do mapa; nas fileiras (semana, mês) é horizontal.
 * [t] desenha: a linha corre primeiro, o ponto entra no fim.
 */
fun DrawScope.stitch(pal: Palette, tl: Offset, w: Float, h: Float, gap: Float, vertical: Boolean = true, t: Float = 1f) {
    if (t <= 0f) return
    val line = maxOf(1.5.dp.toPx(), minOf(w, h) * 0.1f)
    val c = tl + Offset(w / 2, h / 2)
    val run = (t / 0.7f).coerceAtMost(1f)
    val tick = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
    if (vertical) {
        drawRect(pal.gold, Offset(c.x - line / 2, tl.y - gap / 2), Size(line, (h + gap) * run))
        val half = w * 0.22f * tick
        if (half > 0f) drawRect(pal.gold, Offset(c.x - half, c.y - line / 2), Size(half * 2, line))
    } else {
        drawRect(pal.gold, Offset(tl.x - gap / 2, c.y - line / 2), Size((w + gap) * run, line))
        val half = h * 0.22f * tick
        if (half > 0f) drawRect(pal.gold, Offset(c.x - line / 2, c.y - half), Size(line, half * 2))
    }
}

/** Tropeço: um traço diagonal fino no quadrado vazio. */
fun DrawScope.tropeMark(pal: Palette, tl: Offset, size: Float, inset: Float = size * 0.26f) {
    drawLine(pal.text3.copy(alpha = 0.85f), tl + Offset(inset, size - inset), tl + Offset(size - inset, inset), strokeWidth = maxOf(1.dp.toPx(), size * 0.08f), cap = StrokeCap.Round)
}

/** Hoje esperando a volta (ontem ficou vazio): contorno tracejado no lugar do anel. */
fun DrawScope.callRing(pal: Palette, tl: Offset, w: Float, h: Float, radius: Float, breathe: Float = 0f) {
    val o = (1.5f + 1.5f * breathe).dp.toPx()
    val dash = 3.dp.toPx()
    drawRoundRect(
        pal.ink, tl - Offset(o, o), Size(w + 2 * o, h + 2 * o), CornerRadius(radius + o),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f))),
    )
}

/**
 * Um dia no vocabulário do app: ponto para "não fiz", anel de tinta para hoje pendente, asterisco para marco,
 * traço para tropeço. A costura e o giro do asterisco são animados à parte. Compartilhado pelo ano e pela semana.
 */
fun DrawScope.habitCell(
    pal: Palette, tl: Offset, size: Float, radius: Float, color: Color, mark: Mark?, isToday: Boolean, selected: Boolean,
    milestone: Boolean = false, trope: Boolean = false, call: Boolean = false,
) {
    val r = CornerRadius(radius)
    if (milestone) starCell(pal, tl, size)
    else drawRoundRect(color, tl, Size(size, size), r)
    if (trope) tropeMark(pal, tl, size)
    else if (mark == Mark.MISSED) drawCircle(pal.miss.copy(alpha = Alpha.marker), minOf(size * Motion.MISSED_DOT, 3.dp.toPx()), tl + Offset(size / 2, size / 2))
    if (isToday && mark == null) {
        if (call) callRing(pal, tl, size, size, radius)
        else {
            val i = 0.75.dp.toPx()
            drawRoundRect(pal.ink, tl + Offset(i, i), Size(size - 2 * i, size - 2 * i), r, style = Stroke(1.5.dp.toPx()))
        }
    }
    if (selected) {
        val o = 2.dp.toPx()
        drawRoundRect(pal.ink, tl - Offset(o, o), Size(size + 2 * o, size + 2 * o), CornerRadius(radius + o), style = Stroke(2.dp.toPx()))
    }
}

/** Cor e marcas de cada dia do ano (menos hoje, que vive na camada animada). Calculado uma vez por mudança de dados. */
private class StaticCells(
    val colors: Array<Color?>, val missed: BooleanArray, val milestone: BooleanArray, val rest: BooleanArray,
    val trope: BooleanArray, val scar: BooleanArray,
) {
    /** Asteriscos e costuras vivem na camada animada (giram e se desenham). */
    val stars: IntArray = milestone.indices.filter { milestone[it] }.toIntArray()
    val scars: IntArray = scar.indices.filter { scar[it] }.toIntArray()
}

private fun staticCells(u: HabitUi, pal: Palette): StaticCells {
    val n = YEAR_WEEKS * 7
    val colors = arrayOfNulls<Color>(n)
    val missed = BooleanArray(n)
    val milestone = BooleanArray(n)
    val rest = BooleanArray(n)
    val trope = BooleanArray(n)
    val scar = BooleanArray(n)
    for (i in 0 until n) {
        val d = u.start.plusDays(i.toLong())
        if (!d.isBefore(u.today)) continue // hoje e o futuro ficam de fora
        colors[i] = pal.levels[u.level(d)]
        scar[i] = d in u.state.scars
        trope[i] = d in u.state.tropes
        missed[i] = u.markOf(d) == Mark.MISSED && !scar[i] && !trope[i]
        rest[i] = u.state.isRest(d) && !missed[i]
        milestone[i] = d in u.milestones
    }
    return StaticCells(colors, missed, milestone, rest, trope, scar)
}

/**
 * O ano inteiro, como no perfil do GitHub. Rola na horizontal e abre em hoje. Tocar num dia o anota
 * (rótulo no eixo dos meses e uma linha até ele); tocar de novo muda o estado. Tocar num mês abre o mês.
 * [back] é o progresso do gesto de voltar, que desfaz a anotação acompanhando o dedo.
 */
@Composable
fun YearHeatmap(
    u: HabitUi, selected: LocalDate?, back: Float = 0f, celebrate: Int = 0, period: Int = 0,
    onMonth: (YearMonth) -> Unit = {}, onTap: (LocalDate) -> Unit,
) {
    val pal = Tokens
    val reduce = LocalReduceMotion.current
    val today = u.today
    val start = u.start
    val todayIndex = remember(start, today) { ChronoUnit.DAYS.between(start, today).toInt() }
    val cells = remember(u, pal) { staticCells(u, pal) }

    // Dias que mudaram de cor (correção pelo ano ou pela semana): saem da camada estática e fazem crossfade
    // na camada viva, como a transição de 450 ms das células do protótipo.
    val prevCells = remember { mutableStateOf(cells) }
    var fading by remember { mutableStateOf<Pair<StaticCells, IntArray>?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(cells) {
        val old = prevCells.value
        prevCells.value = cells
        if (old === cells || reduce || old.colors.size != cells.colors.size) return@LaunchedEffect
        val changed = cells.colors.indices.filter {
            it != todayIndex && (old.colors[it] != cells.colors[it] || old.missed[it] != cells.missed[it] || old.milestone[it] != cells.milestone[it])
        }.toIntArray()
        if (changed.isEmpty()) return@LaunchedEffect
        fading = old to changed
        fade.snapTo(0f)
        fade.animateTo(1f, tween(Motion.slow, easing = LinearEasing))
        fading = null
    }
    val fadingSet = remember(fading) { fading?.second?.toHashSet() ?: emptySet() }

    // Asteriscos: de tempos em tempos giram 60° em fila (a forma volta igual), como quem pisca.
    val turn = remember { Animatable(0f) }
    val stars = cells.stars
    LaunchedEffect(stars.size, reduce, pal.glyph) {
        if (reduce || stars.isEmpty() || pal.glyph.symmetry == 0f) return@LaunchedEffect
        while (true) {
            delay(Motion.STAR_EVERY)
            turn.snapTo(0f)
            turn.animateTo(1f + 0.12f * (stars.size - 1), tween(Motion.STAR_TURN + 84 * (stars.size - 1), easing = LinearEasing))
            turn.snapTo(0f)
        }
    }
    fun starAngle(k: Int): Float {
        val local = (turn.value - 0.12f * (stars.size - 1 - k)).coerceIn(0f, 1f) // o mais recente gira primeiro
        return FastOutSlowInEasing.transform(local) * pal.glyph.symmetry
    }
    // Costura: quando uma quebra ganha costura (a volta aconteceu), a linha corre e o ponto entra.
    val sewn = remember { mutableStateOf(cells.scars) }
    val sew = remember { Animatable(1f) }
    var fresh by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(cells.scars.contentHashCode()) {
        val before = sewn.value.toHashSet()
        sewn.value = cells.scars
        val new = cells.scars.filter { it !in before }.toSet()
        if (reduce || new.isEmpty()) return@LaunchedEffect
        fresh = new
        sew.snapTo(0f)
        delay(Motion.base.toLong())
        sew.animateTo(1f, tween(Motion.STITCH * new.size.coerceAtMost(3), easing = FastOutSlowInEasing))
        fresh = emptySet()
    }

    // Marco: a sequência acende em fila até hoje (cada dia cresce e volta), como no protótipo.
    val party = remember { Animatable(0f) }
    val run = remember(celebrate) { u.state.streakEndingAt(today).coerceAtMost(todayIndex + 1) }
    val stagger = if (run > 0) minOf(55f, 1400f / run) else 55f
    LaunchedEffect(celebrate) {
        if (celebrate == 0 || reduce || run == 0) return@LaunchedEffect
        delay(120)
        party.snapTo(0f)
        party.animateTo(run * stagger + 360f, tween((run * stagger + 360f).toInt(), easing = LinearEasing))
        party.snapTo(0f)
    }
    val measurer = rememberTextMeasurer()
    val monthStyle = Type.caption.copy(color = pal.text2, fontWeight = FontWeight.Medium)
    val months = remember(start, monthStyle) {
        Cal.monthLabels(start, YEAR_WEEKS).map { (col, label) -> col to measurer.measure(label, monthStyle) }
    }
    val scroll = rememberScrollState()
    val nudge = with(LocalDensity.current) { (STEP * 4).toPx() }
    LaunchedEffect(scroll.maxValue) {
        // Abre quatro semanas antes e desliza até hoje com mola, junto da varredura da abertura.
        if (reduce || Entrance.done || scroll.maxValue == 0) scroll.scrollTo(scroll.maxValue)
        else { scroll.scrollTo((scroll.maxValue - nudge).toInt().coerceAtLeast(0)); scroll.animateScrollTo(scroll.maxValue, Motion.spatial) }
    }
    // Período de um ano anterior: o mapa desliza até o começo, onde esse ano está.
    val firstPeriod = remember { mutableStateOf(true) }
    LaunchedEffect(period) {
        if (firstPeriod.value) { firstPeriod.value = false; return@LaunchedEffect }
        val target = if (period != 0 && period < today.year) 0 else scroll.maxValue
        if (reduce) scroll.scrollTo(target) else scroll.animateScrollTo(target, Motion.spatial)
    }
    val tap by rememberUpdatedState(onTap)
    val openMonth by rememberUpdatedState(onMonth)

    val todayMark = u.todayMark
    val todayStar = today in u.milestones
    val starBirth = remember { Animatable(1f) }
    val hadStar = remember { mutableStateOf(todayStar) }
    LaunchedEffect(todayStar) {
        val was = hadStar.value
        hadStar.value = todayStar
        if (todayStar && !was && !reduce) { starBirth.snapTo(0f); delay(200); starBirth.animateTo(1f, androidx.compose.animation.core.spring(0.55f, 260f)) }
    }
    val todayColor by animateColorAsState(pal.levels[u.level(today)], motion(Motion.slow), label = "today")
    // Com um dia anotado, os meses recuam para o rótulo da anotação ocupar o eixo sozinho.
    val monthsTarget = if (selected != null) Alpha.faded + (1f - Alpha.faded) * back else 1f
    val monthsAlpha by animateFloatAsState(monthsTarget, motion(Motion.quick), label = "months")

    // Abertura: uma máscara diagonal varre a grade a partir de hoje (canto direito) para o passado.
    val reveal = remember { Animatable(if (reduce || Entrance.done) 1f else 0f) }
    val revealed = LocalRevealed.current
    LaunchedEffect(revealed) {
        if (revealed && reveal.value < 1f) { delay(140); reveal.animateTo(1f, tween(Motion.reveal, easing = LinearEasing)) }
    }
    // Ao marcar hoje, a célula solta um halo que se dissipa (só na mudança, não ao abrir o app).
    val halo = remember { Animatable(0f) }
    val previous = remember { mutableStateOf(todayMark) }
    LaunchedEffect(todayMark) {
        val wasDone = previous.value == Mark.DONE
        previous.value = todayMark
        if (todayMark == Mark.DONE && !wasDone && !reduce) { halo.snapTo(1f); halo.animateTo(0f, tween(Motion.halo, easing = FastOutSlowInEasing)) }
    }
    // A linha da anotação desce do rótulo até o dia.
    val drawLine = remember(selected) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(selected) { if (selected != null && !reduce) drawLine.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }

    val gridW = STEP * YEAR_WEEKS + 4.dp // folga para o anel do marco na última coluna
    val gridH = MONTH_H + STEP * 7 - GAP
    val sel = selected?.takeIf { !it.isBefore(start) && !it.isAfter(today) }
    val summary = remember(u) {
        val ms = u.milestones.size
        "Mapa do ano: ${u.year} dias feitos, sequência de ${u.streak}" + (if (ms > 0) ", $ms ${if (ms == 1) "marco" else "marcos"}" else "") +
            (if (u.state.tropes.isNotEmpty()) ", ${u.state.tropes.size} tropeços" else "")
    }
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        Column(Modifier.width(LABEL_W).padding(top = MONTH_H)) {
            for (row in 0 until 7) {
                Box(Modifier.height(STEP), contentAlignment = Alignment.CenterStart) {
                    Cal.WEEKDAYS[row]?.let { Text(it, style = Type.caption.copy(fontSize = 10.sp, lineHeight = 12.sp), color = pal.text3) }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            Box(Modifier.horizontalScroll(scroll)) {
                // Eixo dos meses com marcação: camada própria, recua enquanto há anotação.
                Canvas(Modifier.size(gridW, MONTH_H).graphicsLayer { alpha = monthsAlpha }) {
                    val step = STEP.toPx()
                    for ((col, layout) in months) {
                        drawText(layout, topLeft = Offset(col * step, 2.dp.toPx()))
                        drawRect(pal.line, Offset(col * step, 21.dp.toPx()), Size(1.dp.toPx(), 9.dp.toPx()))
                    }
                }
                // Camada 1: grade estática em cache; na abertura só a máscara por cima é refeita.
                val fx = LocalFx.current
                val dens = LocalDensity.current
                Box(
                    Modifier
                        .size(gridW, gridH)
                        .onGloballyPositioned {
                            with(dens) {
                                fx.heatOrigin = it.positionInRoot() + Offset(0f, MONTH_H.toPx())
                                fx.heatStep = STEP.toPx(); fx.heatCell = CELL.toPx(); fx.heatStart = start
                            }
                        }
                        .graphicsLayer { compositingStrategy = if (reveal.value < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
                        .drawWithContent {
                            drawContent()
                            val t = reveal.value
                            if (t < 1f) {
                                val soft = 9 * STEP.toPx()
                                val dir = Offset(-1f, 1f) / sqrt(2f)
                                val front = t * ((size.width + size.height) / sqrt(2f) + soft)
                                val origin = Offset(size.width, 0f)
                                drawRect(
                                    Brush.linearGradient(0f to Color.Black, 1f to Color.Transparent, start = origin + dir * (front - soft), end = origin + dir * front),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                        val cell = CELL.toPx(); val step = STEP.toPx(); val top = MONTH_H.toPx()
                        val radius = Radius.cell.toPx()
                        for (i in cells.colors.indices) {
                            val c = cells.colors[i] ?: continue
                            if (i in fadingSet) continue // em transição: desenhado na camada viva
                            val tl = Offset((i / 7) * step, top + (i % 7) * step)
                            // Fora do período escolhido na História, o dia recua para quase nada.
                            if (period != 0 && start.plusDays(i.toLong()).year != period) {
                                drawRoundRect(c.copy(alpha = 0.12f), tl, Size(cell, cell), CornerRadius(radius))
                                continue
                            }
                            if (cells.milestone[i]) continue // o asterisco gira na camada viva
                            habitCell(
                                pal, tl, cell, radius, c,
                                if (cells.missed[i]) Mark.MISSED else null, isToday = false, selected = false, trope = cells.trope[i],
                            )
                            if (cells.rest[i]) restDash(pal, tl, cell)
                        }
                    }
                }
                // Camada 2: viva e pequena. Hoje, halo, seleção e a linha da anotação; os toques chegam aqui.
                Canvas(
                    Modifier
                        .size(gridW, gridH)
                        .pointerInput(start, today) {
                            detectTapGestures { o ->
                                val col = (o.x / STEP.toPx()).toInt()
                                val row = ((o.y - MONTH_H.toPx()) / STEP.toPx()).toInt()
                                if (o.y < MONTH_H.toPx() && col in 0 until YEAR_WEEKS) {
                                    // Faixa dos meses: abre o mês que domina a semana tocada.
                                    val d = Cal.dateAt(start, col, 6).let { if (it.isAfter(today)) today else it }
                                    openMonth(YearMonth.from(d))
                                } else if (col in 0 until YEAR_WEEKS && row in 0..6) {
                                    val d = Cal.dateAt(start, col, row)
                                    if (!d.isAfter(today)) tap(d)
                                }
                            }
                        },
                ) {
                    val cell = CELL.toPx(); val step = STEP.toPx(); val top = MONTH_H.toPx()
                    val radius = Radius.cell.toPx()
                    fun at(i: Int) = Offset((i / 7) * step, top + (i % 7) * step)
                    // Crossfade dos dias que mudaram.
                    fading?.let { (old, idx) ->
                        val t = fade.value
                        for (i in idx) {
                            val from = old.colors[i] ?: pal.levels[0]
                            val to = cells.colors[i] ?: continue
                            if (cells.milestone[i]) continue // o asterisco faz o próprio crossfade
                            val tl = at(i)
                            drawRoundRect(androidx.compose.ui.graphics.lerp(from, to, t), tl, Size(cell, cell), CornerRadius(radius))
                            val dotA = (if (old.missed[i]) 1f - t else 0f) + (if (cells.missed[i]) t else 0f)
                            if (dotA > 0f) drawCircle(pal.miss.copy(alpha = Alpha.marker * dotA), minOf(cell * Motion.MISSED_DOT, 3.dp.toPx()), tl + Offset(cell / 2, cell / 2))
                            if (cells.trope[i]) tropeMark(pal, tl, cell)
                        }
                    }
                    // Costuras das quebras com volta; as novas se desenham.
                    for (i in cells.scars) {
                        if (period != 0 && start.plusDays(i.toLong()).year != period) continue
                        stitch(pal, at(i), cell, cell, step - cell, vertical = true, t = (if (i in fresh) sew.value else 1f) * reveal.value)
                    }
                    // Asteriscos dos marcos, girando de vez em quando.
                    stars.forEachIndexed { k, i ->
                        val dim = period != 0 && start.plusDays(i.toLong()).year != period
                        val inFade = i in fadingSet
                        val a = (if (dim) 0.12f else 1f) * reveal.value * (if (inFade) fade.value else 1f)
                        if (inFade) drawRoundRect(pal.levels[0].copy(alpha = 1f - fade.value), at(i), Size(cell, cell), CornerRadius(radius))
                        starCell(pal, at(i), cell, turn = starAngle(k), alpha = a)
                    }
                    // Celebração: cada dia da sequência cresce 40% e volta, em fila até hoje.
                    if (party.value > 0f) {
                        for (k in 0 until run) {
                            val i = todayIndex - run + 1 + k
                            if (i < 0 || i == todayIndex) continue
                            val local = ((party.value - k * stagger) / 360f).coerceIn(0f, 1f)
                            if (local <= 0f || local >= 1f) continue
                            val bump = kotlin.math.sin(local * Math.PI).toFloat() * 0.4f
                            val grow = cell * bump / 2
                            val c = cells.colors[i] ?: continue
                            if (cells.milestone[i]) { starCell(pal, at(i), cell, scale = 1 + bump); continue }
                            drawRoundRect(c, at(i) - Offset(grow, grow), Size(cell + 2 * grow, cell + 2 * grow), CornerRadius(radius * (1 + bump)))
                        }
                    }
                    val todayTl = at(todayIndex)
                    if (todayStar) starCell(pal, todayTl, cell, turn = (1f - starBirth.value) * -120f, scale = starBirth.value.coerceAtLeast(0.01f))
                    else habitCell(pal, todayTl, cell, radius, todayColor, todayMark, isToday = true, selected = false, call = u.state.pendingTrope)
                    if (sel == today) { val o = 2.dp.toPx(); drawRoundRect(pal.ink, todayTl - Offset(o, o), Size(cell + 2 * o, cell + 2 * o), CornerRadius(radius + o), style = Stroke(2.dp.toPx())) }
                    if (halo.value > 0f) {
                        val grow = (1f - halo.value) * cell * 0.6f
                        drawRoundRect(
                            pal.levels[4].copy(alpha = halo.value * 0.7f), todayTl - Offset(grow / 2, grow / 2), Size(cell + grow, cell + grow),
                            CornerRadius(radius + grow / 3), style = Stroke(2.dp.toPx()),
                        )
                    }
                    if (sel != null) {
                        val tl = at(ChronoUnit.DAYS.between(start, sel).toInt())
                        if (sel != today) {
                            val o = 2.dp.toPx()
                            drawRoundRect(pal.ink, tl - Offset(o, o), Size(cell + 2 * o, cell + 2 * o), CornerRadius(radius + o), style = Stroke(2.dp.toPx()))
                        }
                        val x = tl.x + cell / 2
                        val y0 = 20.dp.toPx()
                        val y1 = tl.y - 4.dp.toPx()
                        drawRect(pal.ink.copy(alpha = 1f - back), Offset(x - 0.5.dp.toPx(), y0), Size(1.dp.toPx(), (y1 - y0) * drawLine.value))
                    }
                }
                // Rótulo da anotação, preso ao eixo dos meses; perto do fim do ano vira para a esquerda.
                if (sel != null) {
                    val i = ChronoUnit.DAYS.between(start, sel).toInt()
                    val state = dayState(u, sel).replaceFirstChar { it.uppercase() }
                    val ms = u.milestones[sel]
                    val text = "$state · ${if (sel == today) "hoje" else Cal.shortDate(sel)}" + (if (ms != null) " · marco de $ms" else "")
                    val density = LocalDensity.current
                    val cx = with(density) { (STEP * (i / 7) + CELL / 2).toPx() }
                    val gw = with(density) { gridW.toPx() }
                    Text(
                        text, style = Type.caption, color = pal.bg, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        modifier = Modifier
                            .layout { m, _ ->
                                val p = m.measure(Constraints())
                                layout(p.width, p.height) {
                                    val x = if (cx + p.width > gw) cx - p.width + 1f else cx - 1f
                                    p.place(x.roundToInt(), 0)
                                }
                            }
                            // Entra descendo 3 dp e aparecendo, antes da linha terminar de descer.
                            .graphicsLayer {
                                val t = (drawLine.value * 1.4f).coerceAtMost(1f)
                                alpha = (1f - back) * t
                                translationY = (1f - t) * -3.dp.toPx()
                            }
                            .clip(RoundedCornerShape(1.dp))
                            .background(pal.ink)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            // Borda esquerda esmaecida: um degradê da cor do bloco, sem camada extra na área que rola.
            Box(Modifier.align(Alignment.CenterStart).size(FADE_W, gridH).background(Brush.horizontalGradient(listOf(pal.surface, pal.surface.copy(alpha = 0f)))))
        }
    }
}

/** Legenda: "não fiz", marco e o que cada tom significa (dias seguidos). Com um dia anotado, vira a dica do 2º toque. */
@Composable
fun Legend(hint: Boolean) {
    val pal = Tokens
    Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hint) {
            Spacer(Modifier.weight(1f))
        } else {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(5.dp)) { drawCircle(pal.miss) }
                Text(" não fiz", style = Type.caption, color = pal.text2)
                Spacer(Modifier.width(Space.m))
                Canvas(Modifier.size(10.dp)) { star(pal.levels[4], center, size.width, glyph = pal.glyph) }
                Text(" marco", style = Type.caption, color = pal.text2)
            }
        }
        listOf(1 to "1", 2 to "2–3", 3 to "4–6", 4 to "7+").forEach { (l, t) ->
            Spacer(Modifier.width(Space.s))
            Canvas(Modifier.size(10.dp)) { drawRoundRect(pal.levels[l], cornerRadius = CornerRadius(Radius.legend.toPx())) }
            Text(" $t", style = Type.caption, color = pal.text2)
        }
    }
}
