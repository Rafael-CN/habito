package com.rafael.habito

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cartões para compartilhar, no vocabulário do app: régua, caixa alta larga, número condensado enorme e a grade.
 * Sempre no tema claro (fica bem em qualquer conversa) e em 4:5, o formato que as redes cortam menos.
 */
object ShareImage {
    private const val W = 1080
    private const val H = 1350
    private const val M = 80f
    private val PT = Locale("pt", "BR")
    private val pal = Palettes.Light

    private fun paint(ctx: Context, size: Float, color: Color, weight: Int, width: Int = 100, spacing: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(ctx, R.font.archivo)
            textSize = size
            this.color = color.toArgb()
            fontVariationSettings = "'wght' $weight, 'wdth' $width"
            letterSpacing = spacing
        }

    private fun frame(ctx: Context, caps: String, meta: String): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(pal.bg.toArgb())
        c.drawRect(M, M, W - M, M + 3f, Paint().apply { color = pal.ink.toArgb() })
        c.drawText(caps.uppercase(PT), M, M + 62f, paint(ctx, 28f, pal.ink, 620, 115, 0.1f))
        val mp = paint(ctx, 28f, pal.text3, 400)
        c.drawText(meta, W - M - mp.measureText(meta), M + 62f, mp)
        return bmp to c
    }

    private fun footer(ctx: Context, c: Canvas) {
        c.drawRect(M, H - M - 70f, W - M, H - M - 68f, Paint().apply { color = pal.rule.toArgb() })
        c.drawText("Hábito", M, H - M - 14f, paint(ctx, 28f, pal.ink, 600))
        val t = "um hábito, um quadrado por dia"
        val p = paint(ctx, 28f, pal.text3, 400)
        c.drawText(t, W - M - p.measureText(t), H - M - 14f, p)
    }

    private fun figures(ctx: Context, c: Canvas, y: Float, items: List<Pair<String, String>>) {
        val col = (W - 2 * M) / items.size
        items.forEachIndexed { i, (v, l) ->
            c.drawText(v, M + i * col, y, paint(ctx, 104f, pal.ink, 620, 72, -0.02f))
            c.drawText(l, M + i * col, y + 52f, paint(ctx, 30f, pal.text2, 400))
        }
    }

    private fun cell(c: Canvas, x: Float, y: Float, size: Float, color: Color, missed: Boolean, ring: Color?) {
        val r = size * 0.14f
        c.drawRoundRect(RectF(x, y, x + size, y + size), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
        if (missed) c.drawCircle(x + size / 2, y + size / 2, size * 0.14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = pal.miss.toArgb(); alpha = 190 })
        if (ring != null) {
            val o = size * 0.14f
            c.drawRoundRect(RectF(x - o, y - o, x + size + o, y + size + o), r + o, r + o,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ring.toArgb(); style = Paint.Style.STROKE; strokeWidth = size * 0.12f })
        }
    }

    /** "• não fiz ▢ marco" à esquerda, os tons com seus valores à direita, como no app. */
    private fun legend(ctx: Context, c: Canvas, y: Float) {
        val p = paint(ctx, 26f, pal.text2, 400)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        var x = M
        fill.color = pal.miss.toArgb(); c.drawCircle(x + 6f, y - 9f, 6f, fill); x += 20f
        c.drawText("não fiz", x, y, p); x += p.measureText("não fiz") + 34f
        c.drawStar(x + 12f, y - 9f, 22f, pal.levels[4].toArgb()); x += 32f
        c.drawText("marco", x, y, p)
        var r = W - M
        listOf(4 to "7+", 3 to "4–6", 2 to "2–3", 1 to "1").forEach { (l, t) ->
            r -= p.measureText(t); c.drawText(t, r, y, p)
            r -= 30f; cell(c, r, y - 20f, 22f, pal.levels[l], false, null)
            r -= 22f
        }
    }

    /** O ano: a sequência enorme, as 53 semanas e três números. */
    fun year(ctx: Context, u: HabitUi): Bitmap {
        val (bmp, c) = frame(ctx, u.state.taskName, "últimos 12 meses")
        val unit = if (u.weekly) (if (u.streak == 1) "semana\nseguida" else "semanas\nseguidas") else (if (u.streak == 1) "dia\nseguido" else "dias\nseguidos")
        val hero = paint(ctx, 420f, pal.ink, 650, 68, -0.045f)
        c.drawText("${u.streak}", M - 14f, 560f, hero)
        val side = paint(ctx, 44f, pal.ink, 500)
        unit.lines().forEachIndexed { i, l -> c.drawText(l, W * 0.62f, 500f + i * 54f, side) }

        // grade: 53 colunas na largura útil, rótulos de mês em cima
        val step = (W - 2 * M) / YEAR_WEEKS
        val size = step * 0.8f
        val top = 690f
        val month = paint(ctx, 24f, pal.text2, 500)
        Cal.monthLabels(u.start, YEAR_WEEKS).filter { it.first >= 2 }.forEach { (col, label) -> c.drawText(label, M + col * step, top - 18f, month) }
        legend(ctx, c, top + 7 * step + 56f)
        for (i in 0 until YEAR_WEEKS * 7) {
            val d = u.start.plusDays(i.toLong())
            if (d.isAfter(u.today)) continue
            val x = M + (i / 7) * step; val y = top + (i % 7) * step
            when {
                d in u.milestones -> c.drawStar(x + size / 2, y + size / 2, size * Motion.STAR, pal.levels[4].toArgb())
                d in u.state.scars -> { cell(c, x, y, size, pal.levels[0], false, null); c.drawStitch(x, y, size, step - size, pal.gold.toArgb()) }
                d in u.state.tropes -> { cell(c, x, y, size, pal.levels[0], false, null); c.drawTrope(x, y, size, pal.text3.toArgb()) }
                else -> cell(c, x, y, size, pal.levels[u.level(d)], u.markOf(d) == Mark.MISSED, null)
            }
        }
        figures(ctx, c, 1080f, listOf("${u.best}" to "recorde", "${u.year}" to "dias no ano", "${u.pct30}%" to "nos últimos 30"))
        footer(ctx, c)
        return bmp
    }

    /** O mês da retrospectiva: nome grande, quantos dias, o calendário. */
    fun month(ctx: Context, u: HabitUi, m: YearMonth, r: Retro): Bitmap {
        val (bmp, c) = frame(ctx, "Retrospectiva · ${u.state.taskName}", "${m.year}")
        val name = m.format(DateTimeFormatter.ofPattern("MMMM", PT)).replaceFirstChar { it.uppercase() }
        c.drawText(name, M - 8f, 310f, paint(ctx, 176f, pal.ink, 640, 72, -0.035f))
        c.drawText("${r.done}", M - 8f, 510f, paint(ctx, 200f, pal.ink, 650, 68, -0.045f))
        val side = paint(ctx, 40f, pal.text2, 400)
        c.drawText("dias feitos", W * 0.42f, 440f, side)
        c.drawText("de ${r.possible}", W * 0.42f, 488f, side)
        // calendário: 7 colunas, domingo primeiro
        val gap = 10f
        val size = (W - 2 * M - 6 * gap) / 7
        val rows = (Cal.rowOf(m.atDay(1)) + m.lengthOfMonth() + 6) / 7
        val small = minOf(size * 0.62f, (980f - 572f - (rows - 1) * gap) / rows) // cabe em 5 ou 6 semanas sem encostar nos números
        val top = 572f
        val offset = Cal.rowOf(m.atDay(1))
        val num = paint(ctx, 24f, pal.text2, 500)
        for (day in 1..m.lengthOfMonth()) {
            val i = day - 1 + offset
            val d = m.atDay(day)
            val x = M + (i % 7) * (size + gap)
            val y = top + (i / 7) * (small + gap)
            val out = d.isAfter(u.today)
            val color = if (out) pal.surface else pal.levels[u.level(d)]
            val rr = 4f
            c.drawRoundRect(RectF(x, y, x + size, y + small), rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
            num.color = (if (out) pal.text3 else pal.levelInk[u.level(d)]).toArgb()
            c.drawText("$day", x + 12f, y + 34f, num)
            if (!out && u.markOf(d) == Mark.MISSED) c.drawCircle(x + size - 16f, y + small - 16f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = pal.miss.toArgb() })
        }
        figures(ctx, c, 1120f, listOf("${r.pct}%" to "do mês", "${r.longest}" to "maior sequência", r.deltaLabel to "vs. mês anterior"))
        footer(ctx, c)
        return bmp
    }

    /** Grava no cache e abre o seletor de compartilhamento do sistema. */
    suspend fun share(ctx: Context, bmp: Bitmap, name: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            File(dir, "$name.png").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, null))
    }
}
