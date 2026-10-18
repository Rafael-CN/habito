package com.rafael.habito

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.runBlocking
import java.time.format.DateTimeFormatter
import java.util.Locale

// Medidas do protótipo (design/mix-v3, 4×3 e 4×2), em dp.
private const val PAD_X = 16f
private const val PAD_TOP = 16f
private const val PAD_BOTTOM = 14f
private const val HEAD_H = 14f   // linha da tarefa e da data
private const val GAP = 12f      // entre cabeçalho, grade e rodapé
private const val BTN = 44f      // altura dos botões (e largura do ✕)
private const val LEFT_W = 94f   // coluna esquerda do 4×2: dois botões de 44 e a fresta de 6
private val CONDENSED = FontFamily("sans-serif-condensed")

/** Cor do mapa escolhida no app (dia e noite) e as opções deste widget. */
private class Look(val levels: List<androidx.glance.unit.ColorProvider>, val done: androidx.glance.unit.ColorProvider, val onDone: androidx.glance.unit.ColorProvider, val opts: WidgetOpts)

private fun lookFor(s: HabitState, opts: WidgetOpts): Look {
    val day = paletteFor(s.palette, false); val night = paletteFor(s.palette, true)
    return Look(
        levels = (0..4).map { DayNight(day.levels[it], night.levels[it]) },
        done = DayNight(day.done, night.done), onDone = DayNight(day.onDone, night.onDone), opts = opts,
    )
}

private val LocalLook = staticCompositionLocalOf { lookFor(HabitState(), WidgetOpts()) }
private val DAY = DateTimeFormatter.ofPattern("EEE d", Locale("pt", "BR"))

/**
 * Empilhado (4×3+): cabeçalho, grade, régua e rodapé. Lado a lado (4×2): texto e botões à esquerda, grade à direita.
 * Mini (4×1): grade e ✓. Pequeno (2×2): número, semana e ✓. Mínimo (2×1): número e ✓.
 */
private enum class Mode { Stacked, Side, Mini, Small, Tiny }

/**
 * Quadros do movimento ao marcar pelo widget. O launcher não anima widgets, então a ação troca o estado
 * em sequência curta (encolhe, passa do tamanho, assenta) e cada troca redesenha só a grade.
 */
object WidgetPulse {
    val stage = kotlinx.coroutines.flow.MutableStateFlow(0)
    suspend fun play() {
        stage.value = 1; kotlinx.coroutines.delay(180)
        stage.value = 2; kotlinx.coroutines.delay(200)
        stage.value = 0
    }
}

/** "dias seguidos", ou "semanas seguidas" com meta semanal. */
private fun unit(s: HabitState, sep: String): String {
    val n = s.streak()
    return when {
        s.weekly && n == 1 -> "semana${sep}seguida"
        s.weekly -> "semanas${sep}seguidas"
        n == 1 -> "dia${sep}seguido"
        else -> "dias${sep}seguidos"
    }
}

class HabitWidget : GlanceAppWidget() {
    // Recebe o tamanho real: a grade é desenhada na medida, sem sobrar faixa vazia.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = HabitStore.load(context)
        val density = context.resources.displayMetrics.density
        val wid = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(-1)
        provideContent {
            val stored by HabitStore.flow(context).collectAsState(initial)
            val opts = stored.widgets[wid] ?: WidgetOpts()
            // As semanas deste widget valem só para ele.
            val state = if (opts.weeks > 0) stored.copy(weeks = opts.weeks) else stored
            Cal.firstDow = if (state.mondayFirst) 1 else 0
            val pop by WidgetPulse.stage.collectAsState()
            val size = LocalSize.current
            val w = size.width.value
            val h = size.height.value
            val mode = when {
                w < 200f && h < 110f -> Mode.Tiny
                w < 200f -> Mode.Small
                h < 150f -> Mode.Mini
                h < 230f || w / h > 1.6f -> Mode.Side
                else -> Mode.Stacked
            }
            val (gw, gh) = when (mode) {
                // cabeçalho + fresta, fresta + régua + fresta + rodapé
                Mode.Stacked -> (w - PAD_X * 2) to (h - PAD_TOP - PAD_BOTTOM - HEAD_H - GAP * 3 - 1f - BTN)
                Mode.Side -> (w - PAD_X - 14f - LEFT_W - 14f) to (h - 28f)
                Mode.Mini -> (w - PAD_X * 2 - 12f - BTN) to (h - 24f)
                Mode.Small, Mode.Tiny -> 0f to 0f // sem a grade do ano
            }
            val layers = remember(state, size, pop) {
                if (gw > 0f) HeatmapRenderer.render(state, gw, gh, density, pop)
                else GridLayers(emptyList(), null, null)
            }
            CompositionLocalProvider(LocalLook provides lookFor(state, opts)) { Content(state, layers, mode) }
        }
    }

    @Composable
    private fun Content(state: HabitState, layers: GridLayers, mode: Mode) {
        // Recorte com o raio do próprio launcher (Pixel, One UI), concêntrico com a tela.
        val root = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(if (LocalLook.current.opts.glass) R.drawable.widget_bg_glass else R.drawable.widget_bg))
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
        val open = actionStartActivity<MainActivity>()
        val opts = LocalLook.current.opts

        when (mode) {
            Mode.Stacked -> Column(root.padding(start = PAD_X.dp, end = PAD_X.dp, top = PAD_TOP.dp, bottom = PAD_BOTTOM.dp)) {
                Row(GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.taskName.uppercase(), style = style(11, R.color.hb_ink, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    // Com meta semanal, o canto mostra o andamento da semana no lugar da data.
                    Text(
                        if (state.weekly) "${state.doneThisWeek} de ${state.goal} na semana" else state.today.format(DAY).replace(".", ""),
                        style = style(11, if (state.weekly && state.doneThisWeek >= state.goal) R.color.hb_done else R.color.hb_text2),
                    )
                }
                Spacer(GlanceModifier.height(GAP.dp))
                Grid(layers, GlanceModifier.fillMaxWidth().defaultWeight())
                Spacer(GlanceModifier.height(GAP.dp))
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(R.color.hb_rule))) {}
                Spacer(GlanceModifier.height(GAP.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Row(GlanceModifier.defaultWeight().clickable(open), verticalAlignment = Alignment.CenterVertically) { // duas linhas de 12sp centradas na altura das maiúsculas do número: as bases coincidem
                        if (opts.showStreak) {
                            Number(state)
                            Spacer(GlanceModifier.width(8.dp))
                            Text(unit(state, "\n"), style = style(12, R.color.hb_text2))
                        }
                    }
                    if (opts.bothButtons) { SkipButton(state); Spacer(GlanceModifier.width(6.dp)) }
                    DoneButton(state, label = true)
                }
            }
            Mode.Side -> Row(root.padding(start = PAD_X.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)) {
                Column(GlanceModifier.width(LEFT_W.dp).fillMaxHeight()) {
                    Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(open)) {
                        Text(state.taskName.uppercase(), style = style(11, R.color.hb_ink, FontWeight.Medium), maxLines = 1)
                        Spacer(GlanceModifier.height(10.dp))
                        if (opts.showStreak) {
                            Number(state)
                            Text(unit(state, "\n"), style = style(12, R.color.hb_text2), maxLines = 2)
                        }
                    }
                    Row {
                        if (opts.bothButtons) { SkipButton(state); Spacer(GlanceModifier.width(6.dp)) }
                        DoneButton(state, label = !opts.bothButtons, wide = !opts.bothButtons)
                    }
                }
                Spacer(GlanceModifier.width(14.dp))
                Grid(layers, GlanceModifier.defaultWeight().fillMaxHeight())
            }
            Mode.Mini -> Row(root.padding(horizontal = PAD_X.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Grid(layers, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(12.dp))
                DoneButton(state, label = false)
            }
            Mode.Small -> Column(root.padding(14.dp)) {
                Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(open)) {
                    Text(state.taskName.uppercase(), style = style(11, R.color.hb_ink, FontWeight.Medium), maxLines = 1)
                    Spacer(GlanceModifier.height(4.dp))
                    if (opts.showStreak) Row(verticalAlignment = Alignment.CenterVertically) {
                        Number(state, 40)
                        Spacer(GlanceModifier.width(6.dp))
                        Text(unit(state, "\n"), style = style(11, R.color.hb_text2))
                    }
                }
                Week(state)
                Spacer(GlanceModifier.height(10.dp))
                DoneButton(state, label = true, wide = true)
            }
            Mode.Tiny -> Row(root.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(GlanceModifier.defaultWeight().clickable(open), verticalAlignment = Alignment.CenterVertically) {
                    Number(state, 34)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(unit(state, "\n"), style = style(11, R.color.hb_text2), maxLines = 2)
                }
                DoneButton(state, label = false)
            }
        }
    }

    /** Os últimos 7 dias numa faixa, no tom de cada um; o ponto marca "não fiz" e a régua de tinta, hoje. */
    @Composable
    private fun Week(state: HabitState) {
        val today = state.today
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val levels = Cal.levels(state, days.first(), today)
        // Sem espaçadores: uma linha do Glance aceita no máximo 10 filhos; a fresta vem do recuo de cada dia.
        Row(GlanceModifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Column(GlanceModifier.defaultWeight().padding(end = if (i < 6) 3.dp else 0.dp)) {
                    Box(
                        GlanceModifier.fillMaxWidth().height(18.dp).cornerRadius(2.dp).background(LocalLook.current.levels[levels[d] ?: 0]),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d in state.scars) Box(GlanceModifier.fillMaxWidth().height(2.dp).background(ColorProvider(R.color.hb_gold))) {}
                        else if (d in state.tropes) Unit
                        else if (state.markOf(d) == Mark.MISSED) Box(GlanceModifier.size(4.dp).cornerRadius(2.dp).background(ColorProvider(R.color.hb_miss))) {}
                        else if (state.isRest(d) && d != today) Box(GlanceModifier.width(8.dp).height(2.dp).background(ColorProvider(R.color.hb_text3))) {}
                    }
                    Spacer(GlanceModifier.height(3.dp))
                    Box(GlanceModifier.fillMaxWidth().height(2.dp).background(ColorProvider(if (d == today) R.color.hb_ink else android.R.color.transparent))) {}
                }
            }
        }
    }

    /** Camadas empilhadas, todas do mesmo tamanho: alinham pixel a pixel. Encostada à direita, onde está hoje. Tocar abre o app. */
    @Composable
    private fun Grid(layers: GridLayers, modifier: GlanceModifier) {
        Box(modifier.clickable(actionStartActivity<MainActivity>()), contentAlignment = Alignment.CenterEnd) {
            val look = LocalLook.current
            layers.levels.forEachIndexed { i, bmp -> if (bmp != null) Layer(bmp, look.levels[i]) }
            layers.rest?.let { Layer(it, ColorProvider(R.color.hb_text3)) }
            layers.trope?.let { Layer(it, ColorProvider(R.color.hb_text3)) }
            layers.scar?.let { Layer(it, ColorProvider(R.color.hb_gold)) }
            layers.missed?.let { Layer(it, ColorProvider(R.color.hb_miss)) }
            layers.ring?.let { Layer(it, ColorProvider(R.color.hb_ink)) }
            layers.pop?.let { Layer(it, look.done) }
        }
    }

    @Composable
    private fun Layer(bmp: android.graphics.Bitmap, color: androidx.glance.unit.ColorProvider) {
        Image(
            ImageProvider(bmp), null, GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color),
        )
    }

    /** A sequência em condensada, como o herói do app; em dia de marco, o número fica dourado. */
    @Composable
    private fun Number(state: HabitState, size: Int = 44) {
        val streak = state.streak()
        val milestone = isMilestone(streak, state.weekly) && state.markOf(state.today) == Mark.DONE
        Text(
            "$streak",
            style = TextStyle(
                color = ColorProvider(if (milestone) R.color.hb_gold else R.color.hb_ink),
                fontSize = size.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED,
            ),
            maxLines = 1,
        )
    }

    /** "Marcar" em tinta; feito vira verde ("Feito"). No 4×2 e no mini, só o ✓; no 2×2, ocupa a largura. */
    @Composable
    private fun DoneButton(state: HabitState, label: Boolean, wide: Boolean = false) {
        val done = state.markOf(state.today) == Mark.DONE
        val look = LocalLook.current
        val tint = if (done) look.onDone else ColorProvider(R.color.hb_bg)
        Row(
            GlanceModifier.height(BTN.dp)
                .then(if (wide) GlanceModifier.fillMaxWidth() else if (label) GlanceModifier.padding(start = 12.dp, end = 14.dp) else GlanceModifier.width(BTN.dp))
                .then(if (done) GlanceModifier.background(look.done).cornerRadius(3.dp) else GlanceModifier.background(ImageProvider(R.drawable.btn_ink)))
                .clickable(actionRunCallback<ToggleTodayAction>(), rippleOverride = R.drawable.ripple_rect),
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(R.drawable.ic_check), if (done) "Desmarcar hoje" else "Marcar hoje", GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(tint))
            if (label) {
                Spacer(GlanceModifier.width(6.dp))
                Text(if (done) "Feito" else "Marcar", style = TextStyle(color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
        }
    }

    /** ✕ de contorno; escolhido, ganha borda de tinta e o ✕ fica vermelho. */
    @Composable
    private fun SkipButton(state: HabitState) {
        val missed = state.markOf(state.today) == Mark.MISSED
        Box(
            GlanceModifier.size(BTN.dp)
                .background(ImageProvider(if (missed) R.drawable.btn_missed else R.drawable.btn_outline))
                .clickable(actionRunCallback<SkipTodayAction>(), rippleOverride = R.drawable.ripple_rect),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_x), "Não fiz hoje", GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(ColorProvider(if (missed) R.color.hb_miss else R.color.hb_text2)))
        }
    }

    private fun style(size: Int, color: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = weight)
}

/** Alterna hoje entre feito e vazio. Roda em segundo plano, sem abrir o app. */
class ToggleTodayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val before = HabitStore.load(context).streak()
        val now = HabitStore.toggleToday(context, Mark.DONE)
        val s = HabitStore.load(context)
        val after = s.streak()
        if (now == Mark.DONE && after > before && isMilestone(after, s.weekly)) Haptics.milestone(context) else Haptics.mark(context, now == Mark.DONE)
        Reminder.dismiss(context)
        HabitWidget().updateAll(context) // garante a sessão do widget viva para os quadros
        if (now == Mark.DONE) WidgetPulse.play()
    }
}

/** Alterna hoje entre "não fiz" e vazio. */
class SkipTodayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        HabitStore.toggleToday(context, Mark.MISSED)
        Haptics.mark(context, done = false)
        HabitWidget().updateAll(context)
        Reminder.dismiss(context)
    }
}

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        runBlocking { appWidgetIds.forEach { HabitStore.removeWidget(context, it) } }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val s = runBlocking { HabitStore.load(context) }
        Rollover.schedule(context, s.rolloverHour)
        Reminder.schedule(context, s)
    }
}

/** O mesmo widget, apresentado no seletor como compacto (2×2). */
class HabitWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        runBlocking { appWidgetIds.forEach { HabitStore.removeWidget(context, it) } }
    }
}
