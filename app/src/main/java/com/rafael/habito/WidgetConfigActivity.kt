package com.rafael.habito

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

/**
 * A configuração mora no widget: tocar e segurar → Configurar abre esta tela só para ele. Cada escolha vale na hora
 * (o widget redesenha enquanto você mexe); "Pronto" só fecha.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        // A configuração é opcional: o widget fica mesmo se a pessoa voltar sem mexer.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val flow = HabitStore.flow(applicationContext).stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        enableEdgeToEdge()
        setContent {
            val s by flow.collectAsStateWithLifecycle()
            val state = s ?: return@setContent
            val scope = rememberCoroutineScope()
            HabitTheme(state.palette, state.themeMode, state.paper) {
                WidgetConfig(state.widgets[id] ?: WidgetOpts(), large = isLarge(id), onChange = { o ->
                    scope.launch { HabitStore.setWidget(applicationContext, id, o); HabitWidget().updateAll(applicationContext) }
                }, onDone = { finish() })
            }
        }
    }

    private fun isLarge(id: Int): Boolean = runCatching {
        AppWidgetManager.getInstance(this).getAppWidgetInfo(id).provider.className == HabitWidgetReceiver::class.java.name
    }.getOrDefault(true)
}

@Composable
private fun WidgetConfig(o: WidgetOpts, large: Boolean, onChange: (WidgetOpts) -> Unit, onDone: () -> Unit) {
    val pal = Tokens
    Column(
        Modifier.fillMaxSize().background(pal.bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(top = Space.l, bottom = Space.xl),
    ) {
        Rule()
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text("Este widget", style = Type.monthTitle.copy(fontSize = 44.sp, lineHeight = 44.sp), color = pal.ink, modifier = Modifier.weight(1f))
            Text(if (large) "Ano" else "Compacto", style = Type.caption, color = pal.text3, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text("As mudanças aparecem na tela inicial enquanto você escolhe.", style = Type.body, color = pal.text2, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(18.dp))
        if (large) Option("Semanas no mapa") {
            Track(listOf("Auto", "12", "18", "26"), HabitStore.WEEK_OPTIONS.indexOf(o.weeks).coerceAtLeast(0), { onChange(o.copy(weeks = HabitStore.WEEK_OPTIONS[it])) })
        }
        Option("Mostrar") { Track(listOf("Sequência", "Só o mapa"), if (o.showStreak) 0 else 1, { onChange(o.copy(showStreak = it == 0)) }) }
        Option("Botões") { Track(listOf("✕ e ✓", "Só ✓"), if (o.bothButtons) 0 else 1, { onChange(o.copy(bothButtons = it == 0)) }) }
        Option("Fundo") { Track(listOf("Sólido", "Vidro"), if (o.glass) 1 else 0, { onChange(o.copy(glass = it == 1)) }) }
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(Radius.control)).background(pal.ink).clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) { Text("Pronto", style = Type.bodyL, color = pal.bg, fontWeight = FontWeight.SemiBold) }
        Text(
            "A cor do mapa e o tema vêm dos Ajustes do app.", style = Type.caption, color = pal.text3,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Option(label: String, control: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(label.uppercase(), style = Type.caps, color = Tokens.text2, modifier = Modifier.padding(bottom = 8.dp))
        control()
    }
}
