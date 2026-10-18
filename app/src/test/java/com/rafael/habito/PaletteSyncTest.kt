package com.rafael.habito

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * O app usa as paletas de Tokens.kt; o widget e os drawables usam res/values(-night)/colors.xml.
 * Este teste falha se as duas cópias divergirem.
 */
class PaletteSyncTest {
    private fun xml(path: String): Map<String, String> {
        val text = File(path).readText()
        return Regex("""<color name="(hb_[a-z0-9_]+)">(#[0-9A-Fa-f]{6,8})</color>""")
            .findAll(text).associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private fun pairs(p: Palette) = mapOf(
        "hb_bg" to p.bg, "hb_surface" to p.surface, "hb_rule" to p.rule, "hb_line" to p.line,
        "hb_ink" to p.ink, "hb_text2" to p.text2, "hb_text3" to p.text3, "hb_done" to p.done, "hb_on_done" to p.onDone,
        "hb_miss" to p.miss, "hb_gold" to p.gold,
        "hb_l0" to p.levels[0], "hb_l1" to p.levels[1], "hb_l2" to p.levels[2], "hb_l3" to p.levels[3], "hb_l4" to p.levels[4],
    )

    private fun check(palette: Palette, file: String) {
        val res = xml(file)
        pairs(palette).forEach { (name, color) -> assertEquals("$name em $file", res[name], hex(color)) }
    }

    @Test fun `tema claro igual nos dois lugares`() = check(Palettes.Light, "src/main/res/values/colors.xml")

    @Test fun `tema escuro igual nos dois lugares`() = check(Palettes.Dark, "src/main/res/values-night/colors.xml")
}
