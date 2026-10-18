package com.rafael.habito

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Geometria do calendário estilo GitHub: semanas em colunas, domingo no topo, hoje na última coluna. */
object Cal {
    private val PT = Locale("pt", "BR")
    private val SHORT = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", PT)
    private val LONG = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT)

    /** "qua, 5 de ago" (sem os pontos das abreviações). */
    fun shortDate(d: LocalDate): String = d.format(SHORT).replace(".", "")

    /** "Sexta-feira, 16 de outubro". */
    fun longDate(d: LocalDate): String = d.format(LONG).replaceFirstChar { it.uppercase() }

    val MONTHS = arrayOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val DOW_SHORT = arrayOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb")
    private val DOW_INITIAL = arrayOf("D", "S", "T", "Q", "Q", "S", "S")

    /** 0 = a semana começa no domingo, 1 = na segunda. Definido a partir do estado ao calcular a tela e o widget. */
    @Volatile var firstDow: Int = 0

    /** Dia da semana (0 = domingo) que ocupa a linha [row] da grade. */
    fun dowAt(row: Int): Int = (row + firstDow) % 7

    /** Rótulos de linha à moda do GitHub: só seg, qua e sex. */
    val WEEKDAYS: Map<Int, String> get() = (0..6).filter { dowAt(it) in setOf(1, 3, 5) }.associateWith { DOW_SHORT[dowAt(it)] }

    /** Iniciais na ordem da semana escolhida (cabeçalho do mês). */
    val INITIALS: List<String> get() = (0..6).map { DOW_INITIAL[dowAt(it)] }

    /** Inicial de um dia (semana do Hoje). */
    fun initial(d: LocalDate): String = DOW_INITIAL[d.dow]

    /** Linha de um dia na grade (e coluna no mês). */
    fun rowOf(d: LocalDate): Int = (d.dow - firstDow + 7) % 7

    /** Primeiro dia (domingo ou segunda) da primeira coluna. */
    fun start(today: LocalDate, weeks: Int): LocalDate =
        today.minusDays(((weeks - 1) * 7 + rowOf(today)).toLong())

    fun dateAt(start: LocalDate, col: Int, row: Int): LocalDate = start.plusDays((col * 7 + row).toLong())

    /** Rótulos de mês: coluna onde o mês começa, pulando os que ficariam colados no anterior. */
    fun monthLabels(start: LocalDate, weeks: Int): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var lastCol = -10
        var lastMonth = -1
        for (col in 0 until weeks) {
            val first = start.plusDays(col * 7L)
            val m = first.plusDays(6).monthValue // mês que "domina" a semana
            if (m != lastMonth) {
                if (col - lastCol >= 3 && col <= weeks - 2) { out += col to MONTHS[m - 1]; lastCol = col }
                lastMonth = m
            }
        }
        return out
    }

    /**
     * Intensidade 1..4 pela sequência que termina no dia (1, 2-3, 4-6, 7+).
     * Num hábito binário, a cor mais forte passa a significar "está embalado".
     */
    fun levels(state: HabitState, from: LocalDate, to: LocalDate): Map<LocalDate, Int> {
        val out = HashMap<LocalDate, Int>()
        var run = 0
        var d = from.minusDays(30)
        while (!d.isAfter(to)) {
            // Descanso e tropeço não zeram a corrida: o tom seguinte continua de onde parou.
            run = if (d in state.done) run + 1 else if (state.bridges(d)) run else 0
            out[d] = if (d !in state.done) 0 else when { run == 1 -> 1; run <= 3 -> 2; run <= 6 -> 3; else -> 4 }
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * Dias em que uma sequência alcançou um marco (7, 30, 100, 365), com o tamanho do marco.
     * Ficam na história mesmo que a sequência tenha quebrado depois.
     */
    fun milestones(s: HabitState): Map<LocalDate, Int> {
        val first = s.done.minOrNull() ?: return emptyMap()
        val out = HashMap<LocalDate, Int>()
        var run = 0
        var d = first
        val end = s.done.max()
        while (!d.isAfter(end)) {
            if (d in s.done) { run++; if (isMilestone(run)) out[d] = run } else if (!s.bridges(d)) run = 0
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * Meta semanal: o dia em que a semana cumpriu a meta e, com isso, a sequência de semanas chegou
     * a um marco (4, 12, 26, 52).
     */
    fun weekMilestones(s: HabitState): Map<LocalDate, Int> {
        val first = s.done.minOrNull() ?: return emptyMap()
        val out = HashMap<LocalDate, Int>()
        var ws = s.weekStart(first)
        var run = 0
        while (!ws.isAfter(s.today)) {
            val days = (0..6).map { ws.plusDays(it.toLong()) }.filter { it in s.done && !it.isAfter(s.today) }
            if (days.size >= s.goal) {
                run++
                if (isMilestone(run, weekly = true)) out[days[s.goal - 1]] = run
            } else if (ws != s.weekStart(s.today)) run = 0
            ws = ws.plusWeeks(1)
        }
        return out
    }

    /** Próximo estado ao tocar num dia: vazio → feito → não fiz → vazio. */
    fun next(mark: Mark?): Mark? = when (mark) { null -> Mark.DONE; Mark.DONE -> Mark.MISSED; Mark.MISSED -> null }
}
