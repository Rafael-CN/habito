package com.rafael.habito

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlin.random.Random

/** "Remover animações" do sistema ligado: tudo muda direto, sem transição. */
val LocalReduceMotion = compositionLocalOf { false }

/** Vira true quando a tela de abertura começa a sair: é o sinal para a entrada da tela. */
val LocalRevealed = compositionLocalOf { true }

/** Transição curta, ou troca imediata com animações desligadas. */
@Composable
@ReadOnlyComposable
fun <T> motion(ms: Int): AnimationSpec<T> = if (LocalReduceMotion.current) snap() else tween(ms)

/** A entrada (blocos em fila, grade acendendo) acontece só uma vez por abertura do app, não ao voltar do mês. */
object Entrance {
    @Volatile var done = false
}

/**
 * Entrada de um bloco da tela: surge e sobe 10 dp, em fila pelo índice (45 ms entre blocos).
 * Só na abertura; com animações desligadas o bloco já nasce no lugar.
 */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val reduce = LocalReduceMotion.current
    val revealed = LocalRevealed.current
    val p = remember { Animatable(if (reduce || Entrance.done) 1f else 0f) }
    LaunchedEffect(revealed, reduce) {
        if (reduce) p.snapTo(1f)
        else if (revealed && p.value < 1f) { delay(index * 45L); p.animateTo(1f, Motion.enter); Entrance.done = true }
    }
    return this.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 10.dp.toPx() }
}

/**
 * Número cujos dígitos rolam ao mudar: sobem quando o valor cresce, descem quando diminui.
 * Cada dígito é animado só se mudou, então "11 → 12" mexe apenas a unidade.
 */
@Composable
fun RollingNumber(value: String, color: Color, style: TextStyle) {
    if (LocalReduceMotion.current) { Text(value, color = color, style = style); return }
    val number = value.filter(Char::isDigit).toIntOrNull() ?: 0
    val last = remember { intArrayOf(number) }
    val up = number >= last[0]
    SideEffect { last[0] = number }
    Row {
        value.forEachIndexed { i, ch ->
            key(value.length - i) { // chave contada da direita: a unidade é sempre a mesma posição
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val dir = if (up) 1 else -1
                        (slideInVertically(tween(280)) { it * dir } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(280)) { -it * dir } + fadeOut(tween(180))) using SizeTransform(clip = true)
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), color = color, style = style) }
            }
        }
    }
}

// ---------- Transição compartilhada: o card do ano vira a tela do mês ----------

@OptIn(ExperimentalSharedTransitionApi::class)
class SharedScopes(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

/** Escopos da transição atual; nulo fora dela (o modificador vira no-op). */
val LocalShared = compositionLocalOf<SharedScopes?> { null }

private const val SHARED_YEAR = "ano"

/** Marca o card do ano e a tela do mês como o mesmo objeto: um cresce e vira o outro. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedYear(): Modifier {
    val s = LocalShared.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(s.transition) {
        this@sharedYear.sharedBounds(
            rememberSharedContentState(SHARED_YEAR), s.visibility,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow) },
        )
    }
}
