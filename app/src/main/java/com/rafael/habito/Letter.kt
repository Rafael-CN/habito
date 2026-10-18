package com.rafael.habito

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LETTER_DATE = DateTimeFormatter.ofPattern("d MMM", Locale("pt", "BR"))
private val LETTER = Type.title.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)

/** Botão cheio de tinta, largura toda. */
@Composable
private fun InkButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val pal = Tokens
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(Radius.control))
            .background(pal.ink.copy(alpha = if (enabled) 1f else 0.3f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.bodyL, color = pal.bg, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun QuietButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(Radius.control)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.body, color = Tokens.text2) }
}

/** Campo de uma frase: régua de tinta em cima, fina embaixo, sem caixa. */
@Composable
internal fun LetterField(text: String, onChange: (String) -> Unit, placeholder: String, focus: Boolean = true, minHeight: Int = 140) {
    val pal = Tokens
    val req = remember { FocusRequester() }
    if (focus) LaunchedEffect(Unit) { delay(250); runCatching { req.requestFocus() } }
    Box(
        Modifier.fillMaxWidth().heightIn(min = minHeight.dp)
            .drawBehind {
                drawRect(pal.ink, Offset.Zero, Size(size.width, 1.dp.toPx()))
                drawRect(pal.rule, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
            }
            .padding(vertical = 14.dp),
    ) {
        if (text.isEmpty()) Text(placeholder, style = LETTER, color = pal.text3.copy(alpha = 0.6f))
        BasicTextField(
            text, { onChange(it.take(140)) },
            textStyle = LETTER.copy(color = pal.ink), cursorBrush = SolidColor(pal.ink),
            modifier = Modifier.fillMaxWidth().focusRequester(req),
        )
    }
}

private val FIRST_SUGGESTIONS = listOf("Ler 20 páginas", "Treinar", "Meditar 10 min", "Escrever", "Estudar 30 min")
private val FIRST_GOALS = listOf(0, 3, 4, 5, 6)

/**
 * Dia 1 em três passos calmos: qual hábito, com que frequência e por quê (a frase lacrada até o dia 30).
 * Uma régua de três traços no topo mostra onde se está; voltar recua um passo.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FirstUse(onDone: (name: String, goal: Int, letter: String?) -> Unit) {
    val pal = Tokens
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = step > 0) { step-- }
    Column(
        Modifier.fillMaxSize().background(pal.bg).clickable(remember { MutableInteractionSource() }, null) {}
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = Space.gutter).padding(top = 24.dp, bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            for (i in 0..2) {
                val c by androidx.compose.animation.animateColorAsState(if (i <= step) pal.ink else pal.rule, tween(Motion.base), label = "step")
                Box(Modifier.weight(1f).height(2.dp).background(c))
            }
        }
        androidx.compose.animation.AnimatedContent(
            step, label = "first",
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (androidx.compose.animation.slideInHorizontally(tween(Motion.base)) { it / 6 * dir } + fadeIn(tween(Motion.base))) togetherWith
                    (androidx.compose.animation.slideOutHorizontally(tween(Motion.quick)) { -it / 6 * dir } + androidx.compose.animation.fadeOut(tween(Motion.quick)))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { s ->
            Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
                Text("DIA 1", style = Type.caps, color = pal.text2)
                val title = when (s) { 0 -> "Qual hábito?"; 1 -> "Com que frequência?"; else -> "Por que este hábito?" }
                Text(title, style = Type.monthTitle.copy(fontSize = 52.sp, lineHeight = 52.sp), color = pal.ink, modifier = Modifier.padding(top = 14.dp))
                Spacer(Modifier.height(28.dp))
                when (s) {
                    0 -> {
                        LetterField(name, { name = it.replace("\n", "").take(28) }, "Ler 20 páginas", minHeight = 64)
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.padding(top = 18.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        ) { FIRST_SUGGESTIONS.forEach { sug -> Chip(sug, name == sug) { name = sug } } }
                    }
                    1 -> androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) { FIRST_GOALS.forEach { g -> Chip(if (g == 0) "todo dia" else "$g× por semana", goal == g) { goal = g } } }
                    else -> LetterField(text, { text = it }, "Quero chegar em março lendo sem esforço.")
                }
            }
        }
        when (step) {
            0 -> InkButton("Seguir", enabled = name.isNotBlank()) { step = 1 }
            1 -> InkButton("Seguir") { step = 2 }
            else -> InkButton(if (text.isNotBlank()) "Guardar para o dia 30" else "Começar") { onDone(name.trim(), goal, text.trim().ifEmpty { null }) }
        }
        Spacer(Modifier.height(8.dp))
        if (step == 2 && text.isNotBlank()) QuietButton("Começar sem frase") { onDone(name.trim(), goal, null) }
        else Spacer(Modifier.height(44.dp))
    }
}

/** Lacre no Hoje: um cadeado dourado e o dia em que a frase abre. */
@Composable
internal fun SealChip(forDay: Int) {
    val pal = Tokens
    Row(
        Modifier.border(1.dp, pal.rule, RoundedCornerShape(Radius.control)).padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(13.dp)) {
            val w = 1.5.dp.toPx()
            val body = androidx.compose.ui.geometry.Rect(size.width * 0.18f, size.height * 0.45f, size.width * 0.82f, size.height * 0.95f)
            drawRoundRect(pal.gold, body.topLeft, body.size, androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            // Arco fechado: as duas pernas descem até o corpo (cadeado trancado).
            val l = size.width * 0.32f; val r = size.width * 0.68f; val top = size.height * 0.08f; val mid = size.height * 0.28f
            drawArc(pal.gold, 180f, 180f, false, Offset(l, top), Size(r - l, (mid - top) * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            drawLine(pal.gold, Offset(l, mid), Offset(l, body.top), w)
            drawLine(pal.gold, Offset(r, mid), Offset(r, body.top), w)
        }
        Spacer(Modifier.width(5.dp))
        Text("dia $forDay", style = Type.caption, color = pal.text2, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * O marco abre a frase: o asterisco se desenha braço a braço e gira uma vez, o número, a data,
 * e a frase do dia 1 entra devagar. No dia 30 dá para lacrar outra para o dia 100.
 */
@Composable
fun LetterReveal(letter: Letter, day: java.time.LocalDate, onClose: (next: String?) -> Unit) {
    val pal = Tokens
    val reduce = LocalReduceMotion.current
    val arms = remember { List(3) { Animatable(if (reduce) 1f else 0f) } }
    val turn = remember { Animatable(0f) }
    val quote = remember { Animatable(if (reduce) 1f else 0f) }
    var writing by rememberSaveable { mutableStateOf(false) }
    var next by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        arms.forEachIndexed { k, a -> launch { delay(150L + k * 150L); a.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) } }
        launch { delay(900); turn.animateTo(60f, tween(1400, easing = androidx.compose.animation.core.CubicBezierEasing(0.6f, 0f, 0.2f, 1f))) }
        delay(1500)
        quote.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    BackHandler { onClose(null) }
    Column(
        Modifier.fillMaxSize().background(pal.bg).clickable(remember { MutableInteractionSource() }, null) {}
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = Space.gutter).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // O alto rola (com o teclado aberto, o campo do dia 100 sobe); os botões ficam presos embaixo.
      val scroll = androidx.compose.foundation.rememberScrollState()
      LaunchedEffect(writing) { if (writing) { delay(350); scroll.animateScrollTo(scroll.maxValue) } }
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(top = 56.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(92.dp).graphicsLayer { rotationZ = turn.value }) {
            val len = size.width
            val w = len * 0.15f
            val half = (len - w) / 2
            for (k in 0 until 3) {
                val a = Math.toRadians(k * 60.0 - 90.0)
                val d = Offset(kotlin.math.cos(a).toFloat() * half, kotlin.math.sin(a).toFloat() * half)
                val from = center - d
                drawLine(pal.levels[4], from, from + d * 2f * arms[k].value, strokeWidth = w, cap = StrokeCap.Round)
            }
        }
        Text("${letter.forDay}", style = Type.hero.copy(fontSize = 96.sp, lineHeight = 90.sp), color = pal.ink, modifier = Modifier.padding(top = 26.dp))
        Text(day.format(LETTER_DATE).replace(".", ""), style = Type.body, color = pal.text3, modifier = Modifier.padding(top = 14.dp))
        Column(
            Modifier.fillMaxWidth().padding(top = 40.dp).graphicsLayer { alpha = quote.value; translationY = (1f - quote.value) * 16.dp.toPx() },
        ) {
            Text("DIA 1 · ${letter.written.format(LETTER_DATE).replace(".", "")}".uppercase(), style = Type.caps, color = pal.text2)
            Row(Modifier.padding(top = 10.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(pal.gold))
                Text("“${letter.text}”", style = Type.monthTitle.copy(fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.3).sp), color = pal.ink, modifier = Modifier.padding(start = 14.dp))
            }
        }
        AnimatedVisibility(writing, enter = fadeIn() + expandVertically()) {
            Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
                Text("DIA 100", style = Type.caps, color = pal.text2, modifier = Modifier.padding(bottom = 8.dp))
                LetterField(next, { next = it }, "…", minHeight = 84)
            }
        }
      }
        InkButton(if (writing) "Guardar" else "Seguir") { onClose(next.takeIf { writing && it.isNotBlank() }) }
        Spacer(Modifier.height(8.dp))
        if (!writing && letter.forDay < 100) QuietButton("+ Dia 100") { writing = true } else Spacer(Modifier.height(44.dp))
    }
}
