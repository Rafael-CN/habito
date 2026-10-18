package com.rafael.habito

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HabitLogicTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 10, 16) // sexta

    private fun at(dateTime: LocalDateTime) {
        HabitStore.clock = Clock.fixed(dateTime.atZone(zone).toInstant(), zone)
    }

    private fun days(vararg back: Int) = back.map { today.minusDays(it.toLong()) }.toSet()

    @Before fun setUp() = at(today.atTime(15, 0))
    @After fun tearDown() { HabitStore.clock = Clock.systemDefaultZone() }

    @Test fun `hoje pendente nao quebra a sequencia`() {
        val s = HabitState(done = days(1, 2, 3))
        assertEquals(3, s.currentStreak())
    }

    @Test fun `hoje feito entra na sequencia`() {
        val s = HabitState(done = days(0, 1, 2))
        assertEquals(3, s.currentStreak())
    }

    @Test fun `dois dias vazios zeram a sequencia`() {
        val s = HabitState(done = days(1, 4, 5, 6))
        assertEquals(1, s.currentStreak())
    }

    @Test fun `melhor sequencia olha o ano inteiro`() {
        val s = HabitState(done = days(1, 2, 10, 11, 12, 13, 14))
        assertEquals(5, s.bestStreak())
    }

    @Test fun `contagem dos ultimos dias inclui hoje e ignora o futuro`() {
        val s = HabitState(done = days(0, 6, 7) + today.plusDays(1))
        assertEquals(2, s.doneInLast(7))
    }

    @Test fun `antes da virada ainda conta como ontem`() {
        at(LocalDateTime.of(2026, 10, 17, 2, 30))
        assertEquals(LocalDate.of(2026, 10, 16), HabitState(rolloverHour = 3).today)
        assertEquals(LocalDate.of(2026, 10, 17), HabitState(rolloverHour = 0).today)
    }

    @Test fun `a virada acontece na hora escolhida`() {
        at(LocalDateTime.of(2026, 10, 17, 3, 0))
        assertEquals(LocalDate.of(2026, 10, 17), HabitState(rolloverHour = 3).today)
    }

    @Test fun `grade comeca num domingo e termina na semana de hoje`() {
        val start = Cal.start(today, 26)
        assertEquals(DayOfWeek.SUNDAY, start.dayOfWeek)
        val lastColumn = (0..6).map { Cal.dateAt(start, 25, it) }
        assertTrue(today in lastColumn)
    }

    @Test fun `niveis seguem o tamanho da sequencia`() {
        val s = HabitState(done = days(*(0..9).toList().toIntArray()))
        val lv = Cal.levels(s, today.minusDays(9), today)
        assertEquals(1, lv[today.minusDays(9)])
        assertEquals(2, lv[today.minusDays(8)])
        assertEquals(3, lv[today.minusDays(6)])
        assertEquals(4, lv[today.minusDays(3)])
        assertEquals(0, lv[today.minusDays(10)])
    }

    @Test fun `toque num dia cicla vazio, feito, nao fiz`() {
        assertEquals(Mark.DONE, Cal.next(null))
        assertEquals(Mark.MISSED, Cal.next(Mark.DONE))
        assertNull(Cal.next(Mark.MISSED))
    }

    @Test fun `marcos ficam no dia em que a sequencia chegou a 7 e continuam depois da quebra`() {
        // 10 dias seguidos, quebra, mais 3: o marco de 7 cai no 7º dia da primeira sequência
        val first = (0 until 10).map { today.minusDays(30L - it) }
        val second = (0 until 3).map { today.minusDays(5L - it) }
        val m = Cal.milestones(HabitState(done = (first + second).toSet()))
        assertEquals(mapOf(today.minusDays(24) to 7), m)
    }

    @Test fun `trinta dias seguidos tem dois marcos`() {
        val run = (0 until 30).map { today.minusDays(it.toLong()) }.toSet()
        assertEquals(setOf(7, 30), Cal.milestones(HabitState(done = run)).values.toSet())
    }

    // ---------- meta semanal (hoje é sexta, 16/10; a semana começa no domingo 11/10)
    private val sunday = LocalDate.of(2026, 10, 11)
    private fun week(back: Int, vararg dow: Int) = dow.map { sunday.minusWeeks(back.toLong()).plusDays(it.toLong()) }

    @Test fun `semana em aberto nao quebra a sequencia de semanas`() {
        val done = week(1, 1, 3, 5) + week(2, 1, 3, 5) + week(3, 0, 2, 4) + week(0, 1)
        val s = HabitState(done = done.toSet(), goal = 3)
        assertEquals(3, s.weekStreak())
        assertEquals(1, s.doneThisWeek)
    }

    @Test fun `semana atual cumprida entra na sequencia`() {
        val done = week(1, 1, 3, 5) + week(0, 0, 1, 2)
        assertEquals(2, HabitState(done = done.toSet(), goal = 3).weekStreak())
    }

    @Test fun `semana abaixo da meta zera a sequencia de semanas`() {
        val done = week(1, 1, 3, 5) + week(2, 1) + week(3, 1, 2, 3)
        val s = HabitState(done = done.toSet(), goal = 3)
        assertEquals(1, s.weekStreak())
        assertEquals(1, s.bestWeekStreak())
    }

    @Test fun `com meta semanal a sequencia exibida e em semanas`() {
        val done = week(1, 1, 3, 5, 6) + week(0, 1, 2, 3, 4)
        val s = HabitState(done = done.toSet(), goal = 4)
        assertEquals(2, s.streak())
        assertEquals(4, HabitState(done = done.toSet()).streak()) // sem meta: dias seguidos (seg a qui desta semana)
    }

    @Test fun `marco de 4 semanas cai no dia em que a 4a semana cumpriu a meta`() {
        val done = (4 downTo 1).flatMap { week(it, 1, 2, 3) } + week(0, 1)
        val m = Cal.weekMilestones(HabitState(done = done.toSet(), goal = 3))
        assertEquals(mapOf(sunday.minusWeeks(1).plusDays(3) to 4), m)
    }

    // ---------- descanso (hoje é sexta, 16/10; domingo = 0)
    @Test fun `domingo de descanso nao quebra a sequencia e nao conta`() {
        // sex 9, sáb 10, (dom 11 descanso), seg 12 a qui 15
        val done = days(7, 6, 4, 3, 2, 1)
        assertEquals(6, HabitState(done = done, rest = setOf(0)).currentStreak())
        assertTrue(HabitState(done = done, rest = setOf(0)).tropes.isEmpty()) // descanso, não tropeço
    }

    @Test fun `descanso feito conta normalmente`() {
        assertEquals(7, HabitState(done = days(7, 6, 5, 4, 3, 2, 1), rest = setOf(0)).currentStreak())
    }

    @Test fun `descanso atravessa o recorde, os tons e os marcos`() {
        // 10 dias de calendário com 2 domingos no meio, só os domingos vazios
        val sundays = setOf(today.minusDays(5), today.minusDays(12))
        val done = (1..13).map { today.minusDays(it.toLong()) }.filter { it !in sundays }.toSet()
        val s = HabitState(done = done, rest = setOf(0))
        assertEquals(11, s.bestStreak())
        val lv = Cal.levels(s, today.minusDays(13), today)
        assertEquals(0, lv[today.minusDays(5)])          // o domingo fica vazio
        assertEquals(4, lv[today.minusDays(1)])          // e a corrida chega ao tom máximo
        assertEquals(setOf(7), Cal.milestones(s).values.toSet())
    }

    @Test fun `com meta semanal o descanso nao se aplica`() {
        val s = HabitState(done = days(1), rest = setOf(0), goal = 3)
        assertTrue(!s.isRest(sunday))
    }

    // ---------- semana começando na segunda
    @Test fun `grade e semana comecam na segunda quando escolhido`() {
        try {
            Cal.firstDow = 1
            val start = Cal.start(today, 53)
            assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
            assertEquals(today.minusDays(4), HabitState(mondayFirst = true).weekStart(today))
            assertEquals(listOf("S", "T", "Q", "Q", "S", "S", "D"), Cal.INITIALS)
            assertEquals(setOf("seg", "qua", "sex"), Cal.WEEKDAYS.values.toSet())
        } finally { Cal.firstDow = 0 }
    }

    // ---------- lembretes
    @Test fun `proximo lembrete e o primeiro horario ainda por vir, senao o primeiro de amanha`() {
        val rems = listOf(Rem(7 * 60), Rem(21 * 60))
        assertEquals(21 * 60, Reminder.next(rems, today.atTime(15, 0))!!.second.minutes)
        val (at, r) = Reminder.next(rems, today.atTime(22, 0))!!
        assertEquals(7 * 60, r.minutes); assertEquals(today.plusDays(1), at.toLocalDate())
        assertNull(Reminder.next(emptyList(), today.atTime(8, 0)))
    }

    @Test fun `lembrete respeita feito, so pendente, descanso e meta cumprida`() {
        val pend = Rem(21 * 60, onlyPending = true); val always = Rem(7 * 60, onlyPending = false)
        assertTrue(Reminder.shouldNotify(HabitState(), pend))
        assertTrue(!Reminder.shouldNotify(HabitState(done = days(0)), always))
        assertTrue(!Reminder.shouldNotify(HabitState(missed = days(0)), pend))
        assertTrue(Reminder.shouldNotify(HabitState(missed = days(0)), always))
        assertTrue(!Reminder.shouldNotify(HabitState(rest = setOf(5)), pend))          // hoje é sexta
        val met = HabitState(done = setOf(sunday, sunday.plusDays(1), sunday.plusDays(2)), goal = 3)
        assertTrue(!Reminder.shouldNotify(met, pend))
    }

    @Test fun `lembretes gravados ida e volta, ignorando lixo`() {
        val r = listOf(Rem(1260, true), Rem(450, false))
        assertEquals(r.sortedBy { it.minutes }, HabitStore.decodeRems(HabitStore.encodeRems(r)))
        assertEquals(listOf(Rem(60)), HabitStore.decodeRems("60:1,abc,9999:1,"))
    }

    @Test fun `rotulos de mes nunca ficam colados`() {
        val labels = Cal.monthLabels(Cal.start(today, 53), 53)
        assertTrue(labels.size >= 10)
        labels.zipWithNext().forEach { (a, b) -> assertTrue(b.first - a.first >= 3) }
    }

    // ---------- nunca duas vezes (hoje é sexta, 16/10)
    @Test fun `um dia vazio entre dois feitos vira tropeco e nao quebra`() {
        val s = HabitState(done = days(1, 3, 4, 5))
        assertEquals(setOf(today.minusDays(2)), s.tropes)
        assertEquals(4, s.currentStreak())
        assertEquals(4, s.bestStreak())
    }

    @Test fun `nao fiz isolado tambem vira tropeco`() {
        val s = HabitState(done = days(0, 2, 3), missed = days(1))
        assertEquals(3, s.currentStreak())
    }

    @Test fun `ontem vazio e hoje pendente mantem a sequencia esperando`() {
        val s = HabitState(done = days(2, 3, 4))
        assertTrue(s.pendingTrope)
        assertEquals(today.minusDays(1), s.pendingTropeDay)
        assertEquals(3, s.currentStreak())
        assertTrue(s.tropes.isEmpty())
    }

    @Test fun `nao fiz hoje depois de ontem vazio fecha a sequencia`() {
        val s = HabitState(done = days(2, 3, 4), missed = days(0))
        assertTrue(!s.pendingTrope)
        assertEquals(0, s.currentStreak())
    }

    @Test fun `no maximo um tropeco por semana`() {
        // feito, vazio, feito, vazio, feito: só o primeiro vazio é perdoado
        val s = HabitState(done = days(0, 2, 4, 5))
        assertEquals(setOf(today.minusDays(3)), s.tropes)
        assertEquals(1, s.currentStreak())
    }

    @Test fun `tropeco atravessa tons e marcos`() {
        val done = (0..7).map { today.minusDays(it.toLong()) }.filter { it != today.minusDays(4) }.toSet()
        val s = HabitState(done = done)
        assertEquals(7, s.currentStreak())
        assertEquals(setOf(7), Cal.milestones(s).values.toSet())
        assertEquals(4, Cal.levels(s, today.minusDays(7), today)[today])
    }

    @Test fun `meta semanal nao tem tropeco`() {
        assertTrue(HabitState(done = days(1, 3, 4, 5), goal = 3).tropes.isEmpty())
    }

    // ---------- voltas e costura
    @Test fun `sequencias e voltas`() {
        // 10 feitos (40..31), 3 parados, 8 feitos (27..20), 5 parados, 3 feitos (14..12), parado até hoje
        val done = (31..40) + (20..27) + (12..14)
        val s = HabitState(done = days(*done.toIntArray()))
        assertEquals(listOf(10, 8, 3), s.runs.map { it.days })
        assertEquals(listOf(0, 3, 5), s.runs.map { it.gapBefore })
        assertEquals(0, s.currentStreak())
        assertTrue(s.inReturn)
        assertEquals(3, s.returnNumber)   // a próxima volta é a 3ª
        assertEquals(3, s.lastRunDays)
        val (n, avg) = s.returnsIn(365)
        assertEquals(2, n); assertEquals(4.0, avg, 0.01)
    }

    @Test fun `volta em andamento`() {
        val s = HabitState(done = days(*((10..30) + (0..1)).toIntArray()))
        assertEquals(2, s.currentStreak())
        assertTrue(s.inReturn)
        assertEquals(1, s.returnNumber)
        assertEquals(21, s.lastRunDays)
    }

    @Test fun `costura so depois de sequencia com marco e quando ja voltou`() {
        // 9 feitos, 2 parados, volta
        val s = HabitState(done = days(*((5..13) + (0..2)).toIntArray()))
        assertEquals(setOf(today.minusDays(3), today.minusDays(4)), s.scars)
        // sequência curta não costura
        assertTrue(HabitState(done = days(*((5..8) + (0..2)).toIntArray())).scars.isEmpty())
        // quebrou e não voltou: ainda é só uma lacuna
        assertTrue(HabitState(done = days(*(5..13).toList().toIntArray())).scars.isEmpty())
    }

    // ---------- carta do dia 1
    @Test fun `frase abre quando a sequencia chega ao dia dela`() {
        val l = Letter(30, "porque sim", today.minusDays(29))
        val s29 = HabitState(done = days(*(1..29).toList().toIntArray()), letters = listOf(l))
        assertNull(s29.letterToOpen)
        val s30 = HabitState(done = days(*(0..29).toList().toIntArray()), letters = listOf(l))
        assertEquals(l, s30.letterToOpen)
        assertNull(s30.copy(letters = listOf(l.copy(opened = today))).letterToOpen)
    }

    @Test fun `frases gravadas ida e volta`() {
        val list = listOf(Letter(30, "a|b: c", today, today), Letter(100, "linha\nnova", today))
        assertEquals(list, Letter.decode(Letter.encode(list)))
        assertTrue(Letter.decode("lixo").isEmpty())
    }

    // ---------- regras com data e viagem (hoje é sexta, 16/10)
    @Test fun `descanso novo nao reescreve o passado`() {
        // domingo 11/10 vazio, no meio de uma sequência; o descanso de domingo só passou a valer hoje
        val done = days(7, 6, 4, 3, 2, 1) // sex 9, sáb 10, seg 12 a qui 15
        val h = HabitStore.withRestChange(emptyList(), emptySet(), setOf(0), today)
        val s = HabitState(done = done, rest = setOf(0), restHistory = h)
        assertTrue(!s.isRest(today.minusDays(5)))          // o domingo passado continua um dia comum
        assertEquals(setOf(today.minusDays(5)), s.tropes)  // e vira tropeço, não descanso
        assertTrue(HabitState(rest = setOf(0), restHistory = h).restAt(today.plusDays(2)) == setOf(0))
    }

    @Test fun `historico de descanso ida e volta`() {
        val h = HabitStore.withRestChange(emptyList(), setOf(6), setOf(0, 6), today)
        assertEquals(listOf(HabitStore.EPOCH to setOf(6), today to setOf(0, 6)), h)
        assertEquals(h, HabitStore.decodeRestHistory(HabitStore.encodeRestHistory(h)))
        // mudar de novo no mesmo dia substitui
        assertEquals(2, HabitStore.withRestChange(h, setOf(0, 6), emptySet(), today).size)
    }

    @Test fun `dia seguinte ja feito por fuso vira hoje`() {
        val s = HabitState(done = setOf(today.plusDays(1)) + days(0, 1, 2))
        assertEquals(today.plusDays(1), s.today)
        assertEquals(4, s.currentStreak())
    }
}
