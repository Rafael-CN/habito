package com.rafael.habito

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Cópia automática do histórico num arquivo fixo (habito-backup.json) dentro de uma pasta que a pessoa escolhe.
 * Sem conta e sem nuvem nossa: se a pasta for o Drive, a nuvem é dela.
 */
object AutoBackup {
    const val FILE = "habito-backup.json"
    private const val WORK = "auto-backup"
    private const val DAY = 24L * 60 * 60 * 1000

    /** Intervalo de cada frequência, em dias (0 = desligado). */
    fun periodDays(freq: Int): Int = when (freq) { 1 -> 1; 2 -> 7; 3 -> 30; else -> 0 }

    val FREQ_LABELS = listOf("nunca", "todo dia", "toda semana", "todo mês")

    /** A cópia está atrasada: passou de duas vezes o intervalo, ou nunca houve uma e já existe um mês de história. */
    fun stale(s: HabitState, now: Long = System.currentTimeMillis()): Boolean {
        val p = periodDays(s.backupFreq)
        return if (p > 0) s.backupAt > 0 && now - s.backupAt > 2 * p * DAY || s.backupAt == 0L
        else s.done.size >= 30 && (s.backupAt == 0L || now - s.backupAt > 30 * DAY)
    }

    /** Dias desde a última cópia (ou desde o primeiro dia feito, se nunca houve cópia). */
    fun daysWithout(s: HabitState, now: Long = System.currentTimeMillis()): Int =
        if (s.backupAt > 0) ((now - s.backupAt) / DAY).toInt()
        else s.done.minOrNull()?.let { java.time.temporal.ChronoUnit.DAYS.between(it, s.today).toInt() } ?: 0

    /** "Documentos/Hábito" a partir da árvore escolhida. */
    fun folderName(uri: String): String = runCatching {
        val id = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
        val path = id.substringAfter(':', "").ifEmpty { id.substringBefore(':') }
        path.split('/').filter { it.isNotEmpty() }.takeLast(2).joinToString("/").ifEmpty { "pasta escolhida" }
    }.getOrDefault("pasta escolhida")

    /** Guarda a permissão da pasta escolhida para sempre (sobrevive a reinícios). */
    fun keep(ctx: Context, tree: Uri) {
        ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    /** Escreve a cópia agora. Devolve false se a pasta sumiu ou perdeu a permissão. */
    suspend fun write(ctx: Context): Boolean {
        val s = HabitStore.load(ctx)
        if (s.backupUri.isEmpty()) return false
        return runCatching {
            val tree = Uri.parse(s.backupUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val file = find(ctx, tree) ?: DocumentsContract.createDocument(ctx.contentResolver, parent, "application/json", FILE)!!
            Backup.export(ctx, file)
            HabitStore.setBackupAt(ctx, System.currentTimeMillis())
            true
        }.getOrDefault(false)
    }

    private fun find(ctx: Context, tree: Uri): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ctx.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == FILE) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
        }
        return null
    }

    /** Agenda (ou cancela) a cópia periódica. */
    fun schedule(ctx: Context, s: HabitState) {
        val wm = WorkManager.getInstance(ctx)
        val p = periodDays(s.backupFreq)
        if (p == 0 || s.backupUri.isEmpty()) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(p.toLong(), TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = if (AutoBackup.write(applicationContext)) Result.success() else Result.retry()
}
