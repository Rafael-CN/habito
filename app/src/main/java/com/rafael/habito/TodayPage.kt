package com.rafael.habito

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.util.lerp
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val PT = Locale("pt", "BR")
internal val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", PT)
internal val DAY_MONTH = DateTimeFormatter.ofPattern("d 'de' MMMM", PT)
internal val SINCE = DateTimeFormatter.ofPattern("dd.MM.yy")
internal val HHMM = DateTimeFormatter.ofPattern("HH:mm")
internal val MILESTONE_STEPS = MILESTONES.sorted()

/** "dias seguidos", ou "semanas seguidas" com a meta semanal; [sep] separa as duas palavras. */
internal fun unitLabel(u: HabitUi, sep: String) = when {
    // Voltando de uma quebra: conta a volta, não o zero.
    !u.weekly && u.state.inReturn && u.streak == 1 -> "dia${sep}de volta"
    !u.weekly && u.state.inReturn -> "dias${sep}de volta"
    u.weekly && u.streak == 1 -> "semana${sep}seguida"
    u.weekly -> "semanas${sep}seguidas"
    u.streak == 1 -> "dia${sep}seguido"
    else -> "dias${sep}seguidos"
}

/** Uma seção aberta: respiro, régua de tinta e o conteúdo, entrando em sequência. */
@Composable
internal fun Section(index: Int, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = Space.section).entrance(index)) {
        Rule(index = index)
        content()
    }
}

/** A tarefa. O estado de hoje já está no bloco logo abaixo. */
@Composable
internal fun Top(u: HabitUi) {
    Text(u.state.taskName, style = Type.title, color = Tokens.ink, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
}

/** A sequência atual, o único número enorme. Ao rolar, encolhe para o canto e cede lugar à barra compacta. */
@Composable
internal fun Hero(u: HabitUi, scroll: ScrollState, compact: Boolean = false, time: androidx.compose.runtime.State<Long>? = null) {
    val pal = Tokens
    // O número enche até o próximo marco (na meta semanal, até a meta da semana), no material da cor.
    // No dia do marco o número fica cheio; no dia seguinte começa a encher para o próximo.
    val scrubbing = LocalFx.current.scrub >= 1f
    val st = if (scrubbing) streakAt(u, u.today.minusDays(LocalFx.current.scrub.toLong())) else u.streak
    val fill = if (!scrubbing && isMilestone(u.streak, u.weekly) && u.todayMark == Mark.DONE) 1f else if (u.weekly) u.doneThisWeek.toFloat() / u.goal.coerceAtLeast(1) else {
        val next = MILESTONE_STEPS.firstOrNull { it > st } ?: ((st / 365 + 1) * 365)
        val prev = MILESTONE_STEPS.lastOrNull { it <= st && it < next } ?: 0
        (st - prev).toFloat() / (next - prev)
    }
    val level by animateFloatAsState(fill.coerceIn(0f, 1f), if (LocalReduceMotion.current) snap() else spring(0.8f, 120f), label = "fill")
    val petals = remember(pal.glyph) { PetalState() }
    val density = LocalDensity.current
    val span = with(density) { 160.dp.toPx() }
    val reduce = LocalReduceMotion.current
    val fx = LocalFx.current
    // Feito: o número estica a largura e ganha peso ao virar, e volta numa mola.
    val stretch = remember { Animatable(0f) }
    LaunchedEffect(fx.feito) { if (fx.feito > 0 && !reduce) { stretch.animateTo(1f, tween(M.FAST, easing = M.Arrive)); stretch.animateTo(0f, M.wobble()) } }
    // Não fiz: solta o ar (perde peso e baixa um pouco) e volta devagar.
    val deflate = remember { Animatable(0f) }
    LaunchedEffect(fx.naoFiz) { if (fx.naoFiz > 0 && !reduce) { deflate.animateTo(1f, tween(M.BASE, easing = M.Depart)); deflate.animateTo(0f, tween(M.REVEAL, easing = M.Arrive)) } }
    // Abertura: entra largo e leve, assenta quando a grade termina de acender.
    val open = remember { Animatable(0f) }
    LaunchedEffect(fx.openAt) { if (fx.openAt > 0 && !reduce) { open.snapTo(1f); delay(430); open.animateTo(0f, spring(0.6f, 260f)) } }
    // Marco: treme no swell, dá um soco no impacto e uma luz atravessa o número.
    val lt = launchClock(fx)
    val hitK = ((lt - LAUNCH_RISE) / 700f).coerceAtLeast(0f)
    val punch = if (lt >= LAUNCH_RISE) 0.16f * kotlin.math.exp(-4f * hitK) * kotlin.math.cos(7f * hitK) else 0f
    // tremor do swell: dois senos (13 e 19 Hz) crescendo, não ruído; no máximo 1,2 dp
    val shakeAmp = if (lt > 0f && lt < LAUNCH_RISE) Math.pow((lt / LAUNCH_RISE).toDouble(), 2.2).toFloat() * 1.2f else 0f
    val shakeX = shakeAmp * kotlin.math.sin(lt / 1000f * 2f * Math.PI.toFloat() * 13f)
    val shakeY = shakeAmp * kotlin.math.sin(lt / 1000f * 2f * Math.PI.toFloat() * 19f + 1.3f)
    val sheen = if (lt > LAUNCH_RISE + 400) ((lt - LAUNCH_RISE - 400) / 700f) else -1f
    // a fonte muda em degraus (24 por toda a rolagem): o Hero não recompõe a cada pixel; escala e posição são lidas no desenho
    val scrollStep by remember(reduce) { androidx.compose.runtime.derivedStateOf { if (reduce) 0 else ((scroll.value / span).coerceIn(0f, 1f) * 24f).toInt() } }
    val scrollT = scrollStep / 24f
    val s1 = stretch.value
    val width = 68f + 34f * s1 + 57f * open.value + 40f * scrollT - 4f * deflate.value
    val weight = 650f + 120f * s1 - 350f * open.value - 260f * scrollT - 360f * deflate.value
    val face = heroFace(width, weight)
    // Voltar no tempo: arrastar o número para a esquerda volta dia por dia; soltar corre de volta até hoje.
    val view = androidx.compose.ui.platform.LocalView.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrubBack = remember { Animatable(0f) }
    val scrubJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { fx.scrub = 0f } }
    // até o primeiro registro (não a janela do mapa); sem registro, o gesto nem existe
    val maxBack = remember(u.today, u.state.done, u.state.missed) {
        val first = (u.state.done + u.state.missed).minOrNull()
        if (first == null) 0f else java.time.temporal.ChronoUnit.DAYS.between(first, u.today).toFloat().coerceAtLeast(0f)
    }
    val shown = if (fx.scrub >= 1f) streakAt(u, u.today.minusDays(fx.scrub.toLong())) else u.streak
    // Líquido: a superfície inclina com o celular e balança com a rolagem, o arrasto e o feito, numa mola amortecida.
    val tilt = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val gravity = remember { floatArrayOf(0f) }
    val kick = remember { floatArrayOf(0f) }
    val active = LocalHojeActive.current
    if (!reduce && time != null && active) {
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            val sm = ctx.getSystemService(android.hardware.SensorManager::class.java)
            val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
            val l = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(e: android.hardware.SensorEvent) {
                    val deg = Math.toDegrees(kotlin.math.atan2(e.values[0].toDouble(), e.values[1].toDouble().coerceAtLeast(0.5))).toFloat()
                    gravity[0] = gravity[0] * 0.8f + (deg * 0.55f).coerceIn(-7f, 7f) * 0.2f
                }
                override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
            }
            if (sensor != null) sm.registerListener(l, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
            onPauseOrDispose { sm?.unregisterListener(l) }
        }
        LaunchedEffect(fx.feito) { if (fx.feito > 0) kick[0] += 2.4f }
        LaunchedEffect(Unit) {
            var a = 0f; var v = 0f; var lastScroll = scroll.value.toFloat(); var lastVel = 0f
            var last = withFrameNanos { it }
            while (true) {
                // assentado (sem movimento, sem chute, sem rolagem): dorme e confere 10× por segundo
                if (kotlin.math.abs(v) < 0.01f && kotlin.math.abs(a - gravity[0]) < 0.05f && kotlin.math.abs(kick[0]) < 0.01f && scroll.value.toFloat() == lastScroll) {
                    delay(100); last = withFrameNanos { it }; continue
                }
                withFrameNanos { now ->
                    val dt = ((now - last) / 1e9f).coerceIn(0.001f, 0.05f); last = now
                    val sv = (scroll.value - lastScroll) / dt; lastScroll = scroll.value.toFloat()
                    val acc = (sv - lastVel); lastVel = sv
                    v += (-(a - gravity[0]) * 55f - v * 7.5f) * dt + (-acc * 0.0009f).coerceIn(-1f, 1f) + kick[0] * 30f * dt
                    kick[0] *= Math.pow(0.6, (dt * 60f).toDouble()).toFloat() // igual a 60 e a 120 Hz
                    a = (a + v * dt).coerceIn(-9f, 9f)
                    tilt.floatValue = a
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = if (compact) 10.dp else 18.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$shown ${unitLabel(u, " ")}" },
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier.offset(x = (-4).dp).graphicsLayer {
                val t = if (reduce) 0f else (scroll.value / span).coerceIn(0f, 1f)
                val s = (1f - t * 0.42f) * (1f + punch)
                scaleX = s; scaleY = s * (1f - 0.06f * deflate.value)
                translationY = -22.dp.toPx() * t + minOf(scroll.value.toFloat(), span) * 0.32f
                if (shakeAmp > 0f) { translationX = shakeX.dp.toPx(); translationY += shakeY.dp.toPx() }
                alpha = (1f - 0.85f * t) * (1f - 0.6f * open.value)
                transformOrigin = TransformOrigin(0f, 1f)
            }.onGloballyPositioned { fx.number = it.boundsInRoot() }
                .then(
                    if (u.weekly || maxBack <= 0f) Modifier else Modifier.pointerInput(maxBack) {
                        val d1 = 14.dp.toPx(); val d2 = 7.dp.toPx(); val d3 = 3.5.dp.toPx()
                        // 14 dp por dia na primeira semana, 7 até 30 dias, 3,5 depois: perto é preciso, longe é rápido
                        fun daysFor(dist: Float): Float = when {
                            dist < 7 * d1 -> dist / d1
                            dist < 7 * d1 + 23 * d2 -> 7f + (dist - 7 * d1) / d2
                            else -> 30f + (dist - 7 * d1 - 23 * d2) / d3
                        }
                        fun stepAt(days: Float) = if (days < 7f) d1 else if (days < 30f) d2 else d3
                        var dist = 0f
                        var lastTick = 0L
                        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                scrubJob[0]?.cancel(); tracker.resetTracking()
                                // retoma de onde a volta estava
                                dist = run { var lo = 0f; var hi = 400f * d1; repeat(30) { val m = (lo + hi) / 2; if (daysFor(m) < fx.scrub) { lo = m } else { hi = m } }; lo }
                            },
                            onDragEnd = {
                                val from = fx.scrub
                                val vx = tracker.calculateVelocity().x // px/s; para a direita acelera a volta
                                if (from > 0f) scrubJob[0] = scope.launch {
                                    scrubBack.snapTo(from)
                                    scrubBack.animateTo(0f, spring(1f, 60f), initialVelocity = (-vx / stepAt(from)).coerceIn(-400f, 0f)) { fx.scrub = value.coerceAtLeast(0f) }
                                    fx.scrub = 0f
                                }
                            },
                            onDragCancel = { fx.scrub = 0f },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val before = fx.scrub.toInt()
                            dist = (dist - dx).coerceAtLeast(0f)
                            fx.scrub = daysFor(dist).coerceIn(0f, maxBack)
                            kick[0] += dx * 0.012f // o líquido sente o arrasto
                            val now = android.os.SystemClock.uptimeMillis()
                            if (fx.scrub.toInt() != before && now - lastTick >= 45) {
                                lastTick = now
                                if (u.state.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                if (u.state.sounds) Sounds.scrubTick(ctx, u.state.palette)
                            }
                        }
                    },
                ),
        ) {
            val d = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale.coerceAtMost(1.15f))) {
                Box(
                    Modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithContent {
                        drawContent()
                        if (sheen in 0f..1f) {
                            val x = -size.width * 0.6f + sheen * size.width * 2.2f
                            drawRect(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    0f to Color.Transparent, 0.5f to androidx.compose.ui.graphics.lerp(Color.White, pal.levels[1], 0.35f).copy(alpha = 0.6f), 1f to Color.Transparent,
                                    start = Offset(x, 0f), end = Offset(x + size.width * 0.45f, size.height * 0.25f),
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
                            )
                        }
                    },
                ) {
                    Box(if (time != null) Modifier.materialFill(pal, { level }, { time.value }, petals, { tilt.floatValue }) else Modifier) {
                        val base = if (compact) Type.hero.copy(fontSize = 128.sp, lineHeight = 118.sp, letterSpacing = (-5.5).sp) else Type.hero
                        if (fx.scrub > 0f) Text("$shown", color = if (time != null) vessel(pal) else pal.ink, style = base.copy(fontFamily = face), maxLines = 1, softWrap = false)
                        else RollingNumber("$shown", if (time != null) vessel(pal) else pal.ink, base.copy(fontFamily = face))
                    }
                }
            }
        }
        Column(Modifier.padding(start = 14.dp, bottom = 16.dp)) {
            // voltando no tempo, a data aparece acima do rótulo, sem empurrar nada
            val ago = fx.scrub.toInt()
            val dateA by animateFloatAsState(if (ago >= 1) 1f else 0f, tween(160), label = "ago")
            Text(
                if (ago >= 1) "há $ago ${if (ago == 1) "dia" else "dias"} · ${u.today.minusDays(ago.toLong()).format(DAY_MONTH)}" else " ",
                style = Type.body, color = pal.ink, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(bottom = 4.dp).graphicsLayer { alpha = dateA },
            )
            Text(if (ago >= 1) (if (shown == 1) "dia\nseguido" else "dias\nseguidos") else unitLabel(u, "\n"), style = Type.title.copy(fontWeight = FontWeight.Medium, lineHeight = 20.sp), color = pal.ink)
        }
    }
    ReturnLine(u)
}

/** Na volta: qual volta é esta e quanto durou a sequência anterior. */
@Composable
private fun ReturnLine(u: HabitUi) {
    val pal = Tokens
    val back = !u.weekly && u.state.inReturn
    val sealed = u.state.sealed
    AnimatedVisibility(back || sealed != null, enter = fadeIn(tween(Motion.base)) + expandVertically(), exit = fadeOut(tween(Motion.quick)) + shrinkVertically()) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sealed != null) { SealChip(sealed.forDay); Spacer(Modifier.width(10.dp)) }
            if (back) Text(
                "${u.state.returnNumber}ª volta", style = Type.caption, color = pal.ink, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.border(1.dp, pal.rule, RoundedCornerShape(Radius.control)).padding(horizontal = 7.dp, vertical = 3.dp),
            )
            if (back) Spacer(Modifier.width(12.dp))
            if (back) Text("última: ${u.state.lastRunDays} ${if (u.state.lastRunDays == 1) "dia" else "dias"}", style = Type.caption, color = pal.text3)
        }
    }
}

/** Régua até o próximo marco: um traço por dia (até 30), os feitos acesos, e quanto falta. */
@Composable
internal fun Gauge(u: HabitUi) {
    val streak = u.streak
    if (u.weekly) {
        // Meta semanal: um traço por vez da meta, os feitos da semana acesos.
        val done = u.doneThisWeek
        val left = u.goal - done
        GaugeRuler(
            n = u.goal, target = minOf(done, u.goal).toFloat(), start = "0", end = "${u.goal}×", pulse = LocalFx.current.feito,
            middle = if (left <= 0) "meta da semana cumprida" else if (left == 1) "falta 1 nesta semana" else "faltam $left nesta semana",
        )
        return
    }
    if (isMilestone(streak) && u.todayMark == Mark.DONE) {
        val before = MILESTONE_STEPS.lastOrNull { it < streak } ?: 0
        val n = minOf(streak - before, 30)
        val launching = LocalFx.current.launchAt != 0L
        GaugeRuler(
            n = n, target = n.toFloat(), start = "$before", end = "$streak", middle = "",
            stagger = if (launching) 900L / n else 22L, startDelay = if (launching) 250L else 0L,
        )
        return
    }
    val next = MILESTONE_STEPS.firstOrNull { it > streak } ?: ((streak / 365 + 1) * 365)
    val prev = MILESTONE_STEPS.lastOrNull { it <= streak && it < next } ?: 0
    val span = next - prev
    val n = minOf(span, 30)
    val left = next - streak
    GaugeRuler(
        n = n, target = (streak - prev).toFloat() / span * n, start = "$prev", end = "$next", pulse = LocalFx.current.feito,
        middle = "",
    )
}

@Composable
internal fun GaugeRuler(n: Int, target: Float, start: String, end: String, middle: String, pulse: Int = 0, stagger: Long = 22L, startDelay: Long = 0L) {
    val pal = Tokens
    val reduce = LocalReduceMotion.current
    // Depois do feito, os últimos traços acesos pulam um a um, no ritmo dos tiques da régua (Sounds.tick).
    val bump = remember(n) { List(n) { Animatable(0f) } }
    val litNow by rememberUpdatedState(target.roundToInt())
    LaunchedEffect(pulse) {
        if (pulse == 0 || reduce) return@LaunchedEffect
        delay(LADDER_START)
        val k = minOf(LADDER, litNow)
        for (j in 0 until k) {
            val idx = (litNow - k + j).coerceIn(0, n - 1)
            launch { bump[idx].snapTo(1f); bump[idx].animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
            delay(LADDER_STEP)
        }
    }
    // Cada traço tem a própria mola: ao acender, cresce com um quique curto e ganha a cor, um depois do outro.
    val ticks = remember(n) { List(n) { Animatable(0f) } }
    val lit = target.roundToInt()
    LaunchedEffect(lit, n) {
        val changed = ticks.indices.filter { (it < lit) != (ticks[it].targetValue > 0.5f) || (!ticks[it].isRunning && ticks[it].value != (if (it < lit) 1f else 0f)) }
        changed.forEachIndexed { k, i ->
            val on = if (i < lit) 1f else 0f
            launch {
                if (reduce) ticks[i].snapTo(on)
                else { delay(startDelay + k * stagger); ticks[i].animateTo(on, spring(0.5f, 520f)) }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val w = 1.5.dp.toPx()
            val gap = (size.width - w) / (n - 1).coerceAtLeast(1)
            for (i in 0 until n) {
                val on = ticks[i].value
                val end = i == 0 || i == n - 1
                val b = bump[i].value
                val h = (if (end) 12.dp.toPx() else lerp(6.dp.toPx(), 10.dp.toPx(), on)).coerceAtLeast(1f) * (1f + 1.1f * b)
                val off = pal.line.copy(alpha = if (end) 0.9f else Alpha.tick)
                val c = androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.lerp(off, pal.done, on.coerceIn(0f, 1f)), pal.levels[4], b)
                drawRect(c, Offset(i * gap, size.height - h), Size(w, h))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(start, style = Type.caption, color = pal.text3)
            Spacer(Modifier.weight(1f))
            RollingLabel(middle, Type.caption, pal.text3)
            Spacer(Modifier.weight(1f))
            Text(end, style = Type.caption, color = pal.ink, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Quando o herói sai da tela, uma barra fina assume no topo com a tarefa e a sequência.
 * Surge em proporção à rolagem (sem salto) e ganha a régua fina embaixo.
 */
@Composable
internal fun CompactHeader(u: HabitUi, scroll: ScrollState) {
    val pal = Tokens
    val density = LocalDensity.current
    val start = with(density) { 150.dp.toPx() }
    val span = with(density) { 40.dp.toPx() }
    val progress by remember { derivedStateOf { ((scroll.value - start) / span).coerceIn(0f, 1f) } }
    if (progress == 0f) return
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = progress; translationY = (1f - progress) * -8.dp.toPx() }
            .background(pal.bg),
    ) {
        Row(Modifier.statusBarsPadding().height(52.dp).padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
            Text(u.state.taskName, color = pal.ink, style = Type.bodyL, maxLines = 1, modifier = Modifier.weight(1f))
            RollingNumber("${u.streak}", pal.ink, Type.compactNum)
            Spacer(Modifier.width(6.dp))
            Text(if (u.weekly) (if (u.streak == 1) "semana" else "semanas") else (if (u.streak == 1) "dia" else "dias"), color = pal.text2, style = Type.body)
        }
        Hairline()
    }
}

@Composable
internal fun TodayPanel(u: HabitUi, ripple: Int, onDone: () -> Unit, onSkip: () -> Unit, onDay: (LocalDate) -> Unit, picked: LocalDate? = null) {
    val pal = Tokens
    Panel {
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append(u.today.format(WEEKDAY).replaceFirstChar { it.uppercase() } + ", ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = pal.text2)); append(u.today.format(DAY_MONTH)); pop()
            },
            style = Type.title.copy(fontWeight = FontWeight.Medium), color = pal.ink,
        )
        Spacer(Modifier.height(14.dp))
        Actions(u.todayMark, u.state.doneAt, onDone, onSkip, HoldFeedback(u.state.haptics, u.state.sounds, u.state.palette))
        Spacer(Modifier.height(18.dp))
        WeekStrip(u, ripple, onDay, picked)
    }
}

/**
 * "Não fiz | Marcar feito". Ao escolher, o botão escolhido se expande e engole o outro (que some esmaecendo):
 * feito vira o bloco verde, o ✓ se desenha e o rótulo rola para "Feito hoje", com "às HH:MM" aparecendo no canto;
 * não fiz vira o contorno de tinta, o rótulo rola para "Não fiz hoje" e o ponto vermelho nasce com mola.
 * Tocar no bloco expandido desfaz.
 */
@Composable
internal fun Actions(mark: Mark?, doneAt: Long, onDone: () -> Unit, onSkip: () -> Unit, fb: HoldFeedback = HoldFeedback(false, false, "verde")) {
    // Um toque rápido não marca: o rótulo pede para segurar por um instante.
    var hint by remember { mutableStateOf<Int?>(null) } // 0 = não fiz, 1 = feito
    val holdHint = LocalFx.current.holdHint
    LaunchedEffect(holdHint) { if (holdHint > 0) hint = 1 }
    LaunchedEffect(hint) { if (hint != null) { delay(1600); hint = null } }
    val pal = Tokens
    val reduce = LocalReduceMotion.current
    val share = when (mark) { Mark.DONE -> 1f; Mark.MISSED -> 0f; null -> 2f / 3f }
    val f by animateFloatAsState(share, if (reduce) tween(0) else tween(520, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)), label = "split")
    val doneBg by animateColorAsState(if (mark == Mark.DONE) pal.done else pal.ink, motion(200), label = "doBg")
    val doneFg by animateColorAsState(if (mark == Mark.DONE) pal.onDone else pal.bg, motion(200), label = "doFg")
    val missBorder by animateColorAsState(if (mark == Mark.MISSED) pal.ink else pal.line, motion(200), label = "missB")
    val missFg by animateColorAsState(if (mark == Mark.MISSED) pal.ink else pal.text2, motion(200), label = "missF")
    val missBg by animateColorAsState(if (mark == Mark.MISSED) pal.surface else pal.surface.copy(alpha = 0f), motion(200), label = "missBg")
    val dot by animateFloatAsState(if (mark == Mark.MISSED) 1f else 0f, if (reduce) tween(0) else spring(0.55f, 600f), label = "dot")
    // O ✓ se desenha só quando hoje acaba de virar feito, não ao abrir o app.
    val check = remember { Animatable(1f) }
    val wasDone = remember { mutableStateOf(mark == Mark.DONE) }
    LaunchedEffect(mark) {
        val now = mark == Mark.DONE
        if (now && !wasDone.value && !reduce) { check.snapTo(0f); check.animateTo(1f, tween(340, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) }
        wasDone.value = now
    }
    val time = remember(doneAt) {
        if (doneAt == 0L) null else Instant.ofEpochMilli(doneAt).atZone(ZoneId.systemDefault()).format(HHMM)
    }
    val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
    Layout(
        content = {
            // Não fiz
            ActionBox(
                onSkip,
                fill = if (mark == Mark.MISSED) Tokens.ink.copy(alpha = 0.14f) else Tokens.miss.copy(alpha = 0.22f), fb = fb,
                label = if (mark == Mark.MISSED) "Desfazer não fiz" else "Não fiz", onShortTap = { hint = 0 },
                modifier = Modifier
                    .graphicsLayer { alpha = ((1f - f) * 3f).coerceIn(0f, 1f) }
                    .border(if (mark == Mark.MISSED) 1.5.dp else 1.dp, missBorder, RoundedCornerShape(Radius.control))
                    .background(missBg),
            ) {
                Row(Modifier.align(Alignment.CenterStart).padding(start = 16.dp).wrapContentWidth(Alignment.Start, unbounded = true), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_x), null, Modifier.size(16.dp), tint = missFg)
                    Spacer(Modifier.width(10.dp))
                    RollingLabel(if (hint == 0) "Segure" else if (mark == Mark.MISSED) "Não fiz hoje" else "Não fiz", Type.bodyL.copy(fontWeight = FontWeight.SemiBold), missFg)
                }
                Canvas(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(6.dp).graphicsLayer { scaleX = dot; scaleY = dot }) { drawCircle(pal.miss) }
            }
            // Marcar feito
            ActionBox(
                onDone, fill = if (mark == Mark.DONE) Tokens.bg.copy(alpha = 0.22f) else Tokens.done, fb = fb,
                label = if (mark == Mark.DONE) "Desfazer feito" else "Marcar feito", onShortTap = { hint = 1 },
                modifier = Modifier.graphicsLayer { alpha = (f * 3f).coerceIn(0f, 1f) }.background(doneBg),
            ) {
                Row(Modifier.align(Alignment.CenterStart).padding(start = 16.dp).wrapContentWidth(Alignment.Start, unbounded = true), verticalAlignment = Alignment.CenterVertically) {
                    CheckMark(doneFg, check.value)
                    Spacer(Modifier.width(10.dp))
                    RollingLabel(
                        if (hint == 1) (if (mark == Mark.DONE) "Segure para desfazer" else "Segure para marcar") else if (mark == Mark.DONE) "Feito hoje" else "Marcar feito",
                        Type.bodyL.copy(fontWeight = FontWeight.SemiBold), doneFg,
                    )
                }
                val sub by animateFloatAsState(if (mark == Mark.DONE && time != null) 0.85f else 0f, motion(200), label = "sub")
                if (time != null) Text(
                    "às $time", style = Type.caption.copy(fontSize = 12.sp), color = doneFg, maxLines = 1, softWrap = false,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).graphicsLayer { alpha = sub },
                )
            }
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { ms, c ->
        val w = c.maxWidth
        // A fresta entre os dois some quando um deles ocupa tudo.
        val g = (gap * (minOf(f, 1f - f) * 6f).coerceIn(0f, 1f)).roundToInt()
        val markW = ((w - g) * f).roundToInt().coerceIn(0, w)
        val skipW = (w - g - markW).coerceAtLeast(0)
        val skip = ms[0].measure(Constraints.fixed(skipW, c.maxHeight))
        val done = ms[1].measure(Constraints.fixed(markW, c.maxHeight))
        layout(w, c.maxHeight) {
            skip.place(0, 0)
            done.place(skipW + g, 0)
        }
    }
}

/** O ✓ do protótipo, desenhado por um traço que avança de 0 a [progress]. */
@Composable
internal fun CheckMark(color: Color, progress: Float) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.width / 24f
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(4.5f * s, 12.5f * s); lineTo(9.5f * s, 17.5f * s); lineTo(19.5f * s, 7f * s) }
        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val part = androidx.compose.ui.graphics.Path()
        measure.getSegment(0f, measure.length * progress, part, true)
        drawPath(part, color, style = Stroke(1.75.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * Um dos dois botões do dia. Marcar (e desmarcar) é segurar por [HOLD_MS]: o botão se enche como um copo a partir
 * da esquerda, afunda de leve, dá dois toques curtos no caminho e um som baixo cresce junto. Soltar antes escoa
 * de volta. Leitor de tela: o toque duplo vale direto.
 */
@Composable
internal fun ActionBox(
    onClick: () -> Unit, fill: Color, fb: HoldFeedback, label: String, onShortTap: () -> Unit, modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val view = androidx.compose.ui.platform.LocalView.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val holdJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val lockedUntil = remember { longArrayOf(0L) }
    val commit by rememberUpdatedState(onClick)
    val short by rememberUpdatedState(onShortTap)
    val feedback by rememberUpdatedState(fb)
    val fillColor by rememberUpdatedState(fill)
    Box(
        Modifier.fillMaxHeight()
            .graphicsLayer { val p = progress.value; val s = 1f - 0.035f * p; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(Radius.control)).then(modifier)
            .drawBehind {
                val p = progress.value
                if (p <= 0f) return@drawBehind
                val e = 1f - (1f - p) * (1f - p)
                val x = size.width * e
                val wob = if (reduce) 0f else 4.dp.toPx() * (1f - p)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f); lineTo(x + wob, 0f)
                    quadraticBezierTo(x - wob, size.height / 2, x + wob, size.height)
                    lineTo(0f, size.height); close()
                }
                drawPath(path, fillColor)
            }
            .semantics(mergeDescendants = true) { role = Role.Button; onClick(label) { commit(); true } }
            .pointerInput(Unit) {
                val hold = holdMillis(view.context)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // logo depois de marcar, outro toque não faz nada (sem desfazer por engano)
                    if (android.os.SystemClock.uptimeMillis() < lockedUntil[0]) { waitForUpOrCancellation(); return@awaitEachGesture }
                    holdJob[0]?.cancel()
                    val f = feedback
                    var stream = 0
                    var done = false
                    holdJob[0] = scope.launch {
                        progress.snapTo(0f)
                        // 120 ms de intenção: o copo já se mexe, mas som e vibração só se o dedo ficar
                        progress.animateTo(0.08f, tween(INTENT_MS, easing = LinearEasing))
                        if (f.sounds) stream = Sounds.holdStart(ctx, f.palette)
                        if (f.haptics) view.performHapticFeedback(gestureStart())
                        var t1 = false; var t2 = false
                        progress.animateTo(1f, tween(hold - INTENT_MS, easing = LinearEasing)) {
                            if (!t1 && value >= 0.5f) { t1 = true; if (f.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                            if (!t2 && value >= 0.8f) { t2 = true; if (f.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                        }
                        done = true
                        lockedUntil[0] = android.os.SystemClock.uptimeMillis() + 600
                        Sounds.holdStop(stream, 60)
                        commit()
                        // o copo fica cheio até o botão trocar de estado (a cor nova cobre), e então some
                        delay(420)
                        progress.animateTo(0f, tween(180))
                    }
                    val up = waitForUpOrCancellation()
                    if (!done) {
                        holdJob[0]?.cancel()
                        Sounds.holdStop(stream, 140)
                        if (up != null) short() // soltou antes: o rótulo pede para segurar
                        holdJob[0] = scope.launch { progress.animateTo(0f, spring(0.62f, 380f)) }
                    }
                }
            },
        content = content,
    )
}

/** Os últimos 7 dias como células grandes com o número do dia; toque corrige o dia. */
@Composable
internal fun WeekStrip(u: HabitUi, ripple: Int, onDay: (LocalDate) -> Unit, picked: LocalDate? = null) {
    val pal = Tokens
    val days = remember(u.today) { (6 downTo 0).map { u.today.minusDays(it.toLong()) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { k, d ->
            Column(Modifier.weight(1f)) {
                val level = u.level(d)
                val color by animateColorAsState(pal.levels[level], motion(Motion.slow), label = "day")
                val ink by animateColorAsState(pal.levelInk[level], motion(Motion.slow), label = "dayInk")
                val press = remember { MutableInteractionSource() }
                val pressed by press.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 0.94f else 1f, Motion.press, label = "press")
                val lift = remember { Animatable(0f) }
                LaunchedEffect(ripple) {
                    if (ripple > 0) { delay(k * 55L); lift.animateTo(1f, tween(180, easing = FastOutSlowInEasing)); lift.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }
                }
                val mark = u.markOf(d)
                val isToday = d == u.today
                val fxw = LocalFx.current
                val reduceW = LocalReduceMotion.current
                val drop = remember { Animatable(1f) }
                val wasMissed = remember { mutableStateOf(mark == Mark.MISSED) }
                LaunchedEffect(mark) {
                    val was = wasMissed.value; wasMissed.value = mark == Mark.MISSED
                    if (isToday && mark == Mark.MISSED && !was && !reduceW) { drop.snapTo(0f); drop.animateTo(1f, M.touch()) }
                }
                val lantern = if (isToday && mark == null && !u.state.isRest(d) && !reduceW) lanternLevel(LocalHour.current) else 0f
                val glowS = breathState(lantern > 0f)
                val rise = remember { Animatable(1f) }
                LaunchedEffect(fxw.openAt) { if (fxw.openAt > 0 && !reduceW) { rise.snapTo(0f); delay(380L + k * 30L); try { rise.animateTo(1f, tween(M.BASE, easing = M.Arrive)) } finally { rise.snapTo(1f) } } }
                val trope = d in u.state.tropes
                val scar = d in u.state.scars
                val call = isToday && mark == null && u.state.pendingTrope
                val breatheS = breathState(call)
                // Hoje marcado: o quadrado se enche no gesto da cor (broto, flor, gota, maré…).
                val gesture = remember { Animatable(1f) }
                val ring = remember { Animatable(1f) }
                val wasDone = remember { mutableStateOf(mark == Mark.DONE) }
                val reduceMotion = LocalReduceMotion.current
                val fx = LocalFx.current
                val dens = LocalDensity.current.density
                LaunchedEffect(isToday, mark) {
                    val was = wasDone.value
                    wasDone.value = mark == Mark.DONE
                    if (isToday && mark == Mark.DONE && !was && !reduceMotion) {
                        gesture.snapTo(0f); gesture.animateTo(1f, tween(if (pal.gesture == Gesture.TRACO) 700 else 620, easing = LinearEasing))
                    }
                }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .graphicsLayer { if (rise.value < 1f) { alpha = rise.value; translationY = (1f - rise.value) * 5.dp.toPx() } }
                        .then(if (isToday) Modifier.onGloballyPositioned { fx.todayCell = it.boundsInRoot() } else Modifier)
                        .graphicsLayer { translationY = -4.dp.toPx() * lift.value; scaleX = scale; scaleY = scale }
                        .clickable(interactionSource = press, indication = null) { onDay(d) },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = CornerRadius(Radius.cell.toPx())
                        // à noite, hoje ainda pendente acende como uma lanterna que respira
                        val glow = glowS.value
                        val breathe = breatheS.value
                        if (lantern > 0f) drawCircle(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(pal.levels[3].copy(alpha = ((if (pal.bg.luminance() < 0.3f) 0.14f else 0.10f) + (if (pal.bg.luminance() < 0.3f) 0.14f else 0.12f) * glow) * lantern), Color.Transparent),
                                center = center, radius = size.width * (1.1f + 0.25f * glow),
                            ),
                            radius = size.width * (1.1f + 0.25f * glow), center = center,
                        )
                        if (gesture.value < 1f) gestureFill(pal.gesture, gesture.value, pal.levels[0], pal.levels[level], r.x)
                        else drawRoundRect(color, cornerRadius = r)
                        if (scar) stitch(pal, Offset.Zero, size.width, size.height, 6.dp.toPx(), vertical = false)
                        else if (trope) tropeMark(pal, Offset(size.width - 17.dp.toPx(), size.height - 17.dp.toPx()), 13.dp.toPx(), 2.dp.toPx())
                        else if (mark == Mark.MISSED) {
                            val k = drop.value
                            drawCircle(pal.miss, 2.5.dp.toPx() * (0.4f + 0.6f * k).coerceAtLeast(0.4f), Offset(size.width - 8.5.dp.toPx(), size.height - 8.5.dp.toPx() - (1f - k) * 14.dp.toPx()), alpha = k.coerceIn(0f, 1f))
                        }
                        else if (u.state.isRest(d) && d != u.today) drawRect(pal.text3.copy(alpha = 0.7f), Offset(size.width - 16.dp.toPx(), size.height - 9.dp.toPx()), Size(8.dp.toPx(), 1.5.dp.toPx()))
                        // dia passado escolhido: espera o segundo toque para valer
                        if (d == picked) drawRoundRect(pal.ink, Offset(-2.dp.toPx(), -2.dp.toPx()), Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()), CornerRadius(r.x + 2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                        if (call) callRing(pal, Offset.Zero, size.width, size.height, r.x, breathe)
                        else if (isToday && mark == null) {
                            val i = 0.75.dp.toPx()
                            drawRoundRect(pal.ink, Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), r, style = Stroke(1.5.dp.toPx()))
                        }
                        // Marco: asterisco no canto, na cor do número.
                        if (d in u.milestones) star(ink, Offset(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()), 12.dp.toPx(), glyph = pal.glyph)
                        // Feito: um anel sai do quadrado e se dissipa.
                        if (ring.value < 1f) {
                            val k = ring.value
                            val s = 0.85f + 1.25f * (1f - (1f - k) * (1f - k))
                            val w = size.width * s; val h = size.height * s
                            drawRoundRect(
                                pal.levels[4].copy(alpha = 0.9f * (1f - k)), Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h),
                                CornerRadius(r.x * s), style = Stroke((2.5f * (1f - k) + 0.5f).dp.toPx()),
                            )
                        }
                    }
                    Text("${d.dayOfMonth}", style = Type.caption.copy(lineHeight = 12.sp), color = ink, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, top = 5.dp))
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    Cal.initial(d), style = Type.caption.copy(lineHeight = 12.sp),
                    color = if (isToday) pal.ink else pal.text3, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 1.dp),
                )
            }
        }
    }
}

/** Primeiro uso: a grade esmaece e um convite editorial assume o bloco. */
@Composable
internal fun Invite(modifier: Modifier) {
    val pal = Tokens
    Column(
        modifier.padding(end = 14.dp).background(pal.surface.copy(alpha = Alpha.scrim)),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Seu primeiro dia começa hoje.", style = Type.invite, color = pal.ink)
        Spacer(Modifier.height(6.dp))
        Text("Marque feito e o primeiro quadrado acende aqui.", style = Type.body, color = pal.text2)
    }
}


/**
 * A aba Hoje: agir e sentir o momento, numa tela só. Tarefa, sequência, régua do marco, o bloco Hoje com a semana
 * e o ano compacto, que é prévia e porta para a História.
 */
@Composable
internal fun HojePage(
    u: HabitUi, ripple: Int, scroll: ScrollState,
    onDone: () -> Unit, onSkip: () -> Unit, onDay: (LocalDate) -> Unit, onHistory: () -> Unit,
    onGrid: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    footer: @Composable () -> Unit = {},
    picked: LocalDate? = null,
) {
    val near by remember { androidx.compose.runtime.derivedStateOf { scroll.value < 500 } }
    val time = rememberFrameTime(LocalHojeActive.current && near)
    val pal = Tokens
    val dark = pal.bg.luminance() < 0.3f
    val hour = rememberHour()
    val sky = skyTint(hour, dark, pal)
    androidx.compose.runtime.CompositionLocalProvider(LocalHour provides hour) {
    Box(Modifier.fillMaxSize()) {
    // O campo vivo: atrás do número, some ao rolar.
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().height(460.dp).graphicsLayer { alpha = (1f - scroll.value / 500f).coerceIn(0f, 1f); translationY = -scroll.value * 0.4f },
    ) {
        // a luz do dia, quase nada, por trás do campo vivo
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(sky, Color.Transparent), startY = 0f, endY = size.height))
        livingField(pal, time.value, dark)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .navigationBarsPadding()
            .padding(horizontal = Space.gutter)
            .padding(top = Space.s, bottom = Space.l),
    ) {
        Column(Modifier.entrance(0)) {
            Rule(index = 0)
            Top(u)
            Hero(u, scroll, compact = true, time = time)
            Gauge(u)
        }
        Column(Modifier.padding(top = 28.dp).entrance(1)) {
            Rule(index = 1)
            TodayPanel(u, ripple, onDone = onDone, onSkip = onSkip, onDay = onDay, picked = picked)
        }
        Column(Modifier.padding(top = 28.dp).entrance(2)) {
            Rule(index = 2)
            MiniYear(u, onHistory, onGrid)
        }
        footer()
    }
    }
    }
}

/** As últimas 20 semanas, sem rótulos. Tocar leva à História, no mapa do ano. */
@Composable
internal fun MiniYear(u: HabitUi, onClick: () -> Unit, onGrid: (androidx.compose.ui.geometry.Rect) -> Unit = {}) {
    val pal = Tokens
    val weeks = 20
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 0.985f else 1f, Motion.press, label = "mini")
    val nudge by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 3f else 0f, Motion.press, label = "go")
    val fx = LocalFx.current
    val reduceM = LocalReduceMotion.current
    val appear = remember { Animatable(1f) }
    LaunchedEffect(fx.openAt) { if (fx.openAt > 0 && !reduceM) { appear.snapTo(0f); delay(330); try { appear.animateTo(1f, tween(600, easing = LinearEasing)) } finally { appear.snapTo(1f) } } }
    // Onda da corrente: depois do feito, uma luz volta pela sequência até o primeiro dia dela; os primeiros traços no
    // ritmo dos tiques da régua, depois cada vez mais rápido.
    val wave = remember { Animatable(-1f) }
    LaunchedEffect(fx.feito) {
        if (fx.feito > 0 && !reduceM && !u.weekly) {
            val end = waveAt(139, 140) + 300f
            try { wave.snapTo(0f); wave.animateTo(end, tween(end.toInt(), easing = LinearEasing)) } finally { wave.snapTo(-1f) }
        }
    }
    val scrubDay = if (fx.scrub >= 1f) u.today.minusDays(fx.scrub.toLong()) else null
    Panel(Modifier.scale(scale).clickable(press, indication = null, onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Caps("Últimas 20 semanas", modifier = Modifier.weight(1f))
            Text("História", style = Type.caption, color = pal.ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_arrow_right), null, Modifier.size(14.dp).graphicsLayer { translationX = nudge.dp.toPx() }, tint = pal.ink)
        }
        Spacer(Modifier.height(12.dp))
        val first = u.today.minusDays(((weeks - 1) * 7 + Cal.rowOf(u.today)).toLong())
        Canvas(Modifier.fillMaxWidth().aspectRatio(weeks / 7f).onGloballyPositioned { onGrid(it.boundsInRoot()); fx.miniGrid = it.boundsInRoot() }) {
            val gap = 3.dp.toPx()
            val cell = (size.width - gap * (weeks - 1)) / weeks
            val r = CornerRadius(Radius.cell.toPx())
            for (col in 0 until weeks) for (row in 0 until 7) {
                val d = first.plusDays((col * 7 + row).toLong())
                if (d.isAfter(u.today)) continue
                val tl = Offset(col * (cell + gap), row * (cell + gap))
                val mark = u.markOf(d)
                // abertura: cada quadrado aparece com uma mola curta, em diagonal a partir do canto de cima
                val k = if (appear.value >= 1f) 1f else ((appear.value * 600f - 20f - (col + row) * 12f) / 220f).coerceIn(0f, 1f)
                if (k <= 0f) continue
                var sc = if (k >= 1f) 1f else 0.2f + 0.8f * (1f + 2.70158f * (k - 1f) * (k - 1f) * (k - 1f) + 1.70158f * (k - 1f) * (k - 1f))
                var fillC = pal.levels[u.level(d)]
                var lift = 0f
                if (wave.value >= 0f && mark == Mark.DONE) {
                    val back = java.time.temporal.ChronoUnit.DAYS.between(d, u.today).toInt()
                    if (back < minOf(u.streak, 140)) {
                        val p = ((wave.value - waveAt(back, minOf(u.streak, 140))) / 300f)
                        if (p in 0f..1f) { val b = kotlin.math.sin(p * Math.PI.toFloat()); sc *= 1f + 0.16f * b; lift = 2.dp.toPx() * b; fillC = androidx.compose.ui.graphics.lerp(fillC, pal.levels[4], 0.5f * b) }
                    }
                }
                if (lift != 0f) drawContext.transform.translate(0f, -lift)
                if (sc != 1f) drawContext.transform.scale(sc, sc, tl + Offset(cell / 2, cell / 2))
                habitCell(
                    pal, tl, cell, r.x, fillC, mark, isToday = d == u.today, selected = false,
                    milestone = d in u.milestones, trope = d in u.state.tropes, call = d == u.today && u.state.pendingTrope,
                )
                if (d in u.state.scars) stitch(pal, tl, cell, cell, gap)
                else if (mark == null && u.state.isRest(d) && d != u.today) restDash(pal, tl, cell)
                if (sc != 1f) drawContext.transform.scale(1f / sc, 1f / sc, tl + Offset(cell / 2, cell / 2))
                if (lift != 0f) drawContext.transform.translate(0f, lift)
                // voltando no tempo: o que vem depois do dia escolhido recua, e o dia ganha um contorno
                if (scrubDay != null) {
                    if (d.isAfter(scrubDay)) drawRoundRect(pal.surface.copy(alpha = 0.72f), tl, Size(cell, cell), CornerRadius(r.x))
                    else if (d == scrubDay) drawRoundRect(pal.ink, tl, Size(cell, cell), CornerRadius(r.x), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

/** Hoje esperando a volta: o contorno tracejado respira devagar. */
@Composable
internal fun callBreath(): Float {
    if (LocalReduceMotion.current) return 0f
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "call")
    val v by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "breath",
    )
    return v
}

/** Dia de descanso: um traço curto no lugar do quadrado vazio. */
internal fun DrawScope.restDash(pal: Palette, tl: Offset, cell: Float) {
    val h = 1.5.dp.toPx()
    drawRect(pal.text3.copy(alpha = 0.7f), tl + Offset(cell * 0.25f, cell / 2 - h / 2), Size(cell * 0.5f, h))
}


/** Quantos traços sobem na escada da régua depois do feito (e quantos tiques tocam). */
internal const val LADDER = 7

/** Quando a onda da corrente chega ao dia [back] dias atrás (de [n] na sequência): começa depois do número e da régua, ≤ 1 s. */
internal fun waveAt(back: Int, n: Int = 140): Float =
    420f + 300f * Math.pow((back.toDouble() / (n - 1).coerceAtLeast(1)), 0.85).toFloat()

private fun feedbackOn(u: HabitUi) = u.state.haptics

/** Intenção antes de o segurar soar e vibrar: um toque que vira rolagem fica mudo. */
internal const val INTENT_MS = 120

/** Quanto segurar: 600 ms, ou mais se o sistema estiver com o "tocar e segurar" mais lento (acessibilidade). */
internal fun holdMillis(ctx: android.content.Context): Int =
    maxOf(HOLD_MS, (android.view.ViewConfiguration.getLongPressTimeout() * 1.4f).toInt())

/** Começo de gesto: a constante própria no Android 14+, um tique antes disso. */
internal fun gestureStart(): Int =
    if (android.os.Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.CLOCK_TICK

/** A respiração (0..1, 1,2 s) como estado, para ser lida no desenho e não recompor a cada quadro. */
@Composable
internal fun breathState(on: Boolean): androidx.compose.runtime.State<Float> {
    val zero = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    if (!on || LocalReduceMotion.current) return zero
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
    return t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "breath",
    )
}

/** A hora do dia para quem está dentro do Hoje (um relógio só). */
internal val LocalHour = androidx.compose.runtime.compositionLocalOf { 12f }
