package com.rafael.habito

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Bloco nas Configurações Rápidas: tocar marca o dia, tocar de novo desfaz. Tocar e segurar abre o app
 * (o sistema leva ao app pelo filtro QS_TILE_PREFERENCES). Funciona com a tela bloqueada.
 */
class HabitTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() { refresh() }

    override fun onClick() {
        scope.launch {
            val before = HabitStore.load(applicationContext)
            val now = HabitStore.toggleToday(applicationContext, Mark.DONE)
            val after = HabitStore.load(applicationContext)
            if (after.haptics) {
                if (now == Mark.DONE && isMilestone(after.streak(), after.weekly) && after.streak() > before.streak()) Haptics.milestone(applicationContext)
                else Haptics.mark(applicationContext, now == Mark.DONE)
            }
            Reminder.dismiss(applicationContext)
            HabitWidget().updateAll(applicationContext)
            refresh()
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val s = runBlocking { HabitStore.load(applicationContext) }
        val done = s.markOf(s.today) == Mark.DONE
        tile.state = if (done) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = s.taskName
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        if (Build.VERSION.SDK_INT >= 29) {
            val n = s.streak()
            tile.subtitle = if (done) "Feito · $n" else if (s.weekly) "${s.doneThisWeek} de ${s.goal}" else "Pendente"
        }
        tile.updateTile()
    }

    companion object {
        /** Pede ao sistema para mostrar o bloco (Android 13+). Devolve false onde não há como pedir. */
        fun requestAdd(ctx: Context, onResult: (Boolean) -> Unit): Boolean {
            if (Build.VERSION.SDK_INT < 33) return false
            val sbm = ctx.getSystemService(StatusBarManager::class.java)
            sbm.requestAddTileService(
                ComponentName(ctx, HabitTileService::class.java), ctx.getString(R.string.app_name),
                Icon.createWithResource(ctx, R.drawable.ic_tile), ctx.mainExecutor,
            ) { r -> onResult(r == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || r == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) }
            return true
        }

        /** Pede ao sistema para redesenhar o bloco depois de uma marcação fora dele. */
        fun poke(ctx: Context) = runCatching { requestListeningState(ctx, ComponentName(ctx, HabitTileService::class.java)) }
    }
}
