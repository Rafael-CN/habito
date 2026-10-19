package com.rafael.habito

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Semanas do gráfico anual no app (o perfil do GitHub mostra 53). */
const val YEAR_WEEKS = 53

/**
 * Tudo o que a tela mostra, já calculado. Recalculado só quando os dados mudam
 * (ou o dia vira), nunca a cada recomposição ou quadro de animação.
 */
@Immutable
class HabitUi(
    val state: HabitState,
    val today: LocalDate,
    val start: LocalDate,              // domingo da primeira coluna do ano
    val levels: Map<LocalDate, Int>,   // tom 0..4 de cada dia do ano
    val streak: Int,
    val best: Int,
    val pct30: Int,
    val year: Int,
    val milestones: Map<LocalDate, Int>, // dia → marco alcançado (dias: 7, 30, 100, 365; semanas: 4, 12, 26, 52)
) {
    val todayMark: Mark? get() = state.markOf(today)
    val empty: Boolean get() = state.done.isEmpty() && state.missed.isEmpty()
    val weekly: Boolean get() = state.weekly
    val goal: Int get() = state.goal
    val doneThisWeek: Int get() = state.doneThisWeek
    fun markOf(d: LocalDate): Mark? = state.markOf(d)
    fun level(d: LocalDate): Int = levels[d] ?: 0

    companion object {
        fun from(s: HabitState): HabitUi {
            Cal.firstDow = if (s.mondayFirst) 1 else 0
            val today = s.today
            val start = Cal.start(today, YEAR_WEEKS)
            return HabitUi(
                state = s, today = today, start = start,
                levels = Cal.levels(s, start, today),
                streak = s.streak(), best = s.best(),
                pct30 = s.doneInLast(30) * 100 / 30, year = s.doneInLast(365),
                milestones = if (s.weekly) Cal.weekMilestones(s) else Cal.milestones(s),
            )
        }
    }
}

/** Dono do estado da tela e das ações. Toda gravação também atualiza o widget e os agendamentos. */
class HabitViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val dayTick = MutableStateFlow(0)

    val ui: StateFlow<HabitUi?> = combine(HabitStore.flow(ctx), dayTick) { s, _ -> HabitUi.from(s) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Ao voltar para o app: se o dia virou com a tela aberta, recalcula. */
    fun refresh() {
        val cur = ui.value ?: return
        if (cur.today != cur.state.today) dayTick.value++
    }

    fun setMark(date: LocalDate, mark: Mark?) = viewModelScope.launch {
        HabitStore.setMark(ctx, date, mark)
        if (date == ui.value?.today) Reminder.dismiss(ctx)
        HabitWidget().updateAll(ctx)
    }

    fun setName(name: String) = save { HabitStore.setName(ctx, name) }
    fun setHour(hour: Int) = save { HabitStore.setHour(ctx, hour); Rollover.schedule(ctx, hour) }
    fun setWeeks(weeks: Int) = save { HabitStore.setWeeks(ctx, weeks) }
    fun setReminders(list: List<Rem>) = save { HabitStore.setReminders(ctx, list); Reminder.schedule(ctx, HabitStore.load(ctx)) }
    fun setRest(days: Set<Int>) = save { HabitStore.setRest(ctx, days) }
    fun setPalette(name: String) = save { HabitStore.setPalette(ctx, name) } // o ícone troca quando o app sai de cena (MainActivity.onStop)
    fun setPaper(on: Boolean) = save { HabitStore.setPaper(ctx, on) }
    fun setIconFollows(on: Boolean) = save { HabitStore.setIconFollows(ctx, on) }
    fun setTheme(mode: Int) = save { HabitStore.setTheme(ctx, mode) }
    fun setMondayFirst(on: Boolean) = save { HabitStore.setMondayFirst(ctx, on) }
    fun setHaptics(on: Boolean) = save { HabitStore.setHaptics(ctx, on) }
    fun setCelebrate(on: Boolean) = save { HabitStore.setCelebrate(ctx, on) }
    fun setSounds(on: Boolean) = save { HabitStore.setSounds(ctx, on) }
    fun setGoal(goal: Int) = save { HabitStore.setGoal(ctx, goal) }
    fun setRetroSeen(month: java.time.YearMonth) = viewModelScope.launch { HabitStore.setRetroSeen(ctx, month.toString()) }

    fun sealLetter(forDay: Int, text: String) = viewModelScope.launch { HabitStore.sealLetter(ctx, forDay, text) }
    fun skipLetter() = viewModelScope.launch { HabitStore.setLetterAsked(ctx) }
    /** Abre a frase do marco e, se veio uma nova, lacra para o dia 100. */
    fun closeLetter(l: Letter, next: String?) = viewModelScope.launch {
        HabitStore.openLetter(ctx, l)
        if (!next.isNullOrBlank()) HabitStore.sealLetter(ctx, 100, next)
    }

    /** Frequência da cópia automática; a pasta vem do seletor do sistema. */
    fun setBackup(freq: Int, tree: Uri? = null) = viewModelScope.launch {
        val s = HabitStore.load(ctx)
        val uri = tree?.toString() ?: s.backupUri
        tree?.let { AutoBackup.keep(ctx, it) }
        HabitStore.setBackup(ctx, freq, uri)
        AutoBackup.schedule(ctx, HabitStore.load(ctx))
        if (tree != null) AutoBackup.write(ctx)
    }
    suspend fun backupNow(): Boolean = AutoBackup.write(ctx)
    fun dismissBackupNudge() = viewModelScope.launch { HabitStore.setBackupNudge(ctx, System.currentTimeMillis()) }

    suspend fun export(uri: Uri) = Backup.export(ctx, uri)

    suspend fun peekImport(uri: Uri): Backup.Preview = Backup.peek(ctx, uri)

    /** Substitui tudo pelo backup e devolve o estado anterior, para o desfazer. */
    suspend fun applyImport(s: HabitState): HabitState {
        val before = HabitStore.load(ctx)
        HabitStore.replaceAll(ctx, s)
        HabitWidget().updateAll(ctx)
        Reminder.schedule(ctx, HabitStore.load(ctx))
        return before
    }

    private fun save(block: suspend () -> Unit) = viewModelScope.launch { block(); HabitWidget().updateAll(ctx) }
}
