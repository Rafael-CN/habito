package com.rafael.habito

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.floor
import kotlin.math.min

/**
 * Camadas da grade, uma máscara (ALPHA_8) por tom. O widget pinta cada camada com uma cor de recurso,
 * que o launcher resolve no tema atual: o mapa troca entre claro e escuro sem o app precisar rodar.
 */
class GridLayers(
    val levels: List<Bitmap?>, val missed: Bitmap?, val ring: Bitmap?, val pop: Bitmap? = null, val rest: Bitmap? = null,
    val scar: Bitmap? = null, val trope: Bitmap? = null, val call: Boolean = false,
)

/** Asterisco de seis pontas (o marco), em pixels. */
fun Canvas.drawStar(cx: Float, cy: Float, len: Float, color: Int) {
    val w = len * 0.185f
    val half = (len - w) / 2
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = w; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    for (k in 0 until 3) {
        val a = Math.toRadians((k * 60.0 - 90.0))
        val dx = (kotlin.math.cos(a) * half).toFloat(); val dy = (kotlin.math.sin(a) * half).toFloat()
        drawLine(cx - dx, cy - dy, cx + dx, cy + dy, p)
    }
}

/** Costura vertical numa célula da grade: linha que encosta nos vizinhos e um ponto no meio. */
fun Canvas.drawStitch(x: Float, y: Float, size: Float, gap: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val w = maxOf(1.5f, size * 0.1f)
    val cx = x + size / 2; val cy = y + size / 2
    drawRect(cx - w / 2, y - gap / 2, cx + w / 2, y + size + gap / 2, p)
    drawRect(cx - size * 0.22f, cy - w / 2, cx + size * 0.22f, cy + w / 2, p)
}

/** Tropeço: traço diagonal fino. */
fun Canvas.drawTrope(x: Float, y: Float, size: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = maxOf(1f, size * 0.08f); strokeCap = Paint.Cap.ROUND; alpha = 215 }
    val i = size * 0.26f
    drawLine(x + i, y + size - i, x + size - i, y + i, p)
}

/**
 * Só a grade, sem rótulos nem legenda: no widget a cor já conta a história.
 * Unidade base: célula 10u, espaço 2,2u (a proporção 14/3 do protótipo).
 */
object HeatmapRenderer {
    const val CELL = 10f
    const val GAP = 2.2f // fresta de 3px para célula de 14, como no protótipo
    const val STEP = CELL + GAP
    private const val GRID_H = 7 * STEP - GAP
    private const val MAX_U = 1.8f // célula de no máximo 18dp: perto das 16 semanas do protótipo no 4×3

    /**
     * [pop] anima o quadrado de hoje ao marcar: 1 = encolhido, 2 = passa do tamanho, 0 = em repouso.
     * O widget troca os quadros em sequência (ver [WidgetPulse]).
     */
    fun render(state: HabitState, widthDp: Float, heightDp: Float, density: Float, pop: Int = 0): GridLayers {
        val scale = density.coerceAtMost(2.5f)
        val autoU = min(heightDp / GRID_H, MAX_U).coerceAtLeast(0.5f)
        val weeks = if (state.weeks > 0) state.weeks
        else floor((widthDp / autoU + GAP) / STEP).toInt().coerceIn(4, 53)
        val u = min(min(widthDp / (weeks * STEP - GAP), heightDp / GRID_H), MAX_U).coerceAtLeast(0.3f)

        val wPx = ((weeks * STEP - GAP) * u * scale).toInt().coerceAtLeast(1)
        val hPx = (GRID_H * u * scale).toInt().coerceAtLeast(1)
        fun layer() = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ALPHA_8)
        val levelLayers = arrayOfNulls<Bitmap>(5)
        var missedLayer: Bitmap? = null
        var ringLayer: Bitmap? = null
        var popLayer: Bitmap? = null
        var restLayer: Bitmap? = null
        var scarLayer: Bitmap? = null
        var tropeLayer: Bitmap? = null
        val milestones = if (state.weekly) Cal.weekMilestones(state) else Cal.milestones(state)
        fun canvasOf(b: Bitmap) = Canvas(b).apply { scale(scale, scale) }
        val canvases = HashMap<Bitmap, Canvas>()
        fun draw(b: Bitmap) = canvases.getOrPut(b) { canvasOf(b) }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
        val today = state.today
        val start = Cal.start(today, weeks)
        val levels = Cal.levels(state, start, today)
        val cell = CELL * u
        val r = 1.4f * u // quase reto, como as células do app

        for (col in 0 until weeks) for (row in 0 until 7) {
            val d = Cal.dateAt(start, col, row)
            if (d.isAfter(today)) continue
            val x = col * STEP * u
            val y = row * STEP * u
            val rect = RectF(x, y, x + cell, y + cell)
            val level = levels[d] ?: 0
            if (pop > 0 && d == today) {
                // Hoje acabou de ser marcado: o quadrado nasce pequeno e passa um pouco do tamanho antes de assentar.
                val k = if (pop == 1) 0.55f else 1.18f
                val grow = cell * (k - 1f) / 2
                val pb = popLayer ?: layer().also { popLayer = it }
                draw(pb).drawRoundRect(RectF(rect).apply { inset(-grow, -grow) }, r * k, r * k, fill)
                continue
            }
            // Marco: o asterisco no lugar do quadrado, no tom mais forte.
            if (d in milestones) {
                val lb = levelLayers[4] ?: layer().also { levelLayers[4] = it }
                draw(lb).drawStar(rect.centerX(), rect.centerY(), cell * Motion.STAR, android.graphics.Color.BLACK)
                continue
            }
            val lb = levelLayers[level] ?: layer().also { levelLayers[level] = it }
            draw(lb).drawRoundRect(rect, r, r, fill)
            when {
                d in state.scars -> {
                    val sb = scarLayer ?: layer().also { scarLayer = it }
                    draw(sb).drawStitch(rect.left, rect.top, cell, GAP * u, android.graphics.Color.BLACK)
                }
                d in state.tropes -> {
                    val tb = tropeLayer ?: layer().also { tropeLayer = it }
                    draw(tb).drawTrope(rect.left, rect.top, cell, android.graphics.Color.BLACK)
                }
                // Descanso: um traço curto no lugar do quadrado vazio.
                state.isRest(d) && d != today && state.markOf(d) == null -> {
                    val rb = restLayer ?: layer().also { restLayer = it }
                    fill.alpha = 180
                    draw(rb).drawRect(RectF(rect.left + cell * 0.25f, rect.centerY() - 0.75f, rect.right - cell * 0.25f, rect.centerY() + 0.75f), fill)
                    fill.alpha = 255
                }
                // "Não fiz": um ponto discreto em vez de contorno, menos ruído na grade.
                state.markOf(d) == Mark.MISSED -> {
                    val mb = missedLayer ?: layer().also { missedLayer = it }
                    fill.alpha = 140 // ponto discreto, como no app
                    draw(mb).drawCircle(rect.centerX(), rect.centerY(), cell * Motion.MISSED_DOT, fill)
                    fill.alpha = 255
                }
                // Hoje pendente: anel fino, convite silencioso.
                d == today && state.markOf(d) == null -> {
                    val rb = ringLayer ?: layer().also { ringLayer = it }
                    ring.alpha = 255
                    // Ontem ficou vazio: o anel vira tracejado, esperando a volta.
                    ring.pathEffect = if (state.pendingTrope) android.graphics.DashPathEffect(floatArrayOf(cell * 0.22f, cell * 0.16f), 0f) else null
                    draw(rb).drawRoundRect(RectF(rect).apply { inset(0.75f, 0.75f) }, r, r, ring)
                }
            }
        }
        return GridLayers(levelLayers.toList(), missedLayer, ringLayer, popLayer, restLayer, scarLayer, tropeLayer, state.pendingTrope)
    }
}
