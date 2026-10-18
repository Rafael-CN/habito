package com.rafael.habito

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import java.util.EnumMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * O glifo do marco de cada cor. Todos nascem num quadrado de 20 unidades e viram um contorno cheio
 * (traços já convertidos em área), então o app, o widget e o cartão desenham o mesmo desenho.
 * [symmetry] é o giro que devolve a mesma forma (0 = não gira).
 */
enum class Glyph(val symmetry: Float) {
    ASTERISCO(60f), FLOR(60f), CRISTAL(60f), ESTRELA_MAR(72f), BRILHO(90f), SOL(60f), CARIMBO(60f), LAPIS(0f)
}

/** O gesto do quadrado de hoje ao ser marcado. */
enum class Gesture { BROTO, FLOR, GOTA, ONDA, CINTILA, BRILHO, CARIMBO, TRACO }

object Glyphs {
    fun of(palette: String): Glyph = when (palette) {
        "rosa" -> Glyph.FLOR; "azul" -> Glyph.CRISTAL; "oceano" -> Glyph.ESTRELA_MAR; "violeta" -> Glyph.BRILHO
        "ambar" -> Glyph.SOL; "terracota" -> Glyph.CARIMBO; "grafite" -> Glyph.LAPIS; else -> Glyph.ASTERISCO
    }

    fun gesture(palette: String): Gesture = when (palette) {
        "rosa" -> Gesture.FLOR; "azul" -> Gesture.GOTA; "oceano" -> Gesture.ONDA; "violeta" -> Gesture.CINTILA
        "ambar" -> Gesture.BRILHO; "terracota" -> Gesture.CARIMBO; "grafite" -> Gesture.TRACO; else -> Gesture.BROTO
    }

    private val cache = EnumMap<Glyph, Path>(Glyph::class.java)
    private val composeCache = EnumMap<Glyph, androidx.compose.ui.graphics.Path>(Glyph::class.java)

    /** Contorno cheio em 20×20. */
    @Synchronized fun path(g: Glyph): Path = cache.getOrPut(g) { build(g) }
    @Synchronized fun composePath(g: Glyph): androidx.compose.ui.graphics.Path = composeCache.getOrPut(g) { Path(path(g)).asComposePath() }

    private fun stroked(src: Path, w: Float, join: Paint.Join = Paint.Join.ROUND): Path {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = join }
        return Path().also { p.getFillPath(src, it) }
    }

    private fun pt(a: Double, r: Double) = (10 + cos((a - 90) * PI / 180) * r).toFloat() to (10 + sin((a - 90) * PI / 180) * r).toFloat()
    private fun Path.seg(a: Double, r1: Double, r2: Double) { val (x1, y1) = pt(a, r1); val (x2, y2) = pt(a, r2); moveTo(x1, y1); lineTo(x2, y2) }
    private fun polygon(n: Int, r: (Int) -> Double): Path = Path().apply {
        for (k in 0 until n) { val (x, y) = pt(k * 360.0 / n, r(k)); if (k == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }
    private fun Path.plus(o: Path) = apply { op(o, Path.Op.UNION) }
    private fun Path.minus(o: Path) = apply { op(o, Path.Op.DIFFERENCE) }

    private fun build(g: Glyph): Path = when (g) {
        Glyph.ASTERISCO -> stroked(Path().apply { listOf(0.0, 60.0, 120.0).forEach { seg(it, -8.0, 8.0) } }, 3.7f)
        Glyph.FLOR -> {
            val petals = Path()
            for (a in 0 until 6) {
                val petal = Path().apply { addOval(RectF(7.2f, 0.4f, 12.8f, 9.6f), Path.Direction.CW) }
                petal.transform(Matrix().apply { setRotate(a * 60f, 10f, 10f) })
                petals.plus(petal)
            }
            petals.minus(Path().apply { addCircle(10f, 10f, 2.6f, Path.Direction.CW) })
        }
        Glyph.CRISTAL -> stroked(Path().apply {
            for (k in 0 until 6) {
                val a = k * 60.0
                seg(a, 0.0, 8.2)
                val (bx, by) = pt(a, 4.4)
                val (l1x, l1y) = pt(a - 26, 6.6); val (l2x, l2y) = pt(a + 26, 6.6)
                moveTo(l1x, l1y); lineTo(bx, by); lineTo(l2x, l2y)
            }
        }, 2.2f)
        Glyph.ESTRELA_MAR -> {
            val poly = polygon(10) { if (it % 2 == 0) 9.0 else 3.7 }
            Path(poly).plus(stroked(poly, 1.6f))
        }
        Glyph.BRILHO -> Path().apply {
            moveTo(10f, 0.8f)
            cubicTo(10.9f, 7f, 13f, 9.1f, 19.2f, 10f)
            cubicTo(13f, 10.9f, 10.9f, 13f, 10f, 19.2f)
            cubicTo(9.1f, 13f, 7f, 10.9f, 0.8f, 10f)
            cubicTo(7f, 9.1f, 9.1f, 7f, 10f, 0.8f)
            close()
        }
        Glyph.SOL -> Path().apply { addCircle(10f, 10f, 4.4f, Path.Direction.CW) }
            .plus(stroked(Path().apply { for (k in 0 until 6) seg(k * 60.0, 6.8, 9.3) }, 2.2f))
        Glyph.CARIMBO -> {
            val hex = polygon(6) { 9.0 }
            Path(hex).plus(stroked(hex, 1.4f)).minus(stroked(Path().apply { listOf(0.0, 60.0, 120.0).forEach { seg(it, -5.0, 5.0) } }, 2.1f))
        }
        Glyph.LAPIS -> stroked(Path().apply {
            moveTo(10.2f, 1.6f); lineTo(9.8f, 18.3f)
            moveTo(2.9f, 5.6f); quadTo(10f, 10.4f, 17.3f, 14.2f)
            moveTo(17.1f, 5.4f); quadTo(9.8f, 9.9f, 2.8f, 14.6f)
        }, 1.8f)
    }
}

/**
 * O glifo do marco, de ponta a ponta [len], centrado em [center]. [turn] gira em graus.
 * O nome ficou "star" desde o asterisco da 3.3; hoje cada cor tem o seu.
 */
fun DrawScope.star(color: Color, center: Offset, len: Float, turn: Float = 0f, glyph: Glyph = Glyph.ASTERISCO) {
    if (len <= 0f) return
    val p = Glyphs.composePath(glyph)
    withTransform({
        translate(center.x - len / 2, center.y - len / 2)
        scale(len / 20f, len / 20f, Offset.Zero)
        if (turn != 0f) rotate(turn, Offset(10f, 10f))
    }) { drawPath(p, color) }
}

/** O mesmo glifo num Canvas comum (widget e cartões). */
fun android.graphics.Canvas.drawStar(cx: Float, cy: Float, len: Float, color: Int, glyph: Glyph = Glyph.ASTERISCO) {
    save()
    translate(cx - len / 2, cy - len / 2)
    scale(len / 20f, len / 20f)
    drawPath(Glyphs.path(glyph), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL })
    restore()
}

/**
 * O quadrado de hoje sendo marcado, no gesto da cor. [t] vai de 0 a 1; [base] é o tom vazio e [fill] o tom feito.
 * Desenha a célula inteira (size × size) com cantos [r].
 */
fun DrawScope.gestureFill(kind: Gesture, t: Float, base: Color, fill: Color, r: Float) {
    val w = size.width; val h = size.height
    val cr = CornerRadius(r)
    fun ease(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
    fun back(x: Float): Float { val c = 1.70158f; val y = x - 1f; return 1f + (c + 1f) * y * y * y + c * y * y }
    drawRoundRect(base, cornerRadius = cr)
    when (kind) {
        Gesture.BROTO -> clipRect(top = h * (1f - ease(t))) { drawRoundRect(fill, cornerRadius = cr) }
        Gesture.FLOR, Gesture.GOTA -> {
            val s = if (kind == Gesture.FLOR) back(t) else ease(t)
            val round = (w / 2) * (1f - ease(t)) + r * ease(t)
            withTransform({ rotate(if (kind == Gesture.FLOR) -40f * (1f - ease(t)) else 0f); scale(s, s) }) {
                drawRoundRect(fill, cornerRadius = CornerRadius(round))
            }
            if (kind == Gesture.GOTA) {
                val ring = 0.6f + 1.2f * ease(t)
                drawRoundRect(
                    fill.copy(alpha = (1f - t).coerceIn(0f, 1f)), Offset(w * (1 - ring) / 2, h * (1 - ring) / 2), Size(w * ring, h * ring),
                    CornerRadius(w * ring / 2), style = Stroke(2.5f * density),
                )
            }
        }
        Gesture.ONDA -> {
            val x = (w * 1.4f) * ease(t)
            val wave = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(x, 0f)
                quadraticBezierTo(x - w * 0.15f, h * 0.5f, x - w * 0.4f, h); lineTo(0f, h); close()
            }
            clipRect { drawPath(wave, fill) }
        }
        Gesture.CINTILA -> {
            drawRoundRect(fill.copy(alpha = ease(t)), cornerRadius = cr)
            drawRoundRect(Color.White.copy(alpha = 0.7f * (1f - t) * ease((t * 2).coerceAtMost(1f))), cornerRadius = cr)
        }
        Gesture.BRILHO -> {
            val glow = sin(PI * t).toFloat()
            drawCircle(fill.copy(alpha = 0.35f * glow), radius = w * (0.5f + 0.5f * t), center = center)
            val s = 0.6f + 0.4f * back(t)
            withTransform({ scale(s, s) }) { drawRoundRect(fill, cornerRadius = cr) }
        }
        Gesture.CARIMBO -> {
            val s = 1.6f - 0.6f * back(t)
            withTransform({ rotate(-9f * (1f - ease(t))); scale(s, s) }) { drawRoundRect(fill.copy(alpha = (t * 2.2f).coerceAtMost(1f)), cornerRadius = cr) }
        }
        Gesture.TRACO -> {
            val n = (t * 7f).toInt().coerceIn(0, 7)
            clipRect {
                for (k in 0 until n) {
                    val o = (k + 1) * (w + h) / 8f
                    drawLine(fill, Offset(o, 0f), Offset(o - h, h), strokeWidth = w * 0.16f)
                }
            }
            if (t > 0.85f) drawRoundRect(fill.copy(alpha = (t - 0.85f) / 0.15f), cornerRadius = cr)
        }
    }
}
