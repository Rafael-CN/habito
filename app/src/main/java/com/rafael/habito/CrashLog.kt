package com.rafael.habito

import android.content.Context
import android.content.Intent
import java.io.File
import java.time.LocalDateTime

/**
 * Sem nuvem, um erro no aparelho some sem rastro. Aqui o último erro fica num arquivo local;
 * em Ajustes > Avançado dá para mandá-lo para você mesmo. Nada sai sem o seu toque.
 */
object CrashLog {
    private const val FILE = "ultimo-erro.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                File(app.filesDir, FILE).writeText("Hábito ${BuildConfigLite.version(app)} · ${LocalDateTime.now()}\n${e.stackTraceToString()}")
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun exists(ctx: Context): Boolean = File(ctx.filesDir, FILE).exists()

    /** Abre o compartilhar do sistema com o texto do erro e apaga o arquivo. */
    fun share(ctx: Context) {
        val f = File(ctx.filesDir, FILE)
        val text = runCatching { f.readText() }.getOrNull() ?: return
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        f.delete()
    }
}

/** A versão do app sem depender do BuildConfig. */
object BuildConfigLite {
    fun version(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
}
