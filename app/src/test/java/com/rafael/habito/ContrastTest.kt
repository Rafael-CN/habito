package com.rafael.habito

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Contraste WCAG nos dois temas: texto ≥ 4,5:1 (AA); ícones e texto grande ≥ 3:1. */
class ContrastTest {
    private fun lum(c: Color): Double {
        fun ch(v: Float) = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun atLeast(min: Double, fg: Color, bg: Color, what: String) {
        val r = ratio(fg, bg)
        assertTrue("$what: %.2f:1 (mínimo %.1f)".format(r, min), r >= min)
    }

    private fun check(p: Palette, theme: String) {
        atLeast(7.0, p.ink, p.bg, "$theme texto")
        atLeast(7.0, p.ink, p.surface, "$theme texto no bloco")
        atLeast(4.5, p.text2, p.bg, "$theme texto secundário")
        atLeast(4.5, p.text2, p.surface, "$theme texto secundário no bloco")
        atLeast(4.5, p.text3, p.bg, "$theme legenda")
        atLeast(4.5, p.text3, p.surface, "$theme legenda no bloco")
        atLeast(4.5, p.ink, p.thumb, "$theme opção escolhida no trilho")
        atLeast(4.5, p.text2, p.track, "$theme opção no trilho")
        atLeast(4.5, p.bg, p.ink, "$theme barra de desfazer e botão principal")
        atLeast(4.5, p.onDone, p.done, "$theme \"Feito hoje\"")
        atLeast(4.5, p.done, p.bg, "$theme \"sem restrição\"")
        atLeast(3.0, p.line, p.bg, "$theme borda de controle")
        atLeast(3.0, p.line, p.surface, "$theme borda de controle no bloco")
        atLeast(3.0, p.miss, p.surface, "$theme ponto do \"não fiz\"")
        atLeast(3.0, p.gold, p.surface, "$theme anel do marco")
        p.levels.zip(p.levelInk).forEachIndexed { i, (bg, fg) -> atLeast(4.5, fg, bg, "$theme número no tom $i") }
    }

    /** Cada cor de mapa, nos dois temas: o texto do "feito" e os números sobre os tons continuam legíveis. */
    @Test fun `cores do mapa`() {
        for (name in Accents.NAMES) for (dark in listOf(false, true)) {
            val p = paletteFor(name, dark)
            val t = "$name ${if (dark) "escuro" else "claro"}"
            atLeast(4.5, p.onDone, p.done, "$t \"Feito hoje\"")
            atLeast(3.0, p.done, p.bg, "$t verde do feito no fundo")
            p.levels.zip(p.levelInk).drop(1).forEachIndexed { i, (bg, fg) -> atLeast(4.5, fg, bg, "$t número no tom ${i + 1}") }
        }
    }

    @Test fun `tema claro`() = check(Palettes.Light, "claro")

    @Test fun `tema escuro`() = check(Palettes.Dark, "escuro")
}
