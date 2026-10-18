package com.rafael.habito

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT = Locale("pt", "BR")
private val MONTH_NAME = DateTimeFormatter.ofPattern("MMMM", PT)
private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT)

/** Números do mês: feitos até hoje, dias possíveis e a maior sequência dentro do mês. */
private class MonthStats(val done: Int, val possible: Int, val longest: Int)

private fun monthStats(u: HabitUi, m: YearMonth): MonthStats {
    var done = 0; var possible = 0; var run = 0; var longest = 0
    for (day in 1..m.lengthOfMonth()) {
        val d = m.atDay(day)
        if (d.isAfter(u.today)) break
        possible++
        if (u.markOf(d) == Mark.DONE) { done++; run++; if (run > longest) longest = run } else run = 0
    }
    return MonthStats(done, possible, longest)
}

/**
 * O mês ampliado: nome grande em condensada, régua, três números e um calendário de verdade no mesmo
 * vocabulário do ano. Tocar num dia o lê; tocar de novo muda. Abre recortado a partir do bloco do ano
 * (quem recorta é a página); aqui o corpo sobe e aparece, e some primeiro ao fechar ([closing]).
 * Trocar de mês: o nome rola na vertical, o calendário desliza, os números rolam no lugar.
 */
@Composable
fun MonthScreen(
    u: HabitUi, month: YearMonth, back: Float, closing: Boolean = false,
    onMonth: (YearMonth) -> Unit, onClose: () -> Unit, onDay: (LocalDate) -> Unit, onRetro: (YearMonth) -> Unit = {},
) {
    val pal = Tokens
    val first = YearMonth.from(u.start)
    val last = YearMonth.from(u.today)
    val reduce = LocalReduceMotion.current
    val ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    var picked by remember(month) { mutableStateOf<LocalDate?>(null) }
    val view = LocalView.current
    // Entrada: a barra aparece depois do recorte começar; o corpo sobe 14 dp e aparece.
    val body = remember { Animatable(if (reduce) 1f else 0f) }
    val bar = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        launch { delay(40); body.animateTo(1f, tween(340, easing = ease)) }
        launch { delay(120); bar.animateTo(1f, tween(240)) }
    }
    val out by animateFloatAsState(if (closing) 0f else 1f, tween(if (reduce) 0 else 160), label = "fecha")
    val swipe = with(LocalDensity.current) { 72.dp.toPx() }
    fun go(d: Int) {
        val target = month.plusMonths(d.toLong())
        if (target in first..last) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onMonth(target) }
    }
    Column(
        Modifier
            .fillMaxSize()
            // Durante o gesto de voltar, a tela recua um pouco acompanhando o dedo.
            .graphicsLayer { val s = 1f - 0.06f * back; scaleX = s; scaleY = s }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 10.dp, end = 8.dp).graphicsLayer { alpha = bar.value * out },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.height(48.dp).clip(RoundedCornerShape(Radius.control)).clickable(onClick = onClose).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), "Voltar ao ano", Modifier.size(20.dp), tint = pal.ink)
                Spacer(Modifier.width(Space.s))
                Text("Ano", style = Type.bodyL, color = pal.ink, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            NavIcon(R.drawable.ic_chevron_left, "Mês anterior", enabled = month > first) { go(-1) }
            NavIcon(R.drawable.ic_chevron_right, "Próximo mês", enabled = month < last) { go(1) }
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .graphicsLayer { alpha = body.value * out; translationY = (1f - body.value) * 14.dp.toPx() }
                .padding(horizontal = Space.gutter).padding(bottom = 40.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp).clipToBounds(), verticalAlignment = Alignment.Bottom) {
                AnimatedContent(
                    month,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else (slideInVertically(tween(320, easing = ease)) { it * dir * 2 / 5 } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(200, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))) { -it * dir * 2 / 5 } + fadeOut(tween(160)))
                    },
                    modifier = Modifier.weight(1f), label = "nome",
                ) { m -> Text(m.format(MONTH_NAME).replaceFirstChar { it.uppercase() }, style = Type.monthTitle, color = pal.ink, maxLines = 1) }
                RollingLabel("${month.year}", Type.title.copy(fontWeight = FontWeight.Medium), pal.text3, Modifier.padding(bottom = 4.dp))
            }
            Rule()
            // Os números ficam parados e rolam; só o calendário desliza.
            val st = remember(u, month) { monthStats(u, month) }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Figure("${st.done}", "feitos", Type.stat, Modifier.weight(1f))
                Figure("${if (st.possible == 0) 0 else st.done * 100 / st.possible}%", "do mês", Type.stat, Modifier.weight(1f))
                Figure("${st.longest}", "sequência", Type.stat, Modifier.weight(1f))
            }
            Spacer(Modifier.height(28.dp))
            AnimatedContent(
                month,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (slideInHorizontally(tween(280, easing = ease)) { it * dir * 18 / 100 } + fadeIn(tween(220))) togetherWith
                        fadeOut(tween(120))
                },
                modifier = Modifier.pointerInput(month) {
                    // Deslizar para o lado troca o mês, como as setas.
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = { if (dx < -swipe) go(1) else if (dx > swipe) go(-1) },
                    ) { _, d -> dx += d }
                },
                label = "mes",
            ) { m ->
                MonthGrid(u, m, picked) { d ->
                    if (picked == d) onDay(d)
                    else { picked = d; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                }
            }
            Caption(u, picked)
            if (month < last && hasData(u, month)) {
                Spacer(Modifier.height(Space.xl))
                OutlineButton("Retrospectiva do mês", R.drawable.ic_arrow_out, { onRetro(month) }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Legenda do dia lido: data por extenso, estado e a posição na sequência. */
@Composable
private fun Caption(u: HabitUi, d: LocalDate?) {
    val pal = Tokens
    val text = if (d == null) buildAnnotatedString { }
    else buildAnnotatedString {
        withStyle(SpanStyle(color = pal.ink, fontWeight = FontWeight.SemiBold)) { append(d.format(DAY_LONG).replaceFirstChar { it.uppercase() }) }
        append(" · "); append(dayState(u, d))
        if (u.markOf(d) == Mark.DONE) append(", ${u.state.streakEndingAt(d)}º dia da sequência")
        u.milestones[d]?.let { append(" · marco de $it") }
    }
    Text(text, style = Type.body.copy(fontSize = 12.sp, lineHeight = 16.sp), color = pal.text2, modifier = Modifier.padding(top = Space.l).heightIn(min = 32.dp))
}

@Composable
private fun MonthGrid(u: HabitUi, m: YearMonth, picked: LocalDate?, onDay: (LocalDate) -> Unit) {
    val pal = Tokens
    val offset = Cal.rowOf(m.atDay(1)) // coluna do dia 1 (domingo ou segunda primeiro)
    val days = m.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    Column {
        Row(Modifier.padding(bottom = Space.s), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Cal.INITIALS.forEach { Text(it, color = pal.text3, style = Type.caption, modifier = Modifier.weight(1f)) }
        }
        Hairline()
        Spacer(Modifier.height(Space.s))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            if (day in 1..days) DayCell(u, m.atDay(day), m.atDay(day) == picked, onDay)
                        }
                    }
                }
            }
        }
    }
}

/** Um dia do mês: tom, número no canto, ponto do "não fiz" no canto oposto, anéis de hoje, marco e seleção. */
@Composable
private fun DayCell(u: HabitUi, d: LocalDate, picked: Boolean, onDay: (LocalDate) -> Unit) {
    val pal = Tokens
    val out = d.isAfter(u.today) || d.isBefore(u.start)
    val level = u.level(d)
    val color by animateColorAsState(if (out) pal.bg else pal.levels[level], motion(Motion.slow), label = "dia")
    val number by animateColorAsState(if (out) pal.text3 else pal.levelInk[level], motion(Motion.slow), label = "num")
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 0.92f else 1f, Motion.press, label = "press")
    val mark = if (out) null else u.markOf(d)
    val ring = if (picked) pal.ink else null
    val trope = !out && d in u.state.tropes
    val scar = !out && d in u.state.scars
    val call = !out && d == u.today && mark == null && u.state.pendingTrope
    val breathe = if (call) callBreath() else 0f
    Box(
        Modifier
            .fillMaxSize()
            .scale(scale)
            .then(if (out) Modifier else Modifier.clickable(interactionSource = press, indication = null) { onDay(d) }),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(Radius.cell.toPx())
            if (out) drawRoundRect(pal.rule, cornerRadius = r, style = Stroke(1.dp.toPx()))
            else drawRoundRect(color, cornerRadius = r)
            if (scar) stitch(pal, Offset.Zero, size.width, size.height, 4.dp.toPx(), vertical = false)
            else if (trope) tropeMark(pal, Offset(size.width - 17.dp.toPx(), size.height - 17.dp.toPx()), 13.dp.toPx(), 2.dp.toPx())
            else if (mark == Mark.MISSED) drawCircle(pal.miss, 2.5.dp.toPx(), Offset(size.width - 8.5.dp.toPx(), size.height - 8.5.dp.toPx()))
            else if (!out && u.state.isRest(d) && d != u.today) drawRect(pal.text3.copy(alpha = 0.7f), Offset(size.width - 16.dp.toPx(), size.height - 9.dp.toPx()), Size(8.dp.toPx(), 1.5.dp.toPx()))
            if (!out && d in u.milestones) star(number, Offset(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()), 12.dp.toPx())
            if (call) callRing(pal, Offset.Zero, size.width, size.height, r.x, breathe)
            else if (!out && d == u.today && mark == null) {
                val i = 0.75.dp.toPx()
                drawRoundRect(pal.ink, Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), r, style = Stroke(1.5.dp.toPx()))
            }
            if (ring != null) {
                val o = 2.dp.toPx()
                drawRoundRect(ring, Offset(-o, -o), Size(size.width + 2 * o, size.height + 2 * o), CornerRadius(r.x + o), style = Stroke(if (picked) 1.5.dp.toPx() else 2.dp.toPx()))
            }
        }
        Text(
            "${d.dayOfMonth}", color = number, style = Type.caption.copy(fontSize = 12.sp),
            fontWeight = if (d == u.today) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
    }
}

@Composable
private fun NavIcon(icon: Int, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(Radius.control)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, Modifier.size(20.dp), tint = if (enabled) Tokens.ink else Tokens.ink.copy(alpha = 0.25f))
    }
}
