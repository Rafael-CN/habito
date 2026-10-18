package com.rafael.habito

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Régua de seção: 1 dp de tinta que se desenha da esquerda na abertura. */
@Composable
fun Rule(modifier: Modifier = Modifier, index: Int = 0) {
    val reduce = LocalReduceMotion.current
    val revealed = LocalRevealed.current
    val p = remember { Animatable(if (reduce || Entrance.done) 1f else 0f) }
    LaunchedEffect(revealed) {
        if (revealed && p.value < 1f) { delay(index * 60L); p.animateTo(1f, tween(560, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    }
    val ink = Tokens.ink
    Box(
        modifier.fillMaxWidth().height(1.dp)
            .graphicsLayer { scaleX = p.value; transformOrigin = TransformOrigin(0f, 0.5f) }
            .background(ink),
    )
}

/** Régua fina entre linhas. */
@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Tokens.rule))

/** Rótulo em caixa alta, espaçado. */
@Composable
fun Caps(text: String, color: Color = Tokens.ink, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = Type.caps, color = color, modifier = modifier)

/** Cabeçalho de seção aberta: título à esquerda, nota à direita. */
@Composable
fun SectionHead(title: String, meta: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        Caps(title, modifier = Modifier.weight(1f))
        if (meta != null) Text(meta, style = Type.caption, color = Tokens.text3)
    }
}

/** Bloco por tom, sem borda, encostado na régua: só onde se toca em células (Hoje, Ano). */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Radius.panel)).background(Tokens.surface).padding(padding),
        content = content,
    )
}

/**
 * Seletor em trilho: o trilho inteiro é um objeto e o marcador desliza. As duas bordas animam com molas
 * diferentes, então ele estica na direção do toque e assenta com um quique curto. O escolhido ganha uma régua de tinta.
 */
@Composable
fun Track(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pal = Tokens
    val reduce = LocalReduceMotion.current
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(Radius.control)).background(pal.track).padding(3.dp),
    ) {
        val seg = with(LocalDensity.current) { maxWidth.toPx() } / options.size
        val left = remember { Animatable(selected * seg) }
        val right = remember { Animatable((selected + 1) * seg) }
        val first = remember { mutableStateOf(true) }
        LaunchedEffect(selected, seg) {
            val tl = selected * seg
            val tr = (selected + 1) * seg
            if (first.value || reduce) { left.snapTo(tl); right.snapTo(tr); first.value = false; return@LaunchedEffect }
            val toRight = tl > left.value
            launch { left.animateTo(tl, if (toRight) Motion.trail else Motion.lead) }
            launch { right.animateTo(tr, if (toRight) Motion.lead else Motion.trail) }
        }
        val press = remember { MutableInteractionSource() }
        val pressed by press.collectIsPressedAsState()
        val squash by animateFloatAsState(if (pressed && !reduce) 0.95f else 1f, Motion.press, label = "squash")
        // Marcador: lido só na fase de layout, sem recompor a cada quadro.
        Box(
            Modifier
                .layout { m, c ->
                    val w = (right.value - left.value).roundToInt().coerceAtLeast(0)
                    val pl = m.measure(Constraints.fixed(w, c.maxHeight))
                    layout(c.maxWidth, c.maxHeight) { pl.place(left.value.roundToInt(), 0) }
                }
                .scale(squash)
                .clip(RoundedCornerShape(Radius.thumb))
                .background(pal.thumb)
                .border(1.dp, pal.thumbEdge, RoundedCornerShape(Radius.thumb))
                .drawBehind { drawRect(pal.ink, Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) },
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val color by animateColorAsState(if (on) pal.ink else pal.text2, motion(Motion.quick), label = "opt")
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .semantics { role = Role.RadioButton; this.selected = on }
                        .clickable(interactionSource = if (on) press else remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Type.bodyL, color = color, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Texto que troca rolando, como os números: sobe o novo, sai o antigo. */
@Composable
fun RollingLabel(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        text, modifier = modifier,
        transitionSpec = {
            if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (slideInVertically(spring(0.85f, 500f)) { it } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(200)) { -it } + fadeOut(tween(160)))
        },
        label = "rolling",
    ) { t -> Text(t, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/**
 * Linha de ficha técnica: rótulo à esquerda, valor sempre à vista à direita. Tocar abre o controle logo abaixo,
 * com a mesma mola do trilho. Sem [content], a linha é um atalho (seta).
 */
@Composable
fun SpecRow(
    label: String, value: String, open: Boolean, onClick: () -> Unit,
    valueColor: Color = Tokens.ink, content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val pal = Tokens
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.press, label = "chev")
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    // Linha de atalho: a seta dá um passo na direção em que vai levar.
    val nudge by animateFloatAsState(if (pressed && content == null && !LocalReduceMotion.current) 3f else 0f, Motion.press, label = "nudge")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).clickable(press, indication = null, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Caps(label, color = pal.text2, modifier = Modifier.weight(1f))
            RollingLabel(value, Type.value, valueColor, Modifier.padding(start = Space.m))
            Spacer(Modifier.width(Space.m))
            Icon(
                painterResource(if (content != null) R.drawable.ic_chevron_down else R.drawable.ic_arrow_out), null,
                Modifier.size(16.dp).graphicsLayer { translationX = nudge.dp.toPx(); translationY = -nudge.dp.toPx() }.rotate(if (content != null) turn else 0f),
                tint = pal.text3,
            )
        }
        if (content != null) {
            AnimatedVisibility(
                open,
                enter = expandVertically(spring(0.82f, 420f)) + fadeIn(tween(200, 60)),
                exit = shrinkVertically(spring(1f, 500f)) + fadeOut(tween(120)),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(Space.m), content = content)
            }
        }
        Hairline()
    }
}

/** Um número com a legenda de uma linha embaixo (Números, mês). */
@Composable
fun Figure(value: String, label: String, style: TextStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        RollingNumber(value, Tokens.ink, style)
        Text(label, style = Type.body, color = Tokens.text2, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Botão de contorno, reto: ação secundária. */
@Composable
fun OutlineButton(label: String, icon: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pal = Tokens
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 0.97f else 1f, Motion.press, label = "press")
    val bg by animateColorAsState(if (pressed) pal.surface else pal.surface.copy(alpha = 0f), motion(200), label = "pressBg")
    Row(
        modifier.height(48.dp).scale(scale).clip(RoundedCornerShape(Radius.control))
            .background(bg)
            .border(1.dp, pal.line, RoundedCornerShape(Radius.control))
            .clickable(press, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(painterResource(icon), null, Modifier.size(18.dp), tint = pal.ink); Spacer(Modifier.width(Space.s)) }
        Text(label, style = Type.bodyL, color = pal.ink, fontWeight = FontWeight.Medium)
    }
}

/** Barra de desfazer: invertida, reta, com uma linha que se esvazia mostrando o tempo que resta. */
@Composable
fun UndoBar(message: String, action: String?, onAction: () -> Unit) {
    val pal = Tokens
    val life = remember(message) { Animatable(1f) }
    LaunchedEffect(message) { life.animateTo(0f, tween(Motion.undo, easing = androidx.compose.animation.core.LinearEasing)) }
    // Entra subindo 14 dp, como no protótipo.
    val reduce = LocalReduceMotion.current
    val enter = remember(message) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(message) { enter.animateTo(1f, tween(340, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) }
    // "Hoje · feito": o dia em destaque, o estado em tom secundário.
    val parts = message.split(" · ", limit = 2)
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append(parts[0])
        if (parts.size > 1) {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = pal.onInk2, fontWeight = FontWeight.Normal)); append(" · ${parts[1]}"); pop()
        }
    }
    Box(
        Modifier.fillMaxWidth()
            .graphicsLayer { translationY = (1f - enter.value) * 14.dp.toPx(); alpha = (enter.value * 2f).coerceAtMost(1f) }
            .clip(RoundedCornerShape(Radius.control)).background(pal.ink),
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = Space.l, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = Type.bodyL, color = pal.bg, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (action != null) {
                Text(
                    action, style = Type.bodyL.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    color = pal.bg, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(Radius.control)).clickable(onClick = onAction).padding(horizontal = Space.m, vertical = 14.dp),
                )
            }
        }
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp)
                .graphicsLayer { scaleX = life.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                .background(pal.bg.copy(alpha = 0.6f)),
        )
    }
}
