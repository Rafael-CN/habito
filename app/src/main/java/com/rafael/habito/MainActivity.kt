package com.rafael.habito

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

    override fun onResume() {
        super.onResume()
        reduceMotion.value = animationsOff() // "remover animações" zera a escala de animação do sistema
        vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Abertura: as células do ícone acendem em diagonal; segura só o tempo da animação.
        val opened = SystemClock.uptimeMillis()
        installSplashScreen().apply {
            setKeepOnScreenCondition { SystemClock.uptimeMillis() - opened < 560 }
            setOnExitAnimationListener { p ->
                revealed.value = true // a tela entra enquanto a abertura se desfaz
                // Sem animações, ou com o app já aberto, o sistema pode não criar o ícone: aí só remove.
                val icon = runCatching { p.iconView }.getOrNull()
                if (icon == null || animationsOff()) {
                    p.remove()
                } else {
                    icon.animate().scaleX(1.12f).scaleY(1.12f).setDuration(200).start()
                    p.view.animate().alpha(0f).setDuration(200).withEndAction { p.remove() }.start()
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
            CompositionLocalProvider(LocalRevealed provides revealed.value, LocalReduceMotion provides reduceMotion.value) {
                HabitTheme(ui?.state?.palette ?: "verde", mode) { HomeScreen(vm) }
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
        if (intent?.getBooleanExtra("show_retro", false) == true) RetroDebug.force = true
        if (intent?.getBooleanExtra("test_reminder", false) == true) {
            lifecycleScope.launch { Reminder.notify(applicationContext, HabitStore.load(applicationContext)) }
        }
    }
}
