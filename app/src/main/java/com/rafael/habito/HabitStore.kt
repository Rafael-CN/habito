package com.rafael.habito

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

private val Context.store by preferencesDataStore("habito")

enum class Mark { DONE, MISSED }

/** Um lembrete: minutos desde a meia-noite e se só avisa quando o dia ainda está pendente. */
data class Rem(val minutes: Int, val onlyPending: Boolean = true) {
    val label: String get() = "%02d:%02d".format(minutes / 60, minutes % 60)
}

/** Como cada widget se mostra; configurado no próprio widget (tocar e segurar → Configurar). */
data class WidgetOpts(val weeks: Int = 0, val showStreak: Boolean = true, val bothButtons: Boolean = true, val glass: Boolean = false) {
    fun encode() = "$weeks:${if (showStreak) 1 else 0}:${if (bothButtons) 1 else 0}:${if (glass) 1 else 0}"
    companion object {
        fun decode(s: String?): WidgetOpts = s?.split(':')?.takeIf { it.size == 4 }?.let {
            WidgetOpts(it[0].toIntOrNull() ?: 0, it[1] == "1", it[2] == "1", it[3] == "1")
        } ?: WidgetOpts()
    }
}

/** Uma sequência do histórico: [days] feitos de [start] a [end], depois de [gapBefore] dias parados. */
data class Run(val start: LocalDate, val end: LocalDate, val days: Int, val gapBefore: Int)

/** Dias mínimos entre dois tropeços. */
const val TROPE_GAP = 7

/** Quebras mais longas que isso não ganham costura: viram pausa, não cicatriz. */
const val SCAR_MAX = 14

/** Uma frase lacrada no dia 1, aberta quando uma sequência chega a [forDay]. */
data class Letter(val forDay: Int, val text: String, val written: LocalDate, val opened: LocalDate? = null) {
    companion object {
        private const val F = '\u001F'
        private const val R = '\u001E'
        fun encode(list: List<Letter>) = list.joinToString(R.toString()) { "${it.forDay}$F${it.written}$F${it.opened ?: ""}$F${it.text}" }
        fun decode(s: String?): List<Letter> = s.orEmpty().split(R).mapNotNull { p ->
            val f = p.split(F, limit = 4).takeIf { it.size == 4 } ?: return@mapNotNull null
            val day = f[0].toIntOrNull() ?: return@mapNotNull null
            runCatching { Letter(day, f[3], LocalDate.parse(f[1]), f[2].takeIf { it.isNotEmpty() }?.let(LocalDate::parse)) }.getOrNull()
        }
    }
}

/** Dia da semana como a grade conta: 0 = domingo … 6 = sábado. */
val LocalDate.dow: Int get() = dayOfWeek.value % 7

data class HabitState(
    val done: Set<LocalDate> = emptySet(),
    val missed: Set<LocalDate> = emptySet(),
    val rolloverHour: Int = 0,
    val taskName: String = HabitStore.DEFAULT_NAME,
    val weeks: Int = 0,         // 0 = automático (cabe no widget)
    val reminderHour: Int = -1, // -1 = sem lembrete
    val doneAt: Long = 0L,      // quando hoje foi marcado como feito (epoch ms); 0 = não marcado
    val goal: Int = 0,          // 0 = todo dia; 3..6 = vezes por semana (semana de domingo a sábado, como a grade)
    val retroSeen: String = "", // último mês (yyyy-MM) cuja retrospectiva já foi vista
    val rest: Set<Int> = emptySet(),          // dias de descanso (0 = domingo); não quebram a sequência diária
    val restHistory: List<Pair<LocalDate, Set<Int>>> = emptyList(), // regra de descanso com data: cada dia usa a que valia nele
    val reminders: List<Rem> = emptyList(),   // até três, em qualquer horário
    val palette: String = "verde",            // cor do mapa
    val themeMode: Int = 0,                   // 0 = sistema, 1 = claro, 2 = escuro
    val mondayFirst: Boolean = false,         // a semana começa na segunda
    val haptics: Boolean = true,
    val celebrate: Boolean = true,
    val widgets: Map<Int, WidgetOpts> = emptyMap(), // por appWidgetId
    val letters: List<Letter> = emptyList(),  // frases do dia 1 (lacradas até o marco)
    val letterAsked: Boolean = false,         // a pergunta do dia 1 já apareceu
    val backupFreq: Int = 0,                  // 0 = desligado, 1 = todo dia, 2 = toda semana, 3 = todo mês
    val backupUri: String = "",               // pasta escolhida (árvore SAF)
    val backupAt: Long = 0L,                  // última cópia (epoch ms)
    val backupNudge: Long = 0L,               // quando o aviso de cópia atrasada apareceu no Hoje
) {
    /** A próxima frase ainda lacrada. */
    val sealed: Letter? get() = letters.firstOrNull { it.opened == null }

    /** A frase que abre agora: a sequência chegou ao dia dela, com hoje feito. */
    val letterToOpen: Letter? get() = sealed?.takeIf { !weekly && today in done && currentStreak() >= it.forDay }

    /**
     * Hoje pelo relógio, com uma correção de viagem: se o dia seguinte já está feito (marcado num fuso adiante,
     * e o voo voltou o relógio), hoje é esse dia, para a sequência não quebrar nem contar duas vezes.
     */
    val today: LocalDate get() = HabitStore.today(rolloverHour).let { t -> if (t.plusDays(1) in done) t.plusDays(1) else t }

    /** Os dias de descanso que valiam em [d]: mudar a regra hoje não reescreve o passado. */
    fun restAt(d: LocalDate): Set<Int> = restHistory.lastOrNull { !it.first.isAfter(d) }?.second ?: rest
    val weekly: Boolean get() = goal > 0
    private val firstDow: Int get() = if (mondayFirst) 1 else 0

    /** Dia de descanso que não foi feito: a sequência atravessa sem contar nem quebrar. */
    fun isRest(d: LocalDate): Boolean = !weekly && d !in done && d.dow in restAt(d)

    /**
     * Tropeços ("nunca duas vezes"): um único dia sem fazer entre dois feitos não quebra a sequência.
     * Dois seguidos quebram. No máximo um a cada [TROPE_GAP] dias, para um dia sim, um não, não virar sequência.
     */
    val tropes: Set<LocalDate> by lazy { findTropes() }

    /** Dia que a sequência atravessa sem contar: descanso ou tropeço. */
    fun bridges(d: LocalDate): Boolean = isRest(d) || d in tropes

    /** Dia anterior que conta (pula o descanso). */
    private fun prevCounted(d: LocalDate): LocalDate { var x = d.minusDays(1); var g = 0; while (isRest(x) && g++ < 7) x = x.minusDays(1); return x }
    private fun nextCounted(d: LocalDate): LocalDate { var x = d.plusDays(1); var g = 0; while (isRest(x) && g++ < 7) x = x.plusDays(1); return x }

    private fun tropeAllowed(d: LocalDate, last: LocalDate?) = last == null || java.time.temporal.ChronoUnit.DAYS.between(last, d) >= TROPE_GAP

    private fun findTropes(): Set<LocalDate> {
        if (weekly) return emptySet()
        val first = done.minOrNull() ?: return emptySet()
        val out = LinkedHashSet<LocalDate>()
        var prevDone = false
        var last: LocalDate? = null
        var d = first
        while (d.isBefore(today)) { // hoje ainda está em aberto
            when {
                d in done -> prevDone = true
                isRest(d) -> Unit
                else -> {
                    val next = nextCounted(d)
                    if (prevDone && !next.isAfter(today) && next in done && tropeAllowed(d, last)) { out += d; last = d }
                    else prevDone = false
                }
            }
            d = d.plusDays(1)
        }
        return out
    }

    /** Ontem ficou vazio e hoje ainda dá para voltar: a sequência segue viva, esperando por hoje. */
    val pendingTrope: Boolean by lazy {
        if (weekly || today in done || today in missed) return@lazy false
        val y = prevCounted(today)
        y !in done && prevCounted(y) in done && tropeAllowed(y, tropes.lastOrNull())
    }

    /** O dia vazio que [pendingTrope] está segurando (ontem, ou antes do descanso). */
    val pendingTropeDay: LocalDate? get() = if (pendingTrope) prevCounted(today) else null

    /** Sequências do histórico, da mais antiga à atual. Uma volta é cada sequência depois da primeira. */
    val runs: List<Run> by lazy { findRuns() }

    private fun findRuns(): List<Run> {
        if (weekly) return emptyList()
        val first = done.minOrNull() ?: return emptyList()
        val out = ArrayList<Run>()
        var start: LocalDate? = null; var end = first; var n = 0; var gap = 0
        val hold = pendingTropeDay
        var d = first
        while (!d.isAfter(today)) {
            when {
                d in done -> { if (start == null) { start = d; n = 0 }; n++; end = d }
                bridges(d) || d == hold || d == today -> Unit
                else -> { if (start != null) { out += Run(start, end, n, gap); start = null; gap = 0 }; gap++ }
            }
            d = d.plusDays(1)
        }
        if (start != null) out += Run(start, end, n, gap)
        return out
    }

    /** Lacunas costuradas: os dias de uma quebra que veio depois de uma sequência com marco, quando já houve volta. */
    val scars: Set<LocalDate> by lazy {
        val out = HashSet<LocalDate>()
        runs.zipWithNext().forEach { (a, b) ->
            if (a.days >= MILESTONES.min() && b.gapBefore <= SCAR_MAX) {
                var d = a.end.plusDays(1)
                while (d.isBefore(b.start)) { if (!isRest(d)) out += d; d = d.plusDays(1) }
            }
        }
        out
    }

    /** A sequência atual é uma volta (houve outra antes) e ainda não passou a anterior. */
    val inReturn: Boolean get() {
        if (weekly || runs.isEmpty()) return false
        val s = currentStreak()
        return if (s == 0) true else runs.size >= 2 && s < runs[runs.size - 2].days
    }

    /** Ordinal da volta atual (a primeira sequência não é volta). */
    val returnNumber: Int get() = if (currentStreak() == 0) runs.size else runs.size - 1

    /** Tamanho da sequência anterior à volta atual. */
    val lastRunDays: Int get() = if (currentStreak() == 0) runs.lastOrNull()?.days ?: 0 else runs.getOrNull(runs.size - 2)?.days ?: 0

    /** Voltas no último ano e o tempo médio (em dias) até voltar. */
    fun returnsIn(days: Int): Pair<Int, Double> {
        val since = today.minusDays(days.toLong())
        val r = runs.drop(1).filter { it.start.isAfter(since) }
        return r.size to (if (r.isEmpty()) 0.0 else r.map { it.gapBefore }.average())
    }

    /** Primeiro dia da semana de [d] (domingo ou segunda). */
    fun weekStart(d: LocalDate): LocalDate = d.minusDays(((d.dow - firstDow + 7) % 7).toLong())

    /** Dias feitos na semana que começa em [start] (sem contar o futuro). */
    fun doneInWeek(start: LocalDate): Int = (0..6).count { val d = start.plusDays(it.toLong()); d in done && !d.isAfter(today) }

    /** Feitos na semana de hoje. */
    val doneThisWeek: Int get() = doneInWeek(weekStart(today))

    /** Semanas seguidas com a meta cumprida. A semana atual só conta quando cumprida, e ainda em aberto não quebra. */
    fun weekStreak(): Int {
        var ws = weekStart(today)
        var n = if (doneInWeek(ws) >= goal) 1 else 0
        ws = ws.minusWeeks(1)
        while (doneInWeek(ws) >= goal) { n++; ws = ws.minusWeeks(1) }
        return n
    }

    fun bestWeekStreak(): Int {
        var best = 0; var run = 0
        var ws = weekStart(today).minusWeeks(52)
        while (!ws.isAfter(today)) {
            if (doneInWeek(ws) >= goal) { run++; if (run > best) best = run } else if (ws != weekStart(today)) run = 0
            ws = ws.plusWeeks(1)
        }
        return best
    }

    /** A sequência que o app mostra: dias, ou semanas com a meta semanal ligada. */
    fun streak(): Int = if (weekly) weekStreak() else currentStreak()
    fun best(): Int = if (weekly) bestWeekStreak() else bestStreak()

    fun markOf(d: LocalDate): Mark? = when (d) {
        in done -> Mark.DONE
        in missed -> Mark.MISSED
        else -> null
    }

    /** Hoje pendente não quebra a sequência; descanso e tropeço são atravessados; ontem vazio ainda espera por hoje. */
    fun currentStreak(): Int = when {
        today in done -> countBack(today)
        pendingTrope -> countBack(prevCounted(today).minusDays(1))
        else -> countBack(today.minusDays(1))
    }

    /** Tamanho da sequência que termina em [d] (0 se o dia não foi feito). */
    fun streakEndingAt(d: LocalDate): Int = if (d in done) countBack(d) else 0

    private fun countBack(from: LocalDate): Int {
        var d = from; var n = 0; var guard = 0
        while (guard++ < 4000) {
            when {
                d in done -> n++
                bridges(d) -> Unit
                else -> break
            }
            d = d.minusDays(1)
        }
        return n
    }

    fun bestStreak(): Int {
        var best = 0; var run = 0
        var d = today.minusDays(364)
        while (!d.isAfter(today)) {
            if (d in done) { run++; if (run > best) best = run } else if (!bridges(d)) run = 0
            d = d.plusDays(1)
        }
        return best
    }

    fun doneInLast(days: Int): Int = done.count { it.isAfter(today.minusDays(days.toLong())) && !it.isAfter(today) }
}

object HabitStore {
    private val DONE = stringSetPreferencesKey("done")
    private val MISSED = stringSetPreferencesKey("missed")
    private val HOUR = intPreferencesKey("rollover_hour")
    private val NAME = stringPreferencesKey("task_name")
    private val WEEKS = intPreferencesKey("weeks")
    private val REMINDER = intPreferencesKey("reminder_hour")
    private val DONE_AT = androidx.datastore.preferences.core.longPreferencesKey("done_at")
    private val GOAL = intPreferencesKey("week_goal")
    private val RETRO = stringPreferencesKey("retro_seen")
    private val REST = stringSetPreferencesKey("rest_days")
    private val REST_HISTORY = stringPreferencesKey("rest_history") // "1970-01-01=0;2026-10-18=0,6"
    /** Antes de qualquer mudança registrada: a regra antiga vale para todo o passado. */
    val EPOCH: LocalDate = LocalDate.of(1970, 1, 1)

    fun encodeRestHistory(h: List<Pair<LocalDate, Set<Int>>>): String = h.joinToString(";") { (d, s) -> "$d=" + s.sorted().joinToString(",") }
    fun decodeRestHistory(s: String?): List<Pair<LocalDate, Set<Int>>> = s.orEmpty().split(';').mapNotNull { p ->
        val (d, days) = p.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        runCatching { LocalDate.parse(d) to days.split(',').mapNotNull { it.toIntOrNull() }.toSet() }.getOrNull()
    }.sortedBy { it.first }

    /** Nova regra a partir de [from]; a primeira mudança guarda a regra antiga como a do passado inteiro. */
    fun withRestChange(history: List<Pair<LocalDate, Set<Int>>>, old: Set<Int>, new: Set<Int>, from: LocalDate): List<Pair<LocalDate, Set<Int>>> {
        val base = history.ifEmpty { listOf(EPOCH to old) }
        return (base.filter { it.first.isBefore(from) } + (from to new)).sortedBy { it.first }
    }
    private val REMS = stringPreferencesKey("reminders")     // "540:1,1260:0"
    private val PALETTE = stringPreferencesKey("palette")
    private val THEME = intPreferencesKey("theme_mode")
    private val MONDAY = androidx.datastore.preferences.core.booleanPreferencesKey("monday_first")
    private val HAPTICS = androidx.datastore.preferences.core.booleanPreferencesKey("haptics")
    private val CELEBRATE = androidx.datastore.preferences.core.booleanPreferencesKey("celebrate")
    private val LETTERS = stringPreferencesKey("letters")
    private val LETTER_ASKED = androidx.datastore.preferences.core.booleanPreferencesKey("letter_asked")
    private val BACKUP_FREQ = intPreferencesKey("backup_freq")
    private val BACKUP_URI = stringPreferencesKey("backup_uri")
    private val BACKUP_AT = androidx.datastore.preferences.core.longPreferencesKey("backup_at")
    private val BACKUP_NUDGE = androidx.datastore.preferences.core.longPreferencesKey("backup_nudge")
    const val MAX_REMINDERS = 3
    const val MAX_REST = 3

    fun encodeRems(r: List<Rem>): String = r.joinToString(",") { "${it.minutes}:${if (it.onlyPending) 1 else 0}" }
    fun decodeRems(s: String?): List<Rem> = s.orEmpty().split(',').mapNotNull { p ->
        val (m, o) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        m.toIntOrNull()?.takeIf { it in 0 until 1440 }?.let { Rem(it, o == "1") }
    }.sortedBy { it.minutes }

    const val DEFAULT_NAME = "Estudar 30 min"
    val WEEK_OPTIONS = listOf(0, 12, 18, 26)
    val HOUR_OPTIONS = listOf(0, 3, 5)
    val REMINDER_OPTIONS = listOf(-1, 20, 21, 22)
    val GOAL_OPTIONS = listOf(0, 3, 4, 5, 6)

    /** Trocável nos testes. */
    var clock: Clock = Clock.systemDefaultZone()

    fun today(rolloverHour: Int): LocalDate =
        LocalDateTime.now(clock).minusHours(rolloverHour.toLong()).toLocalDate()

    private fun Preferences.toState() = HabitState(
        done = this[DONE].orEmpty().map(LocalDate::parse).toSet(),
        missed = this[MISSED].orEmpty().map(LocalDate::parse).toSet(),
        rolloverHour = this[HOUR] ?: 0,
        taskName = this[NAME] ?: DEFAULT_NAME,
        weeks = this[WEEKS] ?: 0,
        reminderHour = this[REMINDER] ?: -1,
        doneAt = this[DONE_AT] ?: 0L,
        goal = this[GOAL] ?: 0,
        retroSeen = this[RETRO] ?: "",
        rest = this[REST].orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
        restHistory = decodeRestHistory(this[REST_HISTORY]),
        // Quem vinha do lembrete único (só a hora) ganha a mesma hora como primeiro lembrete.
        reminders = if (this[REMS] != null) decodeRems(this[REMS]) else (this[REMINDER] ?: -1).let { if (it >= 0) listOf(Rem(it * 60)) else emptyList() },
        palette = this[PALETTE] ?: "verde",
        themeMode = this[THEME] ?: 0,
        mondayFirst = this[MONDAY] ?: false,
        haptics = this[HAPTICS] ?: true,
        celebrate = this[CELEBRATE] ?: true,
        widgets = asMap().entries.filter { it.key.name.startsWith("w_") }.mapNotNull { e -> e.key.name.removePrefix("w_").toIntOrNull()?.let { it to WidgetOpts.decode(e.value as? String) } }.toMap(),
        letters = Letter.decode(this[LETTERS]),
        letterAsked = this[LETTER_ASKED] ?: false,
        backupFreq = this[BACKUP_FREQ] ?: 0,
        backupUri = this[BACKUP_URI] ?: "",
        backupAt = this[BACKUP_AT] ?: 0L,
        backupNudge = this[BACKUP_NUDGE] ?: 0L,
    )

    suspend fun load(ctx: Context): HabitState = ctx.store.data.first().toState()

    /** O widget e o app observam este fluxo; qualquer gravação redesenha os dois. */
    fun flow(ctx: Context): Flow<HabitState> = ctx.store.data.map { it.toState() }.distinctUntilChanged()

    suspend fun setName(ctx: Context, name: String) = ctx.store.edit { it[NAME] = name.trim().ifEmpty { DEFAULT_NAME } }
    suspend fun setHour(ctx: Context, hour: Int) = ctx.store.edit { it[HOUR] = hour }
    suspend fun setWeeks(ctx: Context, weeks: Int) = ctx.store.edit { it[WEEKS] = weeks }
    suspend fun setReminder(ctx: Context, hour: Int) = ctx.store.edit { it[REMINDER] = hour }
    suspend fun setGoal(ctx: Context, goal: Int) = ctx.store.edit { it[GOAL] = goal }
    suspend fun setRetroSeen(ctx: Context, month: String) = ctx.store.edit { it[RETRO] = month }
    suspend fun setRest(ctx: Context, days: Set<Int>) = ctx.store.edit { p ->
        val new = days.take(MAX_REST).toSet()
        val old = p[REST].orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        p[REST_HISTORY] = encodeRestHistory(withRestChange(decodeRestHistory(p[REST_HISTORY]), old, new, today(p[HOUR] ?: 0)))
        p[REST] = new.map(Int::toString).toSet()
    }
    suspend fun setReminders(ctx: Context, r: List<Rem>) = ctx.store.edit { it[REMS] = encodeRems(r.take(MAX_REMINDERS).sortedBy(Rem::minutes)) }
    suspend fun setPalette(ctx: Context, name: String) = ctx.store.edit { it[PALETTE] = name }
    suspend fun setTheme(ctx: Context, mode: Int) = ctx.store.edit { it[THEME] = mode }
    suspend fun setMondayFirst(ctx: Context, on: Boolean) = ctx.store.edit { it[MONDAY] = on }
    suspend fun setHaptics(ctx: Context, on: Boolean) = ctx.store.edit { it[HAPTICS] = on }
    suspend fun setCelebrate(ctx: Context, on: Boolean) = ctx.store.edit { it[CELEBRATE] = on }
    suspend fun setWidget(ctx: Context, id: Int, o: WidgetOpts) = ctx.store.edit { it[stringPreferencesKey("w_$id")] = o.encode() }
    suspend fun removeWidget(ctx: Context, id: Int) = ctx.store.edit { it.remove(stringPreferencesKey("w_$id")) }

    /** Guarda uma frase lacrada para o dia [forDay] (substitui a lacrada anterior, se houver). */
    suspend fun sealLetter(ctx: Context, forDay: Int, text: String) = ctx.store.edit { p ->
        val list = Letter.decode(p[LETTERS]).filter { it.opened != null }
        p[LETTERS] = Letter.encode(if (text.isBlank()) list else list + Letter(forDay, text.trim().take(140), today(p[HOUR] ?: 0)))
        p[LETTER_ASKED] = true
    }
    suspend fun openLetter(ctx: Context, l: Letter) = ctx.store.edit { p ->
        p[LETTERS] = Letter.encode(Letter.decode(p[LETTERS]).map { if (it == l) it.copy(opened = today(p[HOUR] ?: 0)) else it })
    }
    suspend fun setLetterAsked(ctx: Context) = ctx.store.edit { it[LETTER_ASKED] = true }
    suspend fun setBackup(ctx: Context, freq: Int, uri: String) = ctx.store.edit { it[BACKUP_FREQ] = freq; it[BACKUP_URI] = uri }
    suspend fun setBackupAt(ctx: Context, at: Long) = ctx.store.edit { it[BACKUP_AT] = at }
    suspend fun setBackupNudge(ctx: Context, at: Long) = ctx.store.edit { it[BACKUP_NUDGE] = at }

    /** Define a marca de um dia. `mark = null` limpa. */
    suspend fun setMark(ctx: Context, date: LocalDate, mark: Mark?) {
        val key = date.toString()
        ctx.store.edit { p ->
            p[DONE] = p[DONE].orEmpty() - key
            p[MISSED] = p[MISSED].orEmpty() - key
            if (date == today(p[HOUR] ?: 0)) p[DONE_AT] = if (mark == Mark.DONE) System.currentTimeMillis() else 0L
            when (mark) {
                Mark.DONE -> p[DONE] = p[DONE].orEmpty() + key
                Mark.MISSED -> p[MISSED] = p[MISSED].orEmpty() + key
                null -> Unit
            }
        }
        HabitTileService.poke(ctx) // o bloco rápido acompanha qualquer marcação
    }

    /** Alterna o dia de hoje entre `mark` e vazio. Devolve a marca que ficou. */
    suspend fun toggleToday(ctx: Context, mark: Mark): Mark? {
        val s = load(ctx)
        val next = if (s.markOf(s.today) == mark) null else mark
        setMark(ctx, s.today, next)
        return next
    }

    /** Substitui tudo (restauração de backup). */
    suspend fun replaceAll(ctx: Context, s: HabitState) {
        ctx.store.edit { p ->
            p[DONE] = s.done.map(LocalDate::toString).toSet()
            p[MISSED] = s.missed.map(LocalDate::toString).toSet()
            p[NAME] = s.taskName
            p[HOUR] = s.rolloverHour
            p[WEEKS] = s.weeks
            p[REMINDER] = s.reminderHour
            p[GOAL] = s.goal
            p[REST] = s.rest.map(Int::toString).toSet()
            p[REST_HISTORY] = encodeRestHistory(s.restHistory)
            p[REMS] = encodeRems(s.reminders)
            p[PALETTE] = s.palette
            p[THEME] = s.themeMode
            p[MONDAY] = s.mondayFirst
            p[HAPTICS] = s.haptics
            p[CELEBRATE] = s.celebrate
            p[LETTERS] = Letter.encode(s.letters)
            if (s.letters.isNotEmpty()) p[LETTER_ASKED] = true
        }
    }

    /**
     * Cenários da 3.3 (só build debug): "volta" = 41 dias com marcos e um tropeço, quebra de 2, volta de 17;
     * "carta" = 29 dias com frase lacrada para o 30 (marcar hoje abre); "tropeco" = ontem vazio, hoje esperando;
     * "novo" = tudo vazio, com a pergunta do dia 1.
     */
    suspend fun seed33(ctx: Context, kind: String) {
        ctx.store.edit { p ->
            val t = today(p[HOUR] ?: 0)
            fun back(r: IntRange) = r.map { t.minusDays(it.toLong()).toString() }.toSet()
            p[MISSED] = emptySet(); p.remove(LETTERS); p[LETTER_ASKED] = true; p[BACKUP_AT] = 0L; p[BACKUP_NUDGE] = 0L
            when (kind) {
                "volta" -> { p[DONE] = back(20..60) - t.minusDays(45).toString() + back(1..17); p[MISSED] = setOf(t.minusDays(19).toString()) }
                "carta" -> { p[DONE] = back(1..29); p[LETTERS] = Letter.encode(listOf(Letter(30, "Quero chegar em março lendo sem esforço.", t.minusDays(29)))) }
                "tropeco" -> p[DONE] = back(2..12)
                "novo" -> { p[DONE] = emptySet(); p[LETTER_ASKED] = false }
            }
        }
    }

    /** Histórico de exemplo (só build debug): ~62% feito, últimos 6 dias seguidos, hoje pendente (marcar = marco de 7). */
    suspend fun seedDemo(ctx: Context) {
        ctx.store.edit { p ->
            val t = today(p[HOUR] ?: 0)
            val done = mutableSetOf<String>(); val missed = mutableSetOf<String>()
            for (i in 1..370) {
                val d = t.minusDays(i.toLong()).toString()
                val r = kotlin.math.abs(kotlin.math.sin(i * 12.9898) * 43758.5453) % 1.0
                when { i <= 6 || (i > 7 && r < 0.62) -> done += d; i == 7 || r < 0.72 -> missed += d }
            }
            p[DONE] = done; p[MISSED] = missed
        }
    }
}
