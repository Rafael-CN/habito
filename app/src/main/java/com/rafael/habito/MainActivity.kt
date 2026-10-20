package com.rafael.habito

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.remember
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Só a moldura: abertura, flags de movimento e a tela. Estado e ações vivem no [HabitViewModel]. */
class MainActivity : ComponentActivity() {
    private val vm: HabitViewModel by viewModels()
    private val revealed = mutableStateOf(false)
    private val reduceMotion = mutableStateOf(false)

    private fun animationsOff() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /**
     * O ícone só troca com o app fora de cena: desligar o alias pelo qual o app foi aberto, com ele na tela,
     * faz a One UI fechar a tela (parece um crash) e às vezes tira o atalho da tela inicial.
     */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        val app = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { IconSwitcher.apply(app, HabitStore.load(app)) } }
    }

    /** O dia pode virar com o app aberto: a cada 30 s confere se o "hoje" mudou e recarrega. */
    private fun watchDay() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    delay(30_000)
                    val u = vm.ui.value ?: continue
                    if (u.state.today != u.today) vm.refresh()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reduceMotion.value = animationsOff() // "remover animações" zera a escala de animação do sistema
        vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Abertura: as células do ícone acendem em diagonal; segura só o tempo da animação.
        val opened = SystemClock.uptimeMillis()
        installSplashScreen().apply {
            // Com motion, a abertura é o glifo da cor (OpeningGlyph): o splash sai assim que o app desenha.
            setKeepOnScreenCondition { val t = SystemClock.uptimeMillis() - opened; (animationsOff() && t < 560) || (vm.ui.value == null && t < 400) } // sem flash da cor errada
            setOnExitAnimationListener { p ->
                revealed.value = true // a tela entra enquanto a abertura se desfaz
                // Sem animações, ou com o app já aberto, o sistema pode não criar o ícone: aí só remove.
                val icon = runCatching { p.iconView }.getOrNull()
                if (icon == null || animationsOff()) {
                    p.remove()
                } else {
                    p.view.animate().alpha(0f).setDuration(120).withEndAction { p.remove() }.start()
                }
            }
        }
        super.onCreate(savedInstanceState)
        // Aberto como "configurar widget": a configuração é opcional, então confirma na hora.
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        debugHooks()
        watchDay()

        // Se a abertura não aparecer (retorno rápido ao app), a tela entra mesmo assim.
        lifecycleScope.launch { delay(1200); revealed.value = true }

        enableEdgeToEdge()
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val mode = ui?.state?.themeMode ?: 0
            // Tema escolhido no app: os ícones da barra de status acompanham, não o sistema.
            val dark = isDark(mode)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val fx = remember { FxState() }
            CompositionLocalProvider(LocalRevealed provides revealed.value, LocalReduceMotion provides reduceMotion.value, LocalFx provides fx) {
                HabitTheme(ui?.state?.palette ?: "verde", mode, ui?.state?.paper ?: false) { HomeScreen(vm) }
            }
        }
    }

    /** Só em build debug: `--es seed33 volta|carta|tropeco|novo` monta os cenários da 3.3; `--ez seed_demo true` preenche um histórico; `--ez test_reminder true` mostra o lembrete; `--ez show_retro true` abre a retrospectiva do mês passado. */
    private fun debugHooks() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (intent?.getBooleanExtra("seed_demo", false) == true) {
            lifecycleScope.launch { HabitStore.seedDemo(applicationContext); HabitWidget().updateAll(applicationContext) }
        }
        intent?.getStringExtra("seed33")?.let { kind -> lifecycleScope.launch { HabitStore.seed33(applicationContext, kind); HabitWidget().updateAll(applicationContext) } }
        // `--es palette rosa --ez paper true`: troca a cor (e o papel) sem passar pelos Ajustes.
        intent?.getStringExtra("palette")?.let { p -> lifecycleScope.launch { HabitStore.setPalette(applicationContext, p); HabitStore.setPaper(applicationContext, intent?.getBooleanExtra("paper", false) == true); IconSwitcher.apply(applicationContext, HabitStore.load(applicationContext)); HabitWidget().updateAll(applicationContext) } }
        if (intent?.getBooleanExtra("show_retro", false) == true) RetroDebug.force = true
        // `--ei render_widget <largura dp>`: a grade do widget, já pintada, em files/widget-<dp>.png (escuro).
        intent?.getIntExtra("render_widget", 0)?.takeIf { it > 0 }?.let { w ->
            lifecycleScope.launch {
                val s = HabitStore.load(applicationContext)
                val g = HeatmapRenderer.render(s, w.toFloat(), w * 0.38f, resources.displayMetrics.density)
                val base = (g.levels.firstOrNull { it != null } ?: g.ring)!!
                val out = android.graphics.Bitmap.createBitmap(base.width, base.height, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(out); c.drawColor(0xFF151517.toInt())
                val p = android.graphics.Paint()
                fun layer(b: android.graphics.Bitmap?, color: Int) { if (b != null) { p.colorFilter = android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN); c.drawBitmap(b, 0f, 0f, p) } }
                val lv = intArrayOf(0xFF242428.toInt(), 0xFF0F3B20.toInt(), 0xFF166B35.toInt(), 0xFF25A050.toInt(), 0xFF4CD27C.toInt())
                g.levels.forEachIndexed { i, b -> layer(b, lv[i]) }
                layer(g.rest, 0xFF8C8C8C.toInt()); layer(g.trope, 0xFF8C8C8C.toInt()); layer(g.scar, 0xFFD9AB33.toInt())
                layer(g.missed, 0xFFF06058.toInt()); layer(g.ring, 0xFFECECEC.toInt())
                java.io.File(filesDir, "widget-$w.png").outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        if (intent?.getBooleanExtra("test_reminder", false) == true) {
            lifecycleScope.launch { Reminder.notify(applicationContext, HabitStore.load(applicationContext)) }
        }
    }
}
