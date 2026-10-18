package com.rafael.habito

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Backup num arquivo JSON legível, escolhido pelo usuário (Drive, Downloads, onde quiser). */
object Backup {
    private const val APP = "habito"

    suspend fun export(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val s = HabitStore.load(ctx)
        val json = JSONObject()
            .put("app", APP)
            .put("version", 3)
            .put("taskName", s.taskName)
            .put("rolloverHour", s.rolloverHour)
            .put("weeks", s.weeks)
            .put("reminderHour", s.reminderHour)
            .put("goal", s.goal)
            .put("rest", JSONArray(s.rest.sorted()))
            .put("reminders", HabitStore.encodeRems(s.reminders))
            .put("palette", s.palette)
            .put("themeMode", s.themeMode)
            .put("mondayFirst", s.mondayFirst)
            .put("haptics", s.haptics)
            .put("celebrate", s.celebrate)
            .put("letters", Letter.encode(s.letters))
            .put("done", JSONArray(s.done.sorted().map(LocalDate::toString)))
            .put("missed", JSONArray(s.missed.sorted().map(LocalDate::toString)))
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toString(2).toByteArray()) }
    }

    /** Devolve quantos dias feitos foram restaurados. Lança exceção se o arquivo não for um backup do Hábito. */
    suspend fun import(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val json = JSONObject(text)
        require(json.optString("app") == APP) { "não é um backup do Hábito" }
        fun dates(key: String): Set<LocalDate> {
            val arr = json.optJSONArray(key) ?: return emptySet()
            return (0 until arr.length()).map { LocalDate.parse(arr.getString(it)) }.toSet()
        }
        val s = HabitState(
            done = dates("done"),
            missed = dates("missed"),
            rolloverHour = json.optInt("rolloverHour", 0),
            taskName = json.optString("taskName", HabitStore.DEFAULT_NAME),
            weeks = json.optInt("weeks", 0),
            reminderHour = json.optInt("reminderHour", -1),
            goal = json.optInt("goal", 0),
            rest = json.optJSONArray("rest")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet(),
            // Backups antigos só tinham a hora do lembrete único.
            reminders = if (json.has("reminders")) HabitStore.decodeRems(json.getString("reminders"))
            else json.optInt("reminderHour", -1).let { if (it >= 0) listOf(Rem(it * 60)) else emptyList() },
            palette = json.optString("palette", "verde"),
            themeMode = json.optInt("themeMode", 0),
            mondayFirst = json.optBoolean("mondayFirst", false),
            haptics = json.optBoolean("haptics", true),
            celebrate = json.optBoolean("celebrate", true),
            letters = Letter.decode(json.optString("letters", "")),
        )
        HabitStore.replaceAll(ctx, s)
        s.done.size
    }
}
