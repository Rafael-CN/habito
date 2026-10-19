package com.rafael.habito

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import kotlin.random.Random

/** Os eventos que soam. A ordem é a das colunas da tabela; TICK são os sete traços da régua, do grave ao agudo. */
enum class Sfx { FEITO, DESFAZER, NAO_FIZ, MARCO, TOQUE, COR, ABRIR, PAGINA, TICK }

/**
 * Os sons de cada cor. Instrumento e tonalidade da v2 (design/sons/v2); feito, não fiz, marco, cor, abertura,
 * página e régua com o motion da v3 (design/sons/v3): sopro, peso, brilho e swell em volta do instrumento.
 * Cada evento tem de 2 a 4 variações que mudam a frase, sorteadas sem repetir a última, e cada toque ainda
 * varia de leve o volume e o lado. Só a cor escolhida fica carregada. Desligado por padrão; tocam no volume de
 * mídia e o modo silencioso cala tudo.
 */
object Sounds {
    private val pool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
            .also { p -> p.setOnLoadCompleteListener { _, id, status -> onLoaded(p, id, status) } }
    }
    private val loaded = HashSet<Int>()
    private var pending = 0 // pedido antes de o som terminar de carregar (o primeiro toque depois de ligar ou trocar de cor)
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

    private fun silent(ctx: Context) = ctx.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_SILENT

    /** Toca o evento numa variação diferente da última. */
    @Synchronized fun play(ctx: Context, palette: String, sfx: Sfx) {
        if (silent(ctx)) return
        prepare(ctx, palette)
        val row = ids.getOrNull(sfx.ordinal) ?: return
        var v = Random.nextInt(row.size)
        if (row.size > 1 && v == last[sfx.ordinal]) v = (v + 1 + Random.nextInt(row.size - 1)) % row.size
        last[sfx.ordinal] = v
        // o marco começa por cima do feito que o trouxe: o feito sai de cena
        if (sfx == Sfx.MARCO && lastStream != 0) pool.stop(lastStream)
        start(row[v], if (sfx == Sfx.MARCO) 1 else 0)
    }

    /** O traço [i] (0..6) da régua acendendo. */
    @Synchronized fun tick(ctx: Context, palette: String, i: Int) {
        if (silent(ctx)) return
        prepare(ctx, palette)
        val row = ids.getOrNull(Sfx.TICK.ordinal) ?: return
        val id = row[i.coerceIn(0, row.size - 1)]
        if (id in loaded) pool.play(id, 0.9f, 0.9f, 0, 0, 1f)
    }

    private fun start(id: Int, priority: Int) {
        if (id !in loaded) { pending = id; return }
        // cada toque um pouco diferente: até 1 dB de volume e um leve deslocamento de lado
        val g = 1f - Random.nextFloat() * 0.11f
        val side = (Random.nextFloat() - 0.5f) * 0.16f
        lastStream = pool.play(id, g * (1f - side), g * (1f + side), priority, 0, 1f)
    }

    @Synchronized private fun onLoaded(p: SoundPool, id: Int, status: Int) {
        if (status != 0) return
        loaded += id
        if (id == pending) { pending = 0; lastStream = p.play(id, 1f, 1f, 0, 0, 1f) }
    }

    private val TABLE: Map<String, Array<IntArray>> = mapOf(
        "verde" to arrayOf(intArrayOf(R.raw.mfx_verde_feito_1, R.raw.mfx_verde_feito_2, R.raw.mfx_verde_feito_3, R.raw.mfx_verde_feito_4), intArrayOf(R.raw.sfx_verde_desfazer_1, R.raw.sfx_verde_desfazer_2, R.raw.sfx_verde_desfazer_3), intArrayOf(R.raw.mfx_verde_naofiz_1, R.raw.mfx_verde_naofiz_2, R.raw.mfx_verde_naofiz_3), intArrayOf(R.raw.mfx_verde_marco_1, R.raw.mfx_verde_marco_2, R.raw.mfx_verde_marco_3), intArrayOf(R.raw.sfx_verde_toque_1, R.raw.sfx_verde_toque_2, R.raw.sfx_verde_toque_3), intArrayOf(R.raw.mfx_verde_cor_1, R.raw.mfx_verde_cor_2), intArrayOf(R.raw.mfx_verde_abrir_1, R.raw.mfx_verde_abrir_2), intArrayOf(R.raw.mfx_verde_pagina_1, R.raw.mfx_verde_pagina_2, R.raw.mfx_verde_pagina_3), intArrayOf(R.raw.mfx_verde_tick_1, R.raw.mfx_verde_tick_2, R.raw.mfx_verde_tick_3, R.raw.mfx_verde_tick_4, R.raw.mfx_verde_tick_5, R.raw.mfx_verde_tick_6, R.raw.mfx_verde_tick_7)),
        "rosa" to arrayOf(intArrayOf(R.raw.mfx_rosa_feito_1, R.raw.mfx_rosa_feito_2, R.raw.mfx_rosa_feito_3, R.raw.mfx_rosa_feito_4), intArrayOf(R.raw.sfx_rosa_desfazer_1, R.raw.sfx_rosa_desfazer_2, R.raw.sfx_rosa_desfazer_3), intArrayOf(R.raw.mfx_rosa_naofiz_1, R.raw.mfx_rosa_naofiz_2, R.raw.mfx_rosa_naofiz_3), intArrayOf(R.raw.mfx_rosa_marco_1, R.raw.mfx_rosa_marco_2, R.raw.mfx_rosa_marco_3), intArrayOf(R.raw.sfx_rosa_toque_1, R.raw.sfx_rosa_toque_2, R.raw.sfx_rosa_toque_3), intArrayOf(R.raw.mfx_rosa_cor_1, R.raw.mfx_rosa_cor_2), intArrayOf(R.raw.mfx_rosa_abrir_1, R.raw.mfx_rosa_abrir_2), intArrayOf(R.raw.mfx_rosa_pagina_1, R.raw.mfx_rosa_pagina_2, R.raw.mfx_rosa_pagina_3), intArrayOf(R.raw.mfx_rosa_tick_1, R.raw.mfx_rosa_tick_2, R.raw.mfx_rosa_tick_3, R.raw.mfx_rosa_tick_4, R.raw.mfx_rosa_tick_5, R.raw.mfx_rosa_tick_6, R.raw.mfx_rosa_tick_7)),
        "azul" to arrayOf(intArrayOf(R.raw.mfx_azul_feito_1, R.raw.mfx_azul_feito_2, R.raw.mfx_azul_feito_3, R.raw.mfx_azul_feito_4), intArrayOf(R.raw.sfx_azul_desfazer_1, R.raw.sfx_azul_desfazer_2, R.raw.sfx_azul_desfazer_3), intArrayOf(R.raw.mfx_azul_naofiz_1, R.raw.mfx_azul_naofiz_2, R.raw.mfx_azul_naofiz_3), intArrayOf(R.raw.mfx_azul_marco_1, R.raw.mfx_azul_marco_2, R.raw.mfx_azul_marco_3), intArrayOf(R.raw.sfx_azul_toque_1, R.raw.sfx_azul_toque_2, R.raw.sfx_azul_toque_3), intArrayOf(R.raw.mfx_azul_cor_1, R.raw.mfx_azul_cor_2), intArrayOf(R.raw.mfx_azul_abrir_1, R.raw.mfx_azul_abrir_2), intArrayOf(R.raw.mfx_azul_pagina_1, R.raw.mfx_azul_pagina_2, R.raw.mfx_azul_pagina_3), intArrayOf(R.raw.mfx_azul_tick_1, R.raw.mfx_azul_tick_2, R.raw.mfx_azul_tick_3, R.raw.mfx_azul_tick_4, R.raw.mfx_azul_tick_5, R.raw.mfx_azul_tick_6, R.raw.mfx_azul_tick_7)),
        "oceano" to arrayOf(intArrayOf(R.raw.mfx_oceano_feito_1, R.raw.mfx_oceano_feito_2, R.raw.mfx_oceano_feito_3, R.raw.mfx_oceano_feito_4), intArrayOf(R.raw.sfx_oceano_desfazer_1, R.raw.sfx_oceano_desfazer_2, R.raw.sfx_oceano_desfazer_3), intArrayOf(R.raw.mfx_oceano_naofiz_1, R.raw.mfx_oceano_naofiz_2, R.raw.mfx_oceano_naofiz_3), intArrayOf(R.raw.mfx_oceano_marco_1, R.raw.mfx_oceano_marco_2, R.raw.mfx_oceano_marco_3), intArrayOf(R.raw.sfx_oceano_toque_1, R.raw.sfx_oceano_toque_2, R.raw.sfx_oceano_toque_3), intArrayOf(R.raw.mfx_oceano_cor_1, R.raw.mfx_oceano_cor_2), intArrayOf(R.raw.mfx_oceano_abrir_1, R.raw.mfx_oceano_abrir_2), intArrayOf(R.raw.mfx_oceano_pagina_1, R.raw.mfx_oceano_pagina_2, R.raw.mfx_oceano_pagina_3), intArrayOf(R.raw.mfx_oceano_tick_1, R.raw.mfx_oceano_tick_2, R.raw.mfx_oceano_tick_3, R.raw.mfx_oceano_tick_4, R.raw.mfx_oceano_tick_5, R.raw.mfx_oceano_tick_6, R.raw.mfx_oceano_tick_7)),
        "violeta" to arrayOf(intArrayOf(R.raw.mfx_violeta_feito_1, R.raw.mfx_violeta_feito_2, R.raw.mfx_violeta_feito_3, R.raw.mfx_violeta_feito_4), intArrayOf(R.raw.sfx_violeta_desfazer_1, R.raw.sfx_violeta_desfazer_2, R.raw.sfx_violeta_desfazer_3), intArrayOf(R.raw.mfx_violeta_naofiz_1, R.raw.mfx_violeta_naofiz_2, R.raw.mfx_violeta_naofiz_3), intArrayOf(R.raw.mfx_violeta_marco_1, R.raw.mfx_violeta_marco_2, R.raw.mfx_violeta_marco_3), intArrayOf(R.raw.sfx_violeta_toque_1, R.raw.sfx_violeta_toque_2, R.raw.sfx_violeta_toque_3), intArrayOf(R.raw.mfx_violeta_cor_1, R.raw.mfx_violeta_cor_2), intArrayOf(R.raw.mfx_violeta_abrir_1, R.raw.mfx_violeta_abrir_2), intArrayOf(R.raw.mfx_violeta_pagina_1, R.raw.mfx_violeta_pagina_2, R.raw.mfx_violeta_pagina_3), intArrayOf(R.raw.mfx_violeta_tick_1, R.raw.mfx_violeta_tick_2, R.raw.mfx_violeta_tick_3, R.raw.mfx_violeta_tick_4, R.raw.mfx_violeta_tick_5, R.raw.mfx_violeta_tick_6, R.raw.mfx_violeta_tick_7)),
        "ambar" to arrayOf(intArrayOf(R.raw.mfx_ambar_feito_1, R.raw.mfx_ambar_feito_2, R.raw.mfx_ambar_feito_3, R.raw.mfx_ambar_feito_4), intArrayOf(R.raw.sfx_ambar_desfazer_1, R.raw.sfx_ambar_desfazer_2, R.raw.sfx_ambar_desfazer_3), intArrayOf(R.raw.mfx_ambar_naofiz_1, R.raw.mfx_ambar_naofiz_2, R.raw.mfx_ambar_naofiz_3), intArrayOf(R.raw.mfx_ambar_marco_1, R.raw.mfx_ambar_marco_2, R.raw.mfx_ambar_marco_3), intArrayOf(R.raw.sfx_ambar_toque_1, R.raw.sfx_ambar_toque_2, R.raw.sfx_ambar_toque_3), intArrayOf(R.raw.mfx_ambar_cor_1, R.raw.mfx_ambar_cor_2), intArrayOf(R.raw.mfx_ambar_abrir_1, R.raw.mfx_ambar_abrir_2), intArrayOf(R.raw.mfx_ambar_pagina_1, R.raw.mfx_ambar_pagina_2, R.raw.mfx_ambar_pagina_3), intArrayOf(R.raw.mfx_ambar_tick_1, R.raw.mfx_ambar_tick_2, R.raw.mfx_ambar_tick_3, R.raw.mfx_ambar_tick_4, R.raw.mfx_ambar_tick_5, R.raw.mfx_ambar_tick_6, R.raw.mfx_ambar_tick_7)),
        "terracota" to arrayOf(intArrayOf(R.raw.mfx_terracota_feito_1, R.raw.mfx_terracota_feito_2, R.raw.mfx_terracota_feito_3, R.raw.mfx_terracota_feito_4), intArrayOf(R.raw.sfx_terracota_desfazer_1, R.raw.sfx_terracota_desfazer_2, R.raw.sfx_terracota_desfazer_3), intArrayOf(R.raw.mfx_terracota_naofiz_1, R.raw.mfx_terracota_naofiz_2, R.raw.mfx_terracota_naofiz_3), intArrayOf(R.raw.mfx_terracota_marco_1, R.raw.mfx_terracota_marco_2, R.raw.mfx_terracota_marco_3), intArrayOf(R.raw.sfx_terracota_toque_1, R.raw.sfx_terracota_toque_2, R.raw.sfx_terracota_toque_3), intArrayOf(R.raw.mfx_terracota_cor_1, R.raw.mfx_terracota_cor_2), intArrayOf(R.raw.mfx_terracota_abrir_1, R.raw.mfx_terracota_abrir_2), intArrayOf(R.raw.mfx_terracota_pagina_1, R.raw.mfx_terracota_pagina_2, R.raw.mfx_terracota_pagina_3), intArrayOf(R.raw.mfx_terracota_tick_1, R.raw.mfx_terracota_tick_2, R.raw.mfx_terracota_tick_3, R.raw.mfx_terracota_tick_4, R.raw.mfx_terracota_tick_5, R.raw.mfx_terracota_tick_6, R.raw.mfx_terracota_tick_7)),
        "grafite" to arrayOf(intArrayOf(R.raw.mfx_grafite_feito_1, R.raw.mfx_grafite_feito_2, R.raw.mfx_grafite_feito_3, R.raw.mfx_grafite_feito_4), intArrayOf(R.raw.sfx_grafite_desfazer_1, R.raw.sfx_grafite_desfazer_2, R.raw.sfx_grafite_desfazer_3), intArrayOf(R.raw.mfx_grafite_naofiz_1, R.raw.mfx_grafite_naofiz_2, R.raw.mfx_grafite_naofiz_3), intArrayOf(R.raw.mfx_grafite_marco_1, R.raw.mfx_grafite_marco_2, R.raw.mfx_grafite_marco_3), intArrayOf(R.raw.sfx_grafite_toque_1, R.raw.sfx_grafite_toque_2, R.raw.sfx_grafite_toque_3), intArrayOf(R.raw.mfx_grafite_cor_1, R.raw.mfx_grafite_cor_2), intArrayOf(R.raw.mfx_grafite_abrir_1, R.raw.mfx_grafite_abrir_2), intArrayOf(R.raw.mfx_grafite_pagina_1, R.raw.mfx_grafite_pagina_2, R.raw.mfx_grafite_pagina_3), intArrayOf(R.raw.mfx_grafite_tick_1, R.raw.mfx_grafite_tick_2, R.raw.mfx_grafite_tick_3, R.raw.mfx_grafite_tick_4, R.raw.mfx_grafite_tick_5, R.raw.mfx_grafite_tick_6, R.raw.mfx_grafite_tick_7)),
    )
}
