package com.rafael.habito

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DOW_NAME = arrayOf("domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado")
private val SUGGESTIONS = listOf("Ler 20 páginas", "Treinar", "Meditar 10 min", "Escrever")
private val PRESETS = listOf("manhã" to 7 * 60, "almoço" to 12 * 60 + 30, "tarde" to 18 * 60, "noite" to 21 * 60)

/** Tudo o que a folha pode mudar. */
class SettingsActions(
    val onName: (String) -> Unit, val onGoal: (Int) -> Unit, val onRest: (Set<Int>) -> Unit,
    val onReminders: (List<Rem>) -> Unit, val onPalette: (String) -> Unit, val onTheme: (Int) -> Unit,
    val onMonday: (Boolean) -> Unit, val onHaptics: (Boolean) -> Unit, val onCelebrate: (Boolean) -> Unit,
    val onHour: (Int) -> Unit, val onExport: () -> Unit, val onImport: () -> Unit, val onBattery: () -> Unit,
    val onAddWidget: (Boolean) -> Unit,
    val onSeal: (Int, String) -> Unit, val onUnseal: () -> Unit,
    val onBackupFreq: (Int) -> Unit, val onBackupFolder: () -> Unit, val onBackupNow: () -> Unit,
    val onAddTile: (() -> Unit)?,
    val onShareError: (() -> Unit)?,
    val tick: () -> Unit,
)

/** "aos domingos", "no fim de semana", "segunda e quarta"… */
internal fun restText(rest: Set<Int>): String {
    val r = rest.sorted()
    return when {
        r.isEmpty() -> "nenhum dia"
        r.size == 1 -> if (r[0] == 0 || r[0] == 6) "aos ${DOW_NAME[r[0]]}s" else "às ${DOW_NAME[r[0]]}s"
        r == listOf(0, 6) -> "no fim de semana"
        else -> r.dropLast(1).joinToString(", ") { DOW_NAME[it] } + " e " + DOW_NAME[r.last()]
    }
}

/**
 * Os Ajustes como se lê: "Quero estudar 30 min todo dia. Descanso aos domingos. Me lembre às 21:00."
 * Cada trecho sublinhado abre o próprio controle logo abaixo da frase, um por vez. Depois vêm a aparência
 * (cor do mapa, tema, começo da semana), as sensações e, fechado no fim, o que é raro (backup e avançado).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsContent(s: HabitState, unrestricted: Boolean, lastBackup: LocalDate?, a: SettingsActions) {
    val pal = Tokens
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var remSel by rememberSaveable { mutableStateOf(0) }
    fun toggle(key: String) { a.tick(); open = if (open == key) null else key }

    Column {
        // 1 · o hábito
        Sentence {
            Word("Quero ")
            Token(s.taskName.replaceFirstChar { it.lowercase() }, open == "task") { toggle("task") }
            Word(" ")
            Token(if (s.weekly) "${s.goal}× por semana" else "todo dia", open == "goal") { toggle("goal") }
            Word(".")
        }
        Editor(open == "task") { TaskEditor(s.taskName, a) }
        Editor(open == "goal") {
            Chips((listOf(0) + listOf(3, 4, 5, 6)).map { if (it == 0) "todo dia" else "$it×" }, HabitStore.GOAL_OPTIONS.indexOf(s.goal)) { a.tick(); a.onGoal(HabitStore.GOAL_OPTIONS[it]) }
            Note(if (s.weekly) "${s.goal} vezes por semana, em qualquer dia. A sequência passa a contar semanas cumpridas." else "Todo dia, ou de 3 a 6 vezes por semana. A sequência conta dias seguidos.")
        }

        AnimatedVisibility(!s.weekly, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Sentence {
                    Word("Descanso ")
                    Token(restText(s.rest), open == "rest") { toggle("rest") }
                    Word(".")
                }
                Editor(open == "rest") {
                    RestDays(s.rest) { d ->
                        a.tick()
                        val next = if (d in s.rest) s.rest - d else if (s.rest.size < HabitStore.MAX_REST) s.rest + d else s.rest
                        a.onRest(next)
                    }
                    Note("Dias de descanso não quebram a sequência e aparecem com um traço no mapa.")
                }
            }
        }

        Sentence {
            Word("Me lembre ")
            if (s.reminders.isEmpty()) Word("nunca ", color = pal.text3)
            s.reminders.forEachIndexed { i, r ->
                if (i > 0) Word(" e ")
                Word("às ")
                Token(r.label, open == "rem" && remSel == i) { a.tick(); remSel = i; open = if (open == "rem" && remSel == i) null else "rem" }
            }
            if (s.reminders.size < HabitStore.MAX_REMINDERS) {
                Word(" ")
                AddToken {
                    a.tick()
                    val used = s.reminders.map { it.minutes }
                    val m = PRESETS.map { it.second }.firstOrNull { it !in used } ?: 8 * 60
                    val next = (s.reminders + Rem(m)).sortedBy { it.minutes }
                    a.onReminders(next); remSel = next.indexOfFirst { it.minutes == m }; open = "rem"
                }
            }
        }
        Editor(open == "rem" && remSel in s.reminders.indices) {
            val r = s.reminders.getOrNull(remSel) ?: return@Editor
            fun update(nr: Rem) {
                val list = s.reminders.toMutableList().also { it[remSel] = nr }.sortedBy { it.minutes }
                remSel = list.indexOf(nr); a.onReminders(list)
            }
            ClockEditor(r, onChange = { a.tick(); update(it) })
            Text(
                "Remover este lembrete", style = Type.body, color = pal.miss, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(Radius.control)).clickable {
                    a.tick(); open = null; a.onReminders(s.reminders.filterIndexed { i, _ -> i != remSel }); remSel = 0
                }.padding(vertical = 6.dp),
            )
        }

        Sentence {
            val sealed = s.sealed
            if (sealed != null) {
                Word("Uma frase lacrada para o ")
                Token("dia ${sealed.forDay}", open == "letter") { toggle("letter") }
                Word(".")
            } else {
                Word("Uma frase para o futuro ")
                AddToken("+ lacrar") { toggle("letter") }
            }
        }
        Editor(open == "letter") { LetterEditor(s, a) { open = null } }

        // 2 · aparência
        Group("Aparência")
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cor do mapa", style = Type.bodyL, color = pal.ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Swatches(s.palette) { a.tick(); a.onPalette(it) }
        }
        PalettePreview(s)
        Labeled("Tema") { Track(listOf("Sistema", "Claro", "Escuro"), s.themeMode, { a.tick(); a.onTheme(it) }, Modifier.width(210.dp)) }
        Labeled("A semana começa") { Track(listOf("Domingo", "Segunda"), if (s.mondayFirst) 1 else 0, { a.tick(); a.onMonday(it == 1) }, Modifier.width(210.dp)) }

        // 3 · sensações
        Group("Sensações")
        SwitchRow("Vibrar ao marcar", "Um toque firme no feito, leve no não fiz.", s.haptics) { a.onHaptics(it) }
        SwitchRow("Comemorar marcos", "7, 30, 100 e 365 dias acendem a sequência.", s.celebrate) { a.tick(); a.onCelebrate(it) }

        // 4 · histórico: a cópia automática, sempre à vista
        Group("Histórico")
        Sentence {
            Word("Guardar uma cópia ")
            Token(AutoBackup.FREQ_LABELS[s.backupFreq], open == "bfreq") { toggle("bfreq") }
            if (s.backupFreq > 0 && s.backupUri.isNotEmpty()) {
                Word(" em ")
                Token(AutoBackup.folderName(s.backupUri), open == "bdest") { toggle("bdest") }
            }
            Word(".")
        }
        Editor(open == "bfreq") {
            Chips(AutoBackup.FREQ_LABELS, s.backupFreq) { a.tick(); a.onBackupFreq(it); if (it == 0) open = null }
        }
        Editor(open == "bdest") {
            OutlineButton("Escolher outra pasta", R.drawable.ic_export, a.onBackupFolder)
        }
        BackupState(s, a)

        // 5 · o que é raro, junto e fechado
        Spacer(Modifier.height(18.dp))
        SpecRow("Trocar de celular", "3 passos", open == "move", { toggle("move") }, valueColor = pal.text3) {
            Steps(listOf("Guardar uma cópia", "Levar o arquivo", "Restaurar no celular novo"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Exportar", R.drawable.ic_export, a.onExport, Modifier.weight(1f))
                OutlineButton("Restaurar", R.drawable.ic_import, a.onImport, Modifier.weight(1f))
            }
        }
        SpecRow("Atalhos", "widget · bloco rápido", open == "widget", { toggle("widget") }, valueColor = pal.text3) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Ano 4×3", R.drawable.ic_widget_add, { a.onAddWidget(false) }, Modifier.weight(1f))
                OutlineButton("Compacto 2×2", R.drawable.ic_widget_add, { a.onAddWidget(true) }, Modifier.weight(1f))
            }
            val addTile = a.onAddTile
            if (addTile != null) OutlineButton("Bloco nas Configurações Rápidas", R.drawable.ic_tile, addTile, Modifier.fillMaxWidth())
            else Note("O bloco rápido fica na lista de edição das Configurações Rápidas.")
            Note("Para ajustar um widget, toque e segure nele e escolha Configurar.")
        }
        SpecRow("Avançado", "virada do dia, bateria", open == "adv", { toggle("adv") }, valueColor = pal.text3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("O dia vira às", style = Type.bodyL, color = pal.ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Track(HabitStore.HOUR_OPTIONS.map { "%02d h".format(it) }, HabitStore.HOUR_OPTIONS.indexOf(s.rolloverHour), { a.tick(); a.onHour(HabitStore.HOUR_OPTIONS[it]) }, Modifier.width(190.dp))
            }
            Note("Para quem dorme tarde: às 3 h, o que você marcar de madrugada ainda conta para o dia anterior.")
            a.onShareError?.let { share ->
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(Radius.control)).clickable(onClick = share), verticalAlignment = Alignment.CenterVertically) {
                    Text("Último erro", style = Type.bodyL, color = pal.ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("enviar", style = Type.body, color = pal.text2, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Icon(painterResource(R.drawable.ic_arrow_out), null, Modifier.size(16.dp), tint = pal.text3)
                }
            }
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(Radius.control)).clickable(onClick = a.onBattery),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bateria", style = Type.bodyL, color = pal.ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(if (unrestricted) "sem restrição" else "otimizada", style = Type.body, color = if (unrestricted) pal.done else pal.text2, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Icon(painterResource(R.drawable.ic_arrow_out), null, Modifier.size(16.dp), tint = pal.text3)
            }
        }
        Text(
            "Hábito 4.0 · fonte Archivo (OFL) · seus dados ficam só neste aparelho",
            style = Type.caption, color = pal.text3, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
        )
    }
}

// ---------- frases

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sentence(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

private val SENTENCE = Type.title.copy(fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp)

@Composable
private fun Word(text: String, color: Color = Tokens.text2) {
    // Espaços no fim viram espaço real entre os trechos.
    Text(text, style = SENTENCE, color = color, softWrap = false)
}

/** Trecho editável: sublinhado fino; aberto, ganha fundo e régua de tinta de 2 dp. */
@Composable
private fun Token(text: String, open: Boolean, onClick: () -> Unit) {
    val pal = Tokens
    val bg by animateColorAsState(if (open) pal.surface else pal.surface.copy(alpha = 0f), motion(200), label = "tokBg")
    val line by animateFloatAsState(if (open) 2f else 1.5f, motion(200), label = "tokLine")
    val lineColor by animateColorAsState(if (open) pal.ink else pal.line, motion(200), label = "tokLc")
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    AnimatedContent(
        text, label = "tok",
        transitionSpec = {
            (slideInVertically(spring(0.85f, 500f)) { it / 2 } + fadeIn(tween(200))) togetherWith (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(140)))
        },
    ) { t ->
        Text(
            t, style = SENTENCE, color = pal.ink, softWrap = false,
            modifier = Modifier
                .graphicsLayer { translationY = if (pressed) 1.dp.toPx() else 0f }
                .clip(RoundedCornerShape(2.dp))
                .background(bg)
                .drawBehind { drawRect(lineColor, Offset(0f, size.height - 5.dp.toPx()), Size(size.width, line.dp.toPx())) }
                .semantics { role = Role.Button; stateDescription = if (open) "aberto" else "fechado" }
                .clickable(press, indication = null, onClick = onClick)
                .padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun AddToken(label: String = "+ horário", onClick: () -> Unit) {
    val pal = Tokens
    Box(Modifier.padding(top = 4.dp).height(30.dp).clip(RoundedCornerShape(Radius.control))
        .drawBehind {
            drawRoundRect(pal.line, style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f))), cornerRadius = CornerRadius(Radius.control.toPx()))
        }
        .clickable(onClick = onClick).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, style = Type.bodyL, color = pal.text3)
    }
}

/** Escrever (ou trocar) a frase lacrada e escolher o dia em que ela abre. Lacrada, ninguém lê, nem você. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LetterEditor(s: HabitState, a: SettingsActions, onDone: () -> Unit) {
    val pal = Tokens
    val sealed = s.sealed
    var text by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(sealed?.forDay ?: 30) }
    Chips(listOf("dia 30", "dia 100"), if (day == 30) 0 else 1) { a.tick(); day = if (it == 0) 30 else 100 }
    LetterField(text, { text = it }, if (sealed != null) "Trocar por outra frase…" else "Por que este hábito?", minHeight = 84)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlineButton("Lacrar", null, { if (text.isNotBlank()) { a.tick(); a.onSeal(day, text); onDone() } }, Modifier.weight(1f))
        var confirm by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(confirm) { if (confirm) { delay(3000); confirm = false } }
        if (sealed != null) Text(
            if (confirm) "Apagar mesmo" else "Apagar", style = Type.body, color = pal.miss, fontWeight = if (confirm) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(Radius.control)).clickable { a.tick(); if (confirm) { a.onUnseal(); onDone() } else confirm = true }.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** O estado da cópia numa linha: ponto verde em dia, âmbar atrasada, e o "guardar agora" quando faz falta. */
@Composable
private fun BackupState(s: HabitState, a: SettingsActions) {
    val pal = Tokens
    val stale = AutoBackup.stale(s)
    val dot by animateColorAsState(when { stale -> pal.gold; s.backupAt == 0L -> pal.text3; else -> pal.done }, motion(300), label = "bdot")
    val text = when {
        s.backupAt == 0L -> "Nenhuma cópia ainda"
        else -> {
            val days = AutoBackup.daysWithout(s)
            val at = java.time.Instant.ofEpochMilli(s.backupAt).atZone(java.time.ZoneId.systemDefault())
            when (days) {
                0 -> "Última cópia hoje, ${at.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                1 -> "Última cópia ontem"
                else -> "Última cópia há $days dias"
            }
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) { drawCircle(dot) }
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.body, color = pal.text2, modifier = Modifier.weight(1f))
        AnimatedVisibility(stale && s.backupUri.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.clip(RoundedCornerShape(Radius.control)).background(pal.ink).clickable { a.tick(); a.onBackupNow() }.padding(horizontal = 13.dp, vertical = 8.dp),
            ) { Text("Guardar agora", style = Type.body, color = pal.bg, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Passos numerados, número condensado à esquerda. */
@Composable
private fun Steps(steps: List<String>) {
    val pal = Tokens
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = Type.stat.copy(fontSize = 20.sp, lineHeight = 20.sp), color = pal.text3, modifier = Modifier.width(26.dp))
                Text(t, style = Type.bodyL, color = pal.ink)
            }
        }
    }
}

/** O controle de um trecho, abrindo sob a frase com a mesma mola das linhas da ficha. */
@Composable
private fun Editor(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible,
        enter = expandVertically(spring(0.82f, 420f)) + fadeIn(tween(200, 60)),
        exit = shrinkVertically(spring(1f, 500f)) + fadeOut(tween(120)),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Note(text: String) = Text(text, style = Type.body.copy(fontSize = 12.sp, lineHeight = 17.sp), color = Tokens.text2)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditor(name: String, a: SettingsActions) {
    val pal = Tokens
    val focus = LocalFocusManager.current
    val req = remember { FocusRequester() }
    var text by remember(name) { mutableStateOf(name) }
    var focused by remember { mutableStateOf(false) }
    val underline by animateFloatAsState(if (focused) 1f else 0f, motion(360), label = "field")
    val save = { if (text.trim() != name) a.onName(text) }
    androidx.compose.runtime.LaunchedEffect(Unit) { delay(150); runCatching { req.requestFocus() } }
    BasicTextField(
        value = text, onValueChange = { text = it.take(28) }, singleLine = true,
        textStyle = Type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = pal.ink),
        cursorBrush = SolidColor(pal.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save(); focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().focusRequester(req).onFocusChanged { focused = it.isFocused; if (!it.isFocused) save() }
            .drawBehind {
                drawRect(pal.line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
                if (underline > 0f) drawRect(pal.ink, Offset(0f, size.height - 2.dp.toPx()), Size(size.width * underline, 2.dp.toPx()))
            }
            .padding(top = 6.dp, bottom = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SUGGESTIONS.forEach { sug -> Chip(sug, false) { a.tick(); text = sug; a.onName(sug) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o -> Chip(o, i == selected) { onSelect(i) } }
    }
}

/** Opção reta: contorno fino; escolhida, vira bloco de tinta. */
@Composable
internal fun Chip(text: String, on: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val pal = Tokens
    val bg by animateColorAsState(if (on) pal.ink else pal.ink.copy(alpha = 0f), motion(200), label = "chipBg")
    val fg by animateColorAsState(if (on) pal.bg else pal.text2, motion(200), label = "chipFg")
    val border by animateColorAsState(if (on) pal.ink else pal.rule, motion(200), label = "chipB")
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 0.96f else 1f, Motion.press, label = "chipS")
    Box(
        Modifier.height(if (small) 32.dp else 36.dp).scale(scale).clip(RoundedCornerShape(Radius.control)).background(bg)
            .border(1.dp, border, RoundedCornerShape(Radius.control))
            .semantics { role = Role.RadioButton }
            .clickable(press, indication = null, onClick = onClick).padding(horizontal = if (small) 10.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = if (small) Type.body else Type.bodyL.copy(fontSize = 14.sp), color = fg, fontWeight = FontWeight.Medium) }
}

/** Os 7 dias na ordem da semana escolhida; os de descanso ficam em tinta, com um traço. */
@Composable
private fun RestDays(rest: Set<Int>, onToggle: (Int) -> Unit) {
    val pal = Tokens
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { row ->
            val d = Cal.dowAt(row)
            val on = d in rest
            val bg by animateColorAsState(if (on) pal.ink else pal.surface, motion(200), label = "dayBg")
            val fg by animateColorAsState(if (on) pal.bg else pal.text2, motion(200), label = "dayFg")
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 0.94f else 1f, Motion.press, label = "dayS")
            Box(
                Modifier.weight(1f).height(44.dp).scale(scale).clip(RoundedCornerShape(Radius.control)).background(bg)
                    .semantics { role = Role.Checkbox; stateDescription = if (on) "descanso" else "dia normal" }
                    .clickable(press, indication = null) { onToggle(d) },
                contentAlignment = Alignment.Center,
            ) {
                Text(Cal.INITIALS[row], style = Type.bodyL.copy(fontSize = 14.sp), color = fg, fontWeight = FontWeight.SemiBold)
                if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(width = 12.dp, height = 2.dp).background(pal.bg.copy(alpha = 0.6f)))
            }
        }
    }
}

/** Relógio: horas e minutos em condensada, setas com passo de 1 h e 15 min, atalhos e "só se ainda não marquei". */
@Composable
private fun ClockEditor(r: Rem, onChange: (Rem) -> Unit) {
    val pal = Tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClockColumn("%02d".format(r.minutes / 60), up = { onChange(r.copy(minutes = (r.minutes + 60) % 1440)) }, down = { onChange(r.copy(minutes = (r.minutes + 1380) % 1440)) })
        Text(":", style = Type.number.copy(fontSize = 44.sp), color = pal.text3, modifier = Modifier.padding(horizontal = 2.dp))
        ClockColumn("%02d".format(r.minutes % 60), up = { onChange(r.copy(minutes = (r.minutes + 15) % 1440)) }, down = { onChange(r.copy(minutes = (r.minutes + 1425) % 1440)) })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESETS.forEach { (label, m) -> Chip(label, r.minutes == m, small = true) { onChange(r.copy(minutes = m)) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onChange(r.copy(onlyPending = !r.onlyPending)) }) {
                Switch(r.onlyPending)
                Spacer(Modifier.width(10.dp))
                Text("só se ainda não marquei", style = Type.body, color = pal.text2)
            }
        }
    }
}

@Composable
private fun ClockColumn(value: String, up: () -> Unit, down: () -> Unit) {
    val pal = Tokens
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepButton(R.drawable.ic_chevron_down, flip = true, up)
        AnimatedContent(value, label = "clock", transitionSpec = {
            (slideInVertically(spring(0.85f, 600f)) { -it / 3 } + fadeIn(tween(160))) togetherWith fadeOut(tween(100))
        }) { v -> Text(v, style = Type.number.copy(fontSize = 56.sp, lineHeight = 56.sp), color = pal.ink) }
        StepButton(R.drawable.ic_chevron_down, flip = false, down)
    }
}

@Composable
private fun StepButton(icon: Int, flip: Boolean, onClick: () -> Unit) {
    val pal = Tokens
    Box(Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(Radius.control)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), if (flip) "Aumentar" else "Diminuir", Modifier.size(18.dp).graphicsLayer { rotationZ = if (flip) 180f else 0f }, tint = pal.text2)
    }
}

/** Interruptor reto: trilho de 38 dp, marcador quadrado que corre com mola. */
@Composable
internal fun Switch(on: Boolean) {
    val pal = Tokens
    val x by animateFloatAsState(if (on) 1f else 0f, if (LocalReduceMotion.current) tween(0) else spring(0.62f, 700f), label = "sw")
    val track by animateColorAsState(if (on) pal.ink else pal.track, motion(200), label = "swT")
    Box(Modifier.size(width = 38.dp, height = 22.dp).clip(RoundedCornerShape(Radius.control)).background(track).padding(3.dp)) {
        Box(Modifier.size(16.dp).graphicsLayer { translationX = x * 16.dp.toPx() }.clip(RoundedCornerShape(Radius.thumb)).background(pal.thumb).border(1.dp, pal.thumbEdge, RoundedCornerShape(Radius.thumb)))
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val pal = Tokens
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { onChange(!on) }
                .semantics { role = Role.Switch; stateDescription = if (on) "ligado" else "desligado" }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.bodyL, color = pal.ink, fontWeight = FontWeight.Medium)
                Text(sub, style = Type.body.copy(fontSize = 12.sp, lineHeight = 16.sp), color = pal.text2)
            }
            Spacer(Modifier.width(12.dp))
            Switch(on)
        }
        Hairline()
    }
}

@Composable
private fun Group(title: String) {
    Column(Modifier.padding(top = 30.dp)) {
        Text(title.uppercase(), style = Type.caps, color = Tokens.text3, modifier = Modifier.padding(bottom = 6.dp))
        Hairline()
    }
}

@Composable
private fun Labeled(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.bodyL, color = Tokens.ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        control()
    }
}

/** Cinco amostras de cor; cada uma mostra três tons da própria escala. */
@Composable
private fun Swatches(selected: String, onSelect: (String) -> Unit) {
    val pal = Tokens
    val dark = pal.bg.red < 0.5f
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Accents.NAMES.forEach { name ->
            val p = paletteFor(name, dark)
            val on = name == selected
            val ring by animateColorAsState(if (on) pal.ink else pal.ink.copy(alpha = 0f), motion(200), label = "sw")
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed && !LocalReduceMotion.current) 0.92f else 1f, Motion.press, label = "swS")
            Canvas(
                Modifier.size(34.dp).scale(scale).clip(RoundedCornerShape(Radius.control)).background(pal.surface)
                    .border(2.dp, ring, RoundedCornerShape(Radius.control))
                    .semantics { role = Role.RadioButton; stateDescription = name }
                    .clickable(press, indication = null) { onSelect(name) },
            ) {
                val pad = 5.dp.toPx(); val g = 2.dp.toPx()
                val w = (size.width - 2 * pad - g) / 2
                val r = CornerRadius(1.dp.toPx())
                drawRoundRect(p.levels[1], Offset(pad, pad), Size(w, w), r)
                drawRoundRect(p.levels[3], Offset(pad + w + g, pad), Size(w, w), r)
                drawRoundRect(p.levels[4], Offset(pad, pad + w + g), Size(2 * w + g, size.height - 2 * pad - w - g), r)
            }
        }
    }
}

/** As últimas 26 semanas na cor escolhida, para ver a troca acontecer. */
@Composable
private fun PalettePreview(s: HabitState) {
    val pal = Tokens
    val today = s.today
    val levels = remember(s) { Cal.levels(s, today.minusDays(26 * 7L), today) }
    Box(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(Radius.panel)).background(pal.surface).padding(10.dp)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(26f / 7f)) {
            val weeks = 26; val gap = 2.dp.toPx()
            val cell = (size.width - gap * (weeks - 1)) / weeks
            val first = today.minusDays(((weeks - 1) * 7 + Cal.rowOf(today)).toLong())
            for (col in 0 until weeks) for (row in 0 until 7) {
                val d = first.plusDays((col * 7 + row).toLong())
                if (d.isAfter(today)) continue
                drawRoundRect(pal.levels[levels[d] ?: 0], Offset(col * (cell + gap), row * (cell + gap)), Size(cell, cell), CornerRadius(1.dp.toPx()))
            }
        }
    }
}
