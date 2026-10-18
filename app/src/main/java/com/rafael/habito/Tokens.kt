package com.rafael.habito

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Linguagem "calma editorial": neutros sem matiz, verde só onde há dado, régua e tipografia no lugar de caixas.
 * Espelhada em res/values(-night)/colors.xml, que o widget usa (PaletteSyncTest confere).
 */
@Immutable
data class Palette(
    val bg: Color,         // página e widget
    val surface: Color,    // blocos por tom (Hoje, Ano)
    val track: Color,      // trilho dos seletores
    val thumb: Color,      // marcador do trilho
    val thumbEdge: Color,
    val rule: Color,       // régua fina entre linhas
    val line: Color,       // borda de controle (≥ 3:1)
    val ink: Color,        // texto principal, réguas de seção, botão principal
    val text2: Color,
    val text3: Color,
    val done: Color,
    val onDone: Color,
    val miss: Color,
    val gold: Color,       // anel dos marcos
    val onInk2: Color,     // texto secundário sobre a barra invertida
    val levels: List<Color>,    // 0 = sem registro, 1..4 = sequência crescente
    val levelInk: List<Color>,  // número legível sobre cada tom
)

object Palettes {
    val Light = Palette(
        bg = Color(0xFFFFFFFF), surface = Color(0xFFF4F4F3), track = Color(0xFFEAEAE8), thumb = Color(0xFFFFFFFF),
        thumbEdge = Color(0x12000000), rule = Color(0xFFE3E3E3), line = Color(0xFF757575),
        ink = Color(0xFF0E0E0E), text2 = Color(0xFF545454), text3 = Color(0xFF6E6E6E),
        done = Color(0xFF12813F), onDone = Color(0xFFFFFFFF), miss = Color(0xFFD1242F), gold = Color(0xFFA87700),
        onInk2 = Color(0xFFB4B4B4),
        levels = listOf(Color(0xFFE3E3E2), Color(0xFFBFE4C7), Color(0xFF6CC286), Color(0xFF2C9A53), Color(0xFF0C6A32)),
        levelInk = listOf(Color(0xFF545454), Color(0xFF0E0E0E), Color(0xFF0E0E0E), Color(0xFF0E0E0E), Color(0xFFFFFFFF)),
    )
    val Dark = Palette(
        bg = Color(0xFF0C0C0D), surface = Color(0xFF151517), track = Color(0xFF1F1F23), thumb = Color(0xFF2E2E33),
        thumbEdge = Color(0x0FFFFFFF), rule = Color(0xFF262628), line = Color(0xFF8C8C8C),
        ink = Color(0xFFECECEC), text2 = Color(0xFFA6A6A6), text3 = Color(0xFF8C8C8C),
        done = Color(0xFF3DB867), onDone = Color(0xFF04170A), miss = Color(0xFFF06058), gold = Color(0xFFD9AB33),
        onInk2 = Color(0xFF555555),
        levels = listOf(Color(0xFF242428), Color(0xFF0F3B20), Color(0xFF166B35), Color(0xFF25A050), Color(0xFF4CD27C)),
        levelInk = listOf(Color(0xFFA6A6A6), Color(0xFFECECEC), Color(0xFFECECEC), Color(0xFF0C0C0D), Color(0xFF0C0C0D)),
    )
}

/**
 * Cores do mapa que a pessoa escolhe nos Ajustes. Mudam só a escala de tons e o "feito"; o resto da linguagem
 * (neutros, tinta, vermelho do "não fiz", dourado dos marcos) fica igual. Cada lista: tons 1..4, feito, texto sobre o feito.
 */
object Accents {
    val NAMES = listOf("verde", "azul", "ambar", "violeta", "grafite")
    private fun c(vararg h: Long) = h.map { Color(it) }
    private val light = mapOf(
        "azul" to c(0xFFC5DDF8, 0xFF79AEEA, 0xFF2F76CF, 0xFF12468F, 0xFF1F67C2, 0xFFFFFFFF),
        "ambar" to c(0xFFF6E2B3, 0xFFE8B95A, 0xFFC98A12, 0xFF7E5200, 0xFF8F5D00, 0xFFFFFFFF),
        "violeta" to c(0xFFDDD3F5, 0xFFAE97E6, 0xFF7656CC, 0xFF4A2E95, 0xFF6242B8, 0xFFFFFFFF),
        "grafite" to c(0xFFD8D8D6, 0xFFA9A9A6, 0xFF6E6E6B, 0xFF2E2E2C, 0xFF2E2E2C, 0xFFFFFFFF),
    )
    private val dark = mapOf(
        "azul" to c(0xFF12305A, 0xFF1C4F92, 0xFF3A7FD6, 0xFF78B4FF, 0xFF4C93E8, 0xFF06121F),
        "ambar" to c(0xFF3D2C08, 0xFF6E4E0E, 0xFFB9841C, 0xFFF0BE4E, 0xFFE0A93A, 0xFF1C1300),
        "violeta" to c(0xFF2A1F4A, 0xFF47367F, 0xFF6A50C4, 0xFFB39CFF, 0xFF9C84F0, 0xFF120A26),
        "grafite" to c(0xFF2A2A2E, 0xFF4A4A50, 0xFF8A8A90, 0xFFD0D0D4, 0xFFD0D0D4, 0xFF0C0C0D),
    )

    /** Tons 1..4, feito e texto sobre o feito para [name] no tema pedido (verde = a paleta base). */
    fun colors(name: String, isDark: Boolean): List<Color>? = (if (isDark) dark else light)[name]
}

private fun lum(c: Color): Double {
    fun ch(v: Float) = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
}
fun contrast(a: Color, b: Color): Double { val (h, l) = listOf(lum(a), lum(b)).sortedDescending(); return (h + 0.05) / (l + 0.05) }

/** A paleta do tema com a cor de mapa escolhida. O número sobre cada tom usa o texto que mais contrasta. */
fun paletteFor(name: String, isDark: Boolean): Palette {
    val base = if (isDark) Palettes.Dark else Palettes.Light
    val a = Accents.colors(name, isDark) ?: return base
    val levels = listOf(base.levels[0]) + a.subList(0, 4)
    val inks = listOf(base.ink, if (isDark) base.bg else Color.White)
    return base.copy(
        levels = levels,
        levelInk = listOf(base.levelInk[0]) + levels.drop(1).map { bg -> inks.maxBy { contrast(it, bg) } },
        done = a[4], onDone = a[5],
    )
}

val LocalPalette = staticCompositionLocalOf { Palettes.Light }

/** Paleta do tema atual (claro ou escuro). */
val Tokens: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

// ---------- Tokens de desenho: todo tamanho, espaço, raio e tempo do app sai daqui ----------

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val gutter = 20.dp   // margem lateral da página
    val xl = 24.dp
    val section = 44.dp  // respiro entre seções
}

/** Rígido como o resto da linguagem: quase reto, com só o suficiente para não ferir o olho. */
object Radius {
    val cell = 2.dp
    val legend = 2.dp
    val thumb = 2.dp
    val control = 3.dp   // botões, trilhos, campos, barra de desfazer
    val panel = 6.dp     // blocos por tom
}

/**
 * Archivo (variável, licença OFL em licenses/), em três larguras como no protótipo: normal para o texto,
 * condensada para números e títulos de impacto, larga para os rótulos em caixa alta.
 * O widget não aceita fonte empacotada e usa a condensada do sistema.
 */
@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int, width: Float) = Font(
    R.font.archivo, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

val Sans: FontFamily = FontFamily(archivo(400, 100f), archivo(500, 100f), archivo(600, 100f), archivo(700, 100f))
val Condensed: FontFamily = FontFamily(archivo(640, 72f))
val HeroFace: FontFamily = FontFamily(archivo(650, 68f))
val Wide: FontFamily = FontFamily(archivo(620, 115f))

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
private val noPad = PlatformTextStyle(includeFontPadding = false)

object Type {
    private const val TNUM = "tnum"
    val caption = TextStyle(fontFamily = Sans, fontSize = 11.sp, lineHeight = 14.sp)
    /** Rótulo de seção e de linha: caixa alta (no texto), larga e espaçada. */
    val caps = TextStyle(fontFamily = Wide, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.1.sp)
    val body = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp)
    val bodyL = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 20.sp)
    val value = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val title = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)
    val compactNum = TextStyle(fontFamily = Condensed, fontSize = 26.sp, lineHeight = 26.sp, fontFeatureSettings = TNUM, platformStyle = noPad, lineHeightStyle = tight)
    val invite = TextStyle(fontFamily = Condensed, fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp)
    val stat = TextStyle(fontFamily = Condensed, fontSize = 36.sp, lineHeight = 36.sp, fontFeatureSettings = TNUM, platformStyle = noPad, lineHeightStyle = tight)
    val number = TextStyle(fontFamily = Condensed, fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp, fontFeatureSettings = TNUM, platformStyle = noPad, lineHeightStyle = tight)
    val monthTitle = TextStyle(fontFamily = Condensed, fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-2).sp, platformStyle = noPad, lineHeightStyle = tight)
    /** A sequência atual: o único número enorme da tela. */
    val hero = TextStyle(fontFamily = HeroFace, fontSize = 152.sp, lineHeight = 140.sp, letterSpacing = (-6.5).sp, fontFeatureSettings = TNUM, platformStyle = noPad, lineHeightStyle = tight)
}

/** Opacidades nomeadas, no lugar de números soltos pelo código. */
object Alpha {
    const val dim = 0.45f       // grade atrás do convite do primeiro uso
    const val scrim = 0.82f     // fundo do convite
    const val marker = 0.75f    // ponto do "não fiz"
    const val tick = 0.45f      // traço apagado da régua do marco
    const val faded = 0.15f     // rótulos de mês enquanto há anotação
}

/**
 * Uma só física. Cor e opacidade usam durações curtas, sem quique; posição e escala usam molas:
 * [press] para toque, [spatial] para deslocamentos, [enter] para o que chega na tela.
 */
object Motion {
    const val quick = 160
    const val base = 300
    const val slow = 450
    const val reveal = 760
    const val halo = 800
    const val theme = 250
    const val undo = 4000   // a barra de desfazer vive o mesmo que o Snackbar curto
    val press = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val spatial = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    val enter = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    /** Trilho: a borda da frente corre (rápida, com quique curto) e a de trás alcança. */
    val lead = spring<Float>(dampingRatio = 0.62f, stiffness = 700f)
    val trail = spring<Float>(dampingRatio = 0.72f, stiffness = 260f)
    const val MISSED_DOT = 0.14f // raio do ponto do "não fiz", em fração da célula
    const val STAR = 1.22f       // asterisco do marco, ponta a ponta, em fração da célula
    const val STAR_EVERY = 9000L // o asterisco dá um giro de 60° a cada tantos ms
    const val STAR_TURN = 700    // duração do giro
    const val STITCH = 700       // a costura se desenha
}
