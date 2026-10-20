package com.rafael.habito

import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.runtime.SideEffect
import android.Manifest
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

private fun describe(date: LocalDate, today: LocalDate, mark: Mark?): String {
    val day = if (date == today) "Hoje" else Cal.shortDate(date).replaceFirstChar { it.uppercase() }
    return "$day · " + when (mark) { Mark.DONE -> "feito"; Mark.MISSED -> "não fiz"; null -> "sem registro" }
}

private val OPEN = CubicBezierEasing(0.22f, 0.9f, 0.26f, 1f)
private val CLOSE = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val SHEET = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * A casca: duas abas (Hoje para agir, História para olhar para trás) que se trocam tocando ou deslizando, a
 * engrenagem que sobe os Ajustes numa folha, e o mês e a retrospectiva abrindo recortados a partir de onde foram
 * tocados. Toda mudança de dia passa por [change], que vibra, grava e oferece desfazer.
 */
@Composable
fun HomeScreen(vm: HabitViewModel) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val u = ui
    val pal = Tokens
    val reduce = LocalReduceMotion.current
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var ripple by remember { mutableIntStateOf(0) }
    var gridRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) } // o mini ano do Hoje, para o marco sair dele
    var glyphMarco by remember { mutableStateOf<Int?>(null) } // a semana e o ano acendem quando um marco é alcançado
    var period by rememberSaveable { mutableIntStateOf(0) } // 0 = 12 meses, ou um ano
    val snackbar = remember { SnackbarHostState() }
    var snackJob by remember { mutableStateOf<Job?>(null) }
    val pager = rememberPagerState { 2 }
    val hojeScroll = rememberScrollState()
    val histScroll = rememberScrollState()

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    var ladderJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val lastChangeToday = remember { mutableStateOf(true) }
    val celebrated = rememberSaveable { mutableStateOf("") }
    fun buzz(kind: Int) { if (u?.state?.haptics != false) view.performHapticFeedback(kind) }
    fun sound(sfx: Sfx) { val st = u?.state ?: return; if (st.sounds) Sounds.play(ctx, st.palette, sfx) }
    val fx = LocalFx.current
    // Não fiz: a tela perde um pouco de cor e volta (Android 12+).
    val desat = remember { Animatable(0f) }
    LaunchedEffect(fx.naoFiz) { if (fx.naoFiz > 0 && !reduce) { desat.animateTo(1f, tween(380)); desat.animateTo(0f, tween(900)) } }
    fun tick() = buzz(HapticFeedbackConstants.CLOCK_TICK)

    fun change(date: LocalDate, mark: Mark?) {
        val cur = u ?: return
        val previous = cur.markOf(date)
        ladderJob?.cancel()
        lastChangeToday.value = date == cur.today
        buzz(when (mark) { Mark.DONE -> HapticFeedbackConstants.CONFIRM; Mark.MISSED -> HapticFeedbackConstants.REJECT; null -> HapticFeedbackConstants.CLOCK_TICK })
        sound(when (mark) { Mark.DONE -> Sfx.FEITO; Mark.MISSED -> Sfx.NAO_FIZ; null -> Sfx.DESFAZER })
        // Motion: só hoje ganha a cena inteira (número, régua com tiques, tela sem cor no não fiz).
        if (date == cur.today && previous != mark && !reduce) {
            val next = cur.streak + 1
            val closesMilestone = cur.state.celebrate && cur.todayMark != Mark.DONE && celebrated.value != "${cur.today}:$next" &&
                (if (cur.weekly) cur.doneThisWeek + 1 >= cur.goal && cur.doneThisWeek < cur.goal && isMilestone(next, true) else isMilestone(next))
            if (mark == Mark.DONE && closesMilestone) Unit
            else if (mark == Mark.DONE) {
                fx.feito++
                if (cur.state.sounds) ladderJob = scope.launch {
                    // tempo absoluto: os tiques não escorregam do ritmo da régua
                    val t0 = android.os.SystemClock.uptimeMillis() + LADDER_START
                    repeat(LADDER) { i -> delay(t0 + i * LADDER_STEP - android.os.SystemClock.uptimeMillis()); Sounds.tick(ctx, cur.state.palette, i) }
                }
            } else if (mark == Mark.MISSED) fx.naoFiz++
        }
        vm.setMark(date, mark)
        // Sem aviso de desfazer: tocar de novo desfaz. O leitor de tela ainda ouve o que mudou.
        view.announceForAccessibility(describe(date, cur.today, mark))
    }

    // Marco alcançado ao marcar hoje: a sequência acende e o aparelho dá três pulsos (se "comemorar" estiver ligado).
    // Uma vez por marco e por dia (desfazer e refazer não comemora de novo); corrigir outro dia não comemora.
    val streak = u?.streak
    val lastStreak = remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(streak) {
        val before = lastStreak.value
        lastStreak.value = streak
        val cur = u
        if (cur != null && cur.state.celebrate && streak != null && before != null && streak > before && isMilestone(streak, cur.weekly) &&
            cur.todayMark == Mark.DONE && lastChangeToday.value && celebrated.value != "${cur.today}:$streak"
        ) {
            celebrated.value = "${cur.today}:$streak"
            ladderJob?.cancel()
            if (cur.state.sounds) Sounds.play(ctx, cur.state.palette, Sfx.MARCO)
            if (!reduce && pager.currentPage == 0) {
                // Lançamento: 1,25 s de swell (véu, brilho, o número tremendo), o impacto, e só então o ano vira o glifo.
                fx.launchAt = uptime()
                glyphMarco = streak
                scope.launch {
                    delay(LAUNCH_RISE)
                    if (cur.state.haptics) Haptics.milestone(ctx)
                    if (fx.number != Rect.Zero) fx.burst(fx.number.center, 46, 5.5f * view.resources.displayMetrics.density, listOf(pal.levels[3], pal.levels[4], pal.levels[2], pal.gold))
                }
            } else if (cur.state.haptics) Haptics.milestone(ctx)
        }
    }

    // Com "Sons" ligado, os sons da cor ficam carregados antes do primeiro toque.
    val soundPalette = u?.state?.let { if (it.sounds) it.palette else null }
    LaunchedEffect(soundPalette) { if (soundPalette != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Sounds.prepare(ctx, soundPalette) } }

    val selectedMark = selected?.let { u?.markOf(it) }
    LaunchedEffect(selected, selectedMark) { if (selected != null) { delay(3600); selected = null } }
    LaunchedEffect(histScroll.isScrollInProgress, pager.currentPage) { if (histScroll.isScrollInProgress || pager.currentPage == 0) selected = null }

    var lastBackup by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.export(uri) }.onSuccess { lastBackup = LocalDate.now(); toast("Backup salvo") }.onFailure { toast("Não foi possível salvar o backup") }
        }
    }
    // Restaurar mostra antes o que vem no arquivo e o que sai; depois de trocar, dá para desfazer.
    var restore by remember { mutableStateOf<Backup.Preview?>(null) }
    fun applyRestore(p: Backup.Preview) = scope.launch {
        restore = null
        val before = vm.applyImport(p.state)
        snackJob?.cancel()
        snackJob = scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("${p.state.done.size} dias restaurados", actionLabel = "Desfazer", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) { tick(); vm.applyImport(before) }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.peekImport(uri) }.onSuccess { restore = it }.onFailure { toast("Arquivo inválido: escolha um backup do Hábito") }
        }
    }
    // Cópia automática: a pasta vem do seletor do sistema; a frequência escolhida espera por ela.
    var pendingFreq by remember { mutableIntStateOf(2) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching { vm.setBackup(pendingFreq, uri) }.onFailure { toast("Não foi possível usar essa pasta") }
    }
    fun backupNow() = scope.launch { if (vm.backupNow()) toast("Cópia guardada") else { pendingFreq = maxOf(u?.state?.backupFreq ?: 2, 1); folderPicker.launch(null) } }

    // Frase do dia 1: aberta quando a sequência chega ao dia dela.
    var opening by remember { mutableStateOf<Letter?>(null) }
    val toOpen = u?.state?.letterToOpen
    // A carta do marco abre depois do glifo.
    LaunchedEffect(toOpen, glyphMarco) { if (toOpen != null && opening == null && glyphMarco == null) { delay(if (reduce) 0 else 900); if (glyphMarco == null) opening = toOpen } }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("Sem permissão de notificação, o lembrete não aparece")
    }
    var unrestricted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        unrestricted = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        onPauseOrDispose { }
    }

    // ---------- sobreposições: mês e retrospectiva, cada uma recortada a partir do retângulo tocado
    var month by rememberSaveable { mutableStateOf<YearMonth?>(null) }
    var monthFrom by remember { mutableStateOf(Rect.Zero) }
    var yearRect by remember { mutableStateOf(Rect.Zero) }
    val monthReveal = remember { Animatable(0f) }
    var shownMonth by remember { mutableStateOf<YearMonth?>(null) }
    LaunchedEffect(month) { shownMonth = reveal(month, shownMonth, monthReveal, reduce) { shownMonth = it } }

    var retro by rememberSaveable { mutableStateOf<YearMonth?>(null) }
    var retroFrom by remember { mutableStateOf(Rect.Zero) }
    val retroReveal = remember { Animatable(0f) }
    var shownRetro by remember { mutableStateOf<YearMonth?>(null) }
    LaunchedEffect(retro) { shownRetro = reveal(retro, shownRetro, retroReveal, reduce) { shownRetro = it } }
    // Na primeira semana do mês, uma vez, a retrospectiva do mês que passou (se houve registro nele).
    LaunchedEffect(u?.today, RetroDebug.force) {
        val cur = u ?: return@LaunchedEffect
        val prev = YearMonth.from(cur.today).minusMonths(1)
        if ((RetroDebug.force || (cur.today.dayOfMonth <= 7 && cur.state.retroSeen != prev.toString())) && hasData(cur, prev)) {
            RetroDebug.force = false; retroFrom = Rect.Zero; retro = prev
        }
    }
    fun closeRetro() { retro?.let { if (it == YearMonth.from(u!!.today).minusMonths(1)) vm.setRetroSeen(it) }; retro = null }
    fun share(kind: String) {
        val cur = u ?: return
        tick()
        scope.launch {
            val bmp = withContext(Dispatchers.Default) {
                if (kind == "ano") ShareImage.year(ctx, cur) else ShareImage.month(ctx, cur, shownRetro!!, retroOf(cur, shownRetro!!))
            }
            runCatching { ShareImage.share(ctx, bmp, "habito-$kind") }.onFailure { toast("Não foi possível compartilhar") }
        }
    }

    // ---------- folha de ajustes
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val sheet = remember { Animatable(0f) } // 0 = fechada, 1 = aberta
    var sheetDrag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sheetOpen) {
        if (reduce) sheet.snapTo(if (sheetOpen) 1f else 0f) else sheet.animateTo(if (sheetOpen) 1f else 0f, tween(if (sheetOpen) 560 else 420, easing = SHEET))
        if (!sheetOpen) sheetDrag = 0f
    }

    // Voltar: anotação, desfazer, mês, retrospectiva e folha, do mais recente ao mais antigo.
    var annoBack by remember { mutableFloatStateOf(0f) }
    BackHandler(enabled = pager.currentPage == 1 && month == null && retro == null && !sheetOpen && selected == null) { scope.launch { pager.animateScrollToPage(0) } }
    BackHandler(enabled = snackbar.currentSnackbarData != null) { snackbar.currentSnackbarData?.dismiss() }
    PredictiveBackHandler(enabled = selected != null) { progress ->
        try { progress.collect { annoBack = it.progress }; selected = null } finally { annoBack = 0f }
    }
    var monthBack by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = month != null) { progress ->
        try { progress.collect { monthBack = it.progress }; month = null } finally { monthBack = 0f }
    }
    var retroBack by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = retro != null) { progress ->
        try { progress.collect { retroBack = it.progress }; closeRetro() } finally { retroBack = 0f }
    }
    BackHandler(enabled = sheetOpen) { sheetOpen = false }

    val goHistory: () -> Unit = {
        tick()
        // O ano vira mapa: cada quadrado do mini ano voa até o seu dia no mapa da História.
        val cur = u
        val g = fx.miniGrid
        if (cur != null && !reduce && g != Rect.Zero && pager.currentPage == 0 && fx.flightAt == 0L && !pager.isScrollInProgress) {
            val weeks = 20
            val gap = 3f * view.resources.displayMetrics.density
            val cell = (g.width - gap * (weeks - 1)) / weeks
            val first = cur.today.minusDays(((weeks - 1) * 7 + Cal.rowOf(cur.today)).toLong())
            val list = ArrayList<FlightCell>()
            for (col in 0 until weeks) for (row in 0 until 7) {
                val d = first.plusDays((col * 7 + row).toLong())
                if (d.isAfter(cur.today)) continue
                val tl = Offset(g.left + col * (cell + gap), g.top + row * (cell + gap))
                list += FlightCell(Rect(tl, Size(cell, cell)), d, pal.levels[cur.level(d)], col)
            }
            fx.flight = list; fx.flightAt = uptime()
            sound(Sfx.PAGINA)
        }
        scope.launch { pager.animateScrollToPage(1, animationSpec = if (reduce) tween(0) else spring(0.9f, 300f)) }
        scope.launch { histScroll.animateScrollTo(0) }
    }

    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().graphicsLayer {
                // A folha aberta faz a página recuar um pouco e escurecer.
                val p = sheet.value
                val s = 1f - 0.05f * p; scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
                renderEffect = if (Build.VERSION.SDK_INT >= 31 && desat.value > 0.01f) {
                    android.graphics.RenderEffect.createColorFilterEffect(
                        android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(1f - 0.65f * desat.value) }),
                    ).asComposeRenderEffect()
                } else null
            },
        ) {
            Tabs(
                page = pager.targetPage, sheetOpen = sheetOpen,
                onTab = { i ->
                    if (i != pager.currentPage) { tick(); scope.launch { pager.animateScrollToPage(i, animationSpec = if (reduce) tween(0) else spring(0.9f, 300f)) } }
                    else scope.launch { (if (i == 0) hojeScroll else histScroll).animateScrollTo(0) } // tocar na aba aberta volta ao topo
                },
                onGear = { tick(); sheetOpen = !sheetOpen },
            )
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { p ->
                    if (u == null) return@HorizontalPager
                    if (p == 0) HojePage(
                        u, ripple, hojeScroll,
                        onDone = { change(u.today, if (u.todayMark == Mark.DONE) null else Mark.DONE) },
                        onSkip = { change(u.today, if (u.todayMark == Mark.MISSED) null else Mark.MISSED) },
                        onDay = { d -> change(d, Cal.next(u.markOf(d))) },
                        onHistory = goHistory,
                        onGrid = { gridRect = it },
                        footer = {
                            // Uma vez por mês no máximo: a história está só neste celular há tempo demais.
                            val s = u.state
                            val show = AutoBackup.stale(s) && s.done.size >= 30 && System.currentTimeMillis() - s.backupNudge > 30L * 24 * 60 * 60 * 1000
                            androidx.compose.animation.AnimatedVisibility(show, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()) {
                                BackupNudge(
                                    s,
                                    onSave = { tick(); vm.dismissBackupNudge(); if (s.backupUri.isNotEmpty()) backupNow() else sheetOpen = true },
                                    onDismiss = { tick(); vm.dismissBackupNudge() },
                                )
                            }
                        },
                    ) else HistoriaPage(
                        u, histScroll, period, onPeriod = { tick(); period = it },
                        map = {
                            YearPanel(u, selected, annoBack, ripple, period, onRect = { yearRect = it }, onShare = { share("ano") },
                                onMonthLabel = { selected = null; tick(); monthFrom = yearRect; month = it },
                                onDay = { d -> if (d == selected) change(d, Cal.next(u.markOf(d))) else { selected = d; tick(); sound(Sfx.TOQUE) } })
                        },
                        onMonth = { m, r -> tick(); monthFrom = r; month = m },
                        onRetro = { m, r -> tick(); retroFrom = r; retro = m },
                    )
                }
                if (u != null) CompactBar(u, visible = pager.currentPage == 1, histScroll)
            }
        }

        // Escurecimento da folha.
        if (sheet.value > 0f) Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = 0.32f * sheet.value * (1f - sheetDrag) }.background(Color.Black)
                .clickable(remember { MutableInteractionSource() }, null) { sheetOpen = false },
        )

        val m = shownMonth
        if (m != null && u != null) ClipOverlay(monthFrom, monthReveal.value) {
            MonthScreen(
                u, m, monthBack, closing = month == null, onMonth = { month = it }, onClose = { month = null },
                onDay = { d -> change(d, Cal.next(u.markOf(d))) }, onRetro = { tick(); retroFrom = Rect.Zero; retro = it },
            )
        }
        val r = shownRetro
        if (r != null && u != null) ClipOverlay(retroFrom, retroReveal.value) {
            RetroScreen(u, r, retroBack, onClose = { tick(); closeRetro() }, onShare = { share("mes") })
        }

        if (sheet.value > 0f && u != null) SettingsHost(
            progress = sheet.value, drag = sheetDrag, onDrag = { sheetDrag = it }, onRelease = { fast -> if (sheetDrag > 0.28f || fast) sheetOpen = false else sheetDrag = 0f },
            onClose = { tick(); sheetOpen = false },
        ) {
            SettingsContent(
                u.state, unrestricted, lastBackup,
                SettingsActions(
                    onName = vm::setName, onGoal = vm::setGoal, onRest = vm::setRest,
                    onReminders = { list ->
                        if (list.size > u.state.reminders.size && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        vm.setReminders(list)
                    },
                    onPalette = { p -> vm.setPalette(p); if (u.state.sounds) Sounds.play(ctx, p, Sfx.COR) }, onTheme = vm::setTheme, onMonday = vm::setMondayFirst,
                    onHaptics = vm::setHaptics, onCelebrate = vm::setCelebrate,
                    onSounds = { on -> vm.setSounds(on); if (on) Sounds.play(ctx, u.state.palette, Sfx.FEITO) }, onHour = vm::setHour,
                    onExport = { exporter.launch("habito-backup-${LocalDate.now()}.json") },
                    onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onBattery = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) },
                    onSeal = { day, text -> vm.sealLetter(day, text) },
                    onUnseal = { vm.sealLetter(u.state.sealed?.forDay ?: 30, "") },
                    onBackupFreq = { f ->
                        if (f > 0 && u.state.backupUri.isEmpty()) { pendingFreq = f; folderPicker.launch(null) } else vm.setBackup(f)
                    },
                    onBackupFolder = { pendingFreq = maxOf(u.state.backupFreq, 1); folderPicker.launch(null) },
                    onBackupNow = { backupNow() },
                    onPaper = vm::setPaper, onIconFollows = vm::setIconFollows,
                    onShareError = if (CrashLog.exists(ctx)) ({ CrashLog.share(ctx) }) else null,
                    onAddTile = if (Build.VERSION.SDK_INT >= 33) ({ HabitTileService.requestAdd(ctx) { added -> if (added) toast("Bloco adicionado") } }) else null,
                    onAddWidget = { small ->
                        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
                        if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(android.content.ComponentName(ctx, if (small) HabitWidgetSmallReceiver::class.java else HabitWidgetReceiver::class.java), null, null)
                        else toast("Este launcher não aceita adicionar por aqui: use a lista de widgets")
                    },
                    tick = ::tick,
                ),
            )
        }


        // Dia 1: a pergunta. Marco com frase lacrada: a frase abre.
        if (u != null) androidx.compose.animation.AnimatedVisibility(u.empty && !u.state.letterAsked, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            FirstUse { name, goal, letter -> tick(); vm.setName(name); vm.setGoal(goal); if (letter != null) vm.sealLetter(30, letter) else vm.skipLetter() }
        }
        val letter = opening
        androidx.compose.animation.AnimatedVisibility(letter != null, enter = androidx.compose.animation.fadeIn(tween(350)), exit = androidx.compose.animation.fadeOut(tween(250))) {
            val l = letter ?: return@AnimatedVisibility
            LetterReveal(l, u?.today ?: LocalDate.now()) { next -> tick(); vm.closeLetter(l, next); opening = null }
        }

        restore?.let { p -> if (u != null) RestoreConfirm(p, u.state, onConfirm = { tick(); applyRestore(p) }, onCancel = { restore = null }) }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Space.l)) { data ->
            UndoBar(data.visuals.message, data.visuals.actionLabel) { data.performAction() }
        }

        // Marco: o ano se solta do mapa e forma o glifo da cor (acima de tudo, até do aviso de desfazer).
        glyphMarco?.let { m -> if (u != null) YearGlyph(u, gridRect, m, startDelay = if (reduce) 560 else LAUNCH_RISE + 520) { glyphMarco = null } }

        // Motion: partículas, o véu do marco e o voo do ano até o mapa, por cima de tudo.
        FxLayer(fx, pal)
        SideEffect { InkFx.armed = sheet.value > 0f }

        // Abertura: o glifo da cor se desenha, encolhe e some enquanto a grade acende.
        var showOpen by remember { mutableStateOf(!OpenOnce.done && !reduce) }
        if (showOpen && LocalRevealed.current && u != null) OpeningGlyph(
            pal,
            onStart = {
                OpenOnce.done = true; fx.openAt = uptime()
                val prefs = ctx.getSharedPreferences("motion", android.content.Context.MODE_PRIVATE)
                val last = prefs.getLong("abrir_at", 0L); val now = System.currentTimeMillis()
                val newDay = java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDate() != java.time.LocalDate.now()
                if (u.state.sounds && (newDay || now - last > 4 * 3600_000L)) { Sounds.play(ctx, u.state.palette, Sfx.ABRIR); prefs.edit().putLong("abrir_at", now).apply() }
            },
            onEnd = { showOpen = false },
        )
    }
}

/** Antes de restaurar: o que chega e o que sai, em números. Nada é gravado até confirmar. */
@Composable
private fun RestoreConfirm(p: Backup.Preview, now: HabitState, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val pal = Tokens
    BackHandler(onBack = onCancel)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(remember { MutableInteractionSource() }, null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(Space.gutter).fillMaxWidth().clip(RoundedCornerShape(Radius.panel)).background(pal.bg)
                .clickable(remember { MutableInteractionSource() }, null) {}.padding(22.dp),
        ) {
            Text("RESTAURAR", style = Type.caps, color = pal.text2)
            Text(p.state.taskName, style = Type.title, color = pal.ink, modifier = Modifier.padding(top = 10.dp))
            val at = if (p.exportedAt > 0) java.time.Instant.ofEpochMilli(p.exportedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() else null
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Figure("${p.state.done.size}", "dias no arquivo", Type.number, Modifier.weight(1f))
                Figure("${now.done.size}", "dias agora", Type.number, Modifier.weight(1f))
            }
            Text(
                (at?.let { "Cópia de ${Cal.shortDate(it)}. " } ?: "") + "O histórico atual é substituído; dá para desfazer logo depois.",
                style = Type.body, color = pal.text2, modifier = Modifier.padding(top = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(Radius.control)).border(1.dp, pal.rule, RoundedCornerShape(Radius.control)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                    Text("Cancelar", style = Type.bodyL, color = pal.ink, fontWeight = FontWeight.Medium)
                }
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(Radius.control)).background(pal.ink).clickable(onClick = onConfirm), contentAlignment = Alignment.Center) {
                    Text("Restaurar", style = Type.bodyL, color = pal.bg, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** "40 dias sem cópia · Guardar", calmo, no fim do Hoje. Aparece uma vez por mês no máximo. */
@Composable
private fun BackupNudge(s: HabitState, onSave: () -> Unit, onDismiss: () -> Unit) {
    val pal = Tokens
    val days = AutoBackup.daysWithout(s)
    Row(
        Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.control)).background(pal.surface)
            .border(1.dp, pal.rule, RoundedCornerShape(Radius.control)).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(7.dp)) { drawCircle(pal.gold) }
        Spacer(Modifier.width(10.dp))
        Text(if (s.backupAt == 0L) "Nenhuma cópia ainda" else "$days dias sem cópia", style = Type.body, color = pal.text2, modifier = Modifier.weight(1f))
        Text("Guardar", style = Type.body, color = pal.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onSave).padding(horizontal = 12.dp, vertical = 14.dp))
        Box(Modifier.size(44.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_x), "Dispensar", Modifier.size(14.dp), tint = pal.text3)
        }
    }
}

/** Abre ou fecha uma sobreposição recortada: devolve o que fica montado (o alvo, ou nada depois de fechar). */
private suspend fun <T> reveal(target: T?, shown: T?, anim: Animatable<Float, *>, reduce: Boolean, set: (T?) -> Unit): T? {
    if (target != null) {
        val opening = shown == null
        set(target)
        if (opening) {
            anim.snapTo(0f)
            withFrameNanos { } // começa depois do primeiro quadro já montado
            if (reduce) anim.snapTo(1f) else anim.animateTo(1f, tween(460, easing = OPEN))
        }
        return target
    }
    if (shown != null) { if (reduce) anim.snapTo(0f) else anim.animateTo(0f, tween(380, easing = CLOSE)) }
    return null
}

/** O retângulo de origem cresce até a tela: mesma cor e mesmos cantos do bloco no início, o fundo no fim. */
@Composable
private fun ClipOverlay(from: Rect, t: Float, content: @Composable () -> Unit) {
    val pal = Tokens
    Box(
        Modifier.fillMaxSize().drawWithContent {
            val o = if (from == Rect.Zero) Rect(size.width * 0.1f, size.height * 0.45f, size.width * 0.9f, size.height * 0.55f) else from
            val r = Rect(lerp(o.left, 0f, t), lerp(o.top, 0f, t), lerp(o.right, size.width, t), lerp(o.bottom, size.height, t))
            val path = Path().apply { addRoundRect(RoundRect(r, CornerRadius(lerp(Radius.panel.toPx(), 0f, t)))) }
            clipPath(path) {
                drawRect(lerp(pal.surface, pal.bg, (t * 1.6f).coerceAtMost(1f)))
                this@drawWithContent.drawContent()
            }
        },
    ) { content() }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Abas em caixa alta larga. A régua de tinta estica até a aba escolhida como o marcador do trilho: a borda da
 * frente corre, a de trás alcança. A engrenagem gira um pouco com a folha aberta.
 */
@Composable
private fun Tabs(page: Int, sheetOpen: Boolean, onTab: (Int) -> Unit, onGear: () -> Unit) {
    val pal = Tokens
    val reduce = LocalReduceMotion.current
    val bounds = remember { arrayOf(0f to 0f, 0f to 0f) }
    var ready by remember { mutableStateOf(false) }
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    LaunchedEffect(page, ready) {
        if (!ready) return@LaunchedEffect
        val (l, r) = bounds[page]
        if (left.value == 0f && right.value == 0f || reduce) { left.snapTo(l); right.snapTo(r); return@LaunchedEffect }
        val toRight = l > left.value
        launch { left.animateTo(l, if (toRight) Motion.trail else Motion.lead) }
        launch { right.animateTo(r, if (toRight) Motion.lead else Motion.trail) }
    }
    val gutter = with(LocalDensity.current) { Space.gutter.toPx() }
    val gear by animateFloatAsState(if (sheetOpen) 60f else 0f, if (reduce) tween(0) else spring(0.7f, 300f), label = "gear")
    Column {
        Box(Modifier.fillMaxWidth().height(46.dp)) {
            Row(Modifier.fillMaxSize().padding(start = Space.gutter, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Hoje", "História").forEachIndexed { i, label ->
                    val color by animateColorAsState(if (i == page) pal.ink else pal.text3, motion(250), label = "tab")
                    Box(
                        Modifier.fillMaxHeight()
                            .onGloballyPositioned { c -> val x = c.positionInParent().x + gutter; bounds[i] = x to x + c.size.width; if (i == 1) ready = true }
                            .semantics { role = Role.Tab; selected = i == page }
                            .clickable(remember { MutableInteractionSource() }, null) { onTab(i) },
                        contentAlignment = Alignment.Center,
                    ) { Text(label.uppercase(), style = Type.caps, color = color) }
                    Spacer(Modifier.width(24.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(Radius.control)).clickable(onClick = onGear),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_gear), "Ajustes", Modifier.size(20.dp).graphicsLayer { rotationZ = gear }, tint = pal.ink) }
            }
            // Régua: lida só no desenho, sem recompor a cada quadro.
            Box(
                Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomStart).drawBehind {
                    if (right.value > left.value) drawRect(pal.ink, Offset(left.value, 0f), Size(right.value - left.value, size.height))
                },
            )
        }
        Hairline()
    }
}

/** Ao rolar a História, a tarefa e a sequência ficam fixas no topo. */
@Composable
private fun CompactBar(u: HabitUi, visible: Boolean, scroll: ScrollState) {
    val pal = Tokens
    val density = LocalDensity.current
    val start = with(density) { 90.dp.toPx() }
    val span = with(density) { 40.dp.toPx() }
    val progress by remember(visible) { derivedStateOf { if (!visible) 0f else ((scroll.value - start) / span).coerceIn(0f, 1f) } }
    if (progress == 0f) return
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = progress; translationY = (1f - progress) * -8.dp.toPx() }.background(pal.bg)) {
        Row(Modifier.height(48.dp).padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
            Text(u.state.taskName, color = pal.ink, style = Type.bodyL, maxLines = 1, modifier = Modifier.weight(1f))
            RollingNumber("${u.streak}", pal.ink, Type.compactNum)
            Spacer(Modifier.width(6.dp))
            Text(if (u.weekly) (if (u.streak == 1) "semana" else "semanas") else (if (u.streak == 1) "dia" else "dias"), color = pal.text2, style = Type.body)
        }
        Hairline()
    }
}

/** O bloco do mapa na História: título, total no período, compartilhar, o ano e a legenda. */
@Composable
private fun YearPanel(
    u: HabitUi, selected: LocalDate?, back: Float, ripple: Int, period: Int,
    onRect: (Rect) -> Unit, onShare: () -> Unit, onMonthLabel: (YearMonth) -> Unit, onDay: (LocalDate) -> Unit,
) {
    val pal = Tokens
    val total = remember(u, period) { doneIn(u, period) }
    Panel(Modifier.onGloballyPositioned { onRect(it.boundsInRoot()) }, padding = PaddingValues(start = 12.dp, top = 16.dp, end = 0.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Caps("Mapa", modifier = Modifier.weight(1f))
            RollingLabel(if (total == 1) "1 feito" else "$total feitos", Type.caption, pal.text3)
            if (!u.empty) Box(
                Modifier.padding(start = 4.dp)
                    .layout { m, c -> val p = m.measure(c); val h = 14.dp.roundToPx(); layout(p.width, h) { p.place(0, (h - p.height) / 2) } }
                    .size(36.dp).clip(RoundedCornerShape(Radius.control)).clickable(onClick = onShare),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_share), "Compartilhar o ano", Modifier.size(16.dp), tint = pal.text2) }
        }
        Spacer(Modifier.height(6.dp))
        Box {
            Box(Modifier.graphicsLayer { alpha = if (u.empty) Alpha.dim else 1f }) {
                YearHeatmap(u, selected, back, celebrate = ripple, period = period, onMonth = onMonthLabel, onTap = onDay)
            }
            if (u.empty) Invite(Modifier.matchParentSize())
        }
        Spacer(Modifier.height(Space.m))
        Box(Modifier.padding(end = 14.dp)) { Legend(hint = selected != null) }
    }
}

/**
 * A folha dos Ajustes: sobe de baixo com mola, alça para arrastar e fechar, título grande e o conteúdo rolando.
 * [drag] é a fração arrastada para baixo (0..1).
 */
@Composable
private fun SettingsHost(
    progress: Float, drag: Float, onDrag: (Float) -> Unit, onRelease: (Boolean) -> Unit, onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pal = Tokens
    var height by remember { mutableFloatStateOf(1f) }
    var velocity by remember { mutableFloatStateOf(0f) }
    val state = rememberDraggableState { d -> onDrag(((drag * height) + d).coerceAtLeast(0f) / height) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .onGloballyPositioned { height = it.size.height.toFloat().coerceAtLeast(1f) }
                .layout { m, c ->
                    val p = m.measure(c)
                    layout(p.width, p.height) { p.place(0, ((1f - progress) * p.height * 1.05f + drag * p.height).roundToInt()) }
                }
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(pal.bg)
                .clickable(remember { MutableInteractionSource() }, null) { },
        ) {
            Box(
                Modifier.fillMaxWidth().height(30.dp).draggable(
                    state, Orientation.Vertical,
                    onDragStopped = { v -> velocity = v; onRelease(v > 1800f) },
                ),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(pal.line.copy(alpha = 0.55f))) }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ajustes", style = Type.monthTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp)), color = pal.ink, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(Radius.control)).background(pal.surface).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_x), "Fechar ajustes", Modifier.size(18.dp), tint = pal.ink)
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.gutter).padding(bottom = 24.dp)) {
                content()
            }
        }
    }
}
