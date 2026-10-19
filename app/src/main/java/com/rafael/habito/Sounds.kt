package com.rafael.habito

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import kotlin.random.Random

/** Os eventos que soam. A ordem é a das colunas da tabela. */
enum class Sfx { FEITO, DESFAZER, NAO_FIZ, MARCO, TOQUE }

/**
 * Os sons de cada cor (design/sons/v2): um instrumento e uma tonalidade por cor, três variações por evento.
 * Só a cor escolhida fica carregada. Desligado por padrão; o modo silencioso e o vibrar do aparelho calam tudo.
 */
object Sounds {
    private val pool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
            .also { p -> p.setOnLoadCompleteListener { _, id, status -> onLoaded(p, id, status) } }
    }
    private val loaded = HashSet<Int>()
    private var pending = 0 // pedido antes de o som terminar de carregar (o primeiro toque depois de ligar)
    private var palette: String? = null
    private var ids: Array<IntArray> = emptyArray()
    private val last = IntArray(Sfx.entries.size) { -1 }
    private var lastStream = 0

    /** Carrega os sons da cor (e solta os da anterior). Chamar quando "Sons" liga ou a cor muda. */
    @Synchronized fun prepare(ctx: Context, palette: String) {
        if (palette == this.palette) return
        ids.forEach { row -> row.forEach { pool.unload(it) } }
        loaded.clear(); pending = 0
        val res = TABLE[palette] ?: TABLE.getValue("verde")
        ids = Array(res.size) { e -> IntArray(res[e].size) { v -> pool.load(ctx.applicationContext, res[e][v], 1) } }
        this.palette = palette
        last.fill(-1)
    }

    /** Toca o evento numa variação diferente da última. */
    @Synchronized fun play(ctx: Context, palette: String, sfx: Sfx) {
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        prepare(ctx, palette)
        val row = ids.getOrNull(sfx.ordinal) ?: return
        var v = Random.nextInt(row.size)
        if (v == last[sfx.ordinal]) v = (v + 1 + Random.nextInt(row.size - 1)) % row.size
        last[sfx.ordinal] = v
        // o marco começa por cima do feito que o trouxe: o feito sai de cena
        if (sfx == Sfx.MARCO && lastStream != 0) pool.stop(lastStream)
        if (row[v] in loaded) lastStream = pool.play(row[v], 1f, 1f, if (sfx == Sfx.MARCO) 1 else 0, 0, 1f)
        else pending = row[v]
    }

    @Synchronized private fun onLoaded(p: SoundPool, id: Int, status: Int) {
        if (status != 0) return
        loaded += id
        if (id == pending) { pending = 0; lastStream = p.play(id, 1f, 1f, 0, 0, 1f) }
    }

    private val TABLE: Map<String, Array<IntArray>> = mapOf(
        "verde" to arrayOf(intArrayOf(R.raw.sfx_verde_feito_1, R.raw.sfx_verde_feito_2, R.raw.sfx_verde_feito_3), intArrayOf(R.raw.sfx_verde_desfazer_1, R.raw.sfx_verde_desfazer_2, R.raw.sfx_verde_desfazer_3), intArrayOf(R.raw.sfx_verde_naofiz_1, R.raw.sfx_verde_naofiz_2, R.raw.sfx_verde_naofiz_3), intArrayOf(R.raw.sfx_verde_marco_1, R.raw.sfx_verde_marco_2, R.raw.sfx_verde_marco_3), intArrayOf(R.raw.sfx_verde_toque_1, R.raw.sfx_verde_toque_2, R.raw.sfx_verde_toque_3)),
        "rosa" to arrayOf(intArrayOf(R.raw.sfx_rosa_feito_1, R.raw.sfx_rosa_feito_2, R.raw.sfx_rosa_feito_3), intArrayOf(R.raw.sfx_rosa_desfazer_1, R.raw.sfx_rosa_desfazer_2, R.raw.sfx_rosa_desfazer_3), intArrayOf(R.raw.sfx_rosa_naofiz_1, R.raw.sfx_rosa_naofiz_2, R.raw.sfx_rosa_naofiz_3), intArrayOf(R.raw.sfx_rosa_marco_1, R.raw.sfx_rosa_marco_2, R.raw.sfx_rosa_marco_3), intArrayOf(R.raw.sfx_rosa_toque_1, R.raw.sfx_rosa_toque_2, R.raw.sfx_rosa_toque_3)),
        "azul" to arrayOf(intArrayOf(R.raw.sfx_azul_feito_1, R.raw.sfx_azul_feito_2, R.raw.sfx_azul_feito_3), intArrayOf(R.raw.sfx_azul_desfazer_1, R.raw.sfx_azul_desfazer_2, R.raw.sfx_azul_desfazer_3), intArrayOf(R.raw.sfx_azul_naofiz_1, R.raw.sfx_azul_naofiz_2, R.raw.sfx_azul_naofiz_3), intArrayOf(R.raw.sfx_azul_marco_1, R.raw.sfx_azul_marco_2, R.raw.sfx_azul_marco_3), intArrayOf(R.raw.sfx_azul_toque_1, R.raw.sfx_azul_toque_2, R.raw.sfx_azul_toque_3)),
        "oceano" to arrayOf(intArrayOf(R.raw.sfx_oceano_feito_1, R.raw.sfx_oceano_feito_2, R.raw.sfx_oceano_feito_3), intArrayOf(R.raw.sfx_oceano_desfazer_1, R.raw.sfx_oceano_desfazer_2, R.raw.sfx_oceano_desfazer_3), intArrayOf(R.raw.sfx_oceano_naofiz_1, R.raw.sfx_oceano_naofiz_2, R.raw.sfx_oceano_naofiz_3), intArrayOf(R.raw.sfx_oceano_marco_1, R.raw.sfx_oceano_marco_2, R.raw.sfx_oceano_marco_3), intArrayOf(R.raw.sfx_oceano_toque_1, R.raw.sfx_oceano_toque_2, R.raw.sfx_oceano_toque_3)),
        "violeta" to arrayOf(intArrayOf(R.raw.sfx_violeta_feito_1, R.raw.sfx_violeta_feito_2, R.raw.sfx_violeta_feito_3), intArrayOf(R.raw.sfx_violeta_desfazer_1, R.raw.sfx_violeta_desfazer_2, R.raw.sfx_violeta_desfazer_3), intArrayOf(R.raw.sfx_violeta_naofiz_1, R.raw.sfx_violeta_naofiz_2, R.raw.sfx_violeta_naofiz_3), intArrayOf(R.raw.sfx_violeta_marco_1, R.raw.sfx_violeta_marco_2, R.raw.sfx_violeta_marco_3), intArrayOf(R.raw.sfx_violeta_toque_1, R.raw.sfx_violeta_toque_2, R.raw.sfx_violeta_toque_3)),
        "ambar" to arrayOf(intArrayOf(R.raw.sfx_ambar_feito_1, R.raw.sfx_ambar_feito_2, R.raw.sfx_ambar_feito_3), intArrayOf(R.raw.sfx_ambar_desfazer_1, R.raw.sfx_ambar_desfazer_2, R.raw.sfx_ambar_desfazer_3), intArrayOf(R.raw.sfx_ambar_naofiz_1, R.raw.sfx_ambar_naofiz_2, R.raw.sfx_ambar_naofiz_3), intArrayOf(R.raw.sfx_ambar_marco_1, R.raw.sfx_ambar_marco_2, R.raw.sfx_ambar_marco_3), intArrayOf(R.raw.sfx_ambar_toque_1, R.raw.sfx_ambar_toque_2, R.raw.sfx_ambar_toque_3)),
        "terracota" to arrayOf(intArrayOf(R.raw.sfx_terracota_feito_1, R.raw.sfx_terracota_feito_2, R.raw.sfx_terracota_feito_3), intArrayOf(R.raw.sfx_terracota_desfazer_1, R.raw.sfx_terracota_desfazer_2, R.raw.sfx_terracota_desfazer_3), intArrayOf(R.raw.sfx_terracota_naofiz_1, R.raw.sfx_terracota_naofiz_2, R.raw.sfx_terracota_naofiz_3), intArrayOf(R.raw.sfx_terracota_marco_1, R.raw.sfx_terracota_marco_2, R.raw.sfx_terracota_marco_3), intArrayOf(R.raw.sfx_terracota_toque_1, R.raw.sfx_terracota_toque_2, R.raw.sfx_terracota_toque_3)),
        "grafite" to arrayOf(intArrayOf(R.raw.sfx_grafite_feito_1, R.raw.sfx_grafite_feito_2, R.raw.sfx_grafite_feito_3), intArrayOf(R.raw.sfx_grafite_desfazer_1, R.raw.sfx_grafite_desfazer_2, R.raw.sfx_grafite_desfazer_3), intArrayOf(R.raw.sfx_grafite_naofiz_1, R.raw.sfx_grafite_naofiz_2, R.raw.sfx_grafite_naofiz_3), intArrayOf(R.raw.sfx_grafite_marco_1, R.raw.sfx_grafite_marco_2, R.raw.sfx_grafite_marco_3), intArrayOf(R.raw.sfx_grafite_toque_1, R.raw.sfx_grafite_toque_2, R.raw.sfx_grafite_toque_3)),
    )
}
