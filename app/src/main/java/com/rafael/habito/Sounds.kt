package com.rafael.habito

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import kotlin.random.Random

/** Os eventos que soam. A ordem é a das colunas da tabela; TICK são os sete traços da régua, do grave ao agudo. */
enum class Sfx { FEITO, DESFAZER, NAO_FIZ, MARCO, TOQUE, COR, ABRIR, PAGINA, TICK, SEGURAR }

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
        // o marco começa por cima do feito que o trouxe: o feito baixa numa rampa (sem corte seco)
        if (sfx == Sfx.MARCO && lastStream != 0) duck(lastStream, 0.35f, 120)
        // a abertura não toca com o celular no vibrar (abrir o app numa reunião)
        if (sfx == Sfx.ABRIR && vibrating(ctx)) return
        start(row[v], if (sfx == Sfx.MARCO) 1 else 0, detune = sfx == Sfx.TOQUE)
    }

    /** O traço [i] (0..6) da régua acendendo. */
    @Synchronized fun tick(ctx: Context, palette: String, i: Int) {
        if (silent(ctx)) return
        prepare(ctx, palette)
        val row = ids.getOrNull(Sfx.TICK.ordinal) ?: return
        val id = row[i.coerceIn(0, row.size - 1)]
        if (id in loaded) pool.play(id, 0.9f, 0.9f, 0, 0, 1f)
    }

    /** Começa o som de segurar (cresce em ~0,7 s); devolve o stream para parar depois. 0 = nada tocando. */
    @Synchronized fun holdStart(ctx: Context, palette: String): Int {
        if (silent(ctx)) return 0
        prepare(ctx, palette)
        val id = ids.getOrNull(Sfx.SEGURAR.ordinal)?.firstOrNull() ?: return 0
        if (id !in loaded) return 0
        return pool.play(id, 0.85f, 0.85f, 0, 0, 1f)
    }

    /** Para o som de segurar: escoando (soltou antes) ou num corte curto (o feito assume). */
    fun holdStop(stream: Int, fadeMs: Long) {
        if (stream == 0) return
        val h = main
        val steps = 6
        for (i in 1..steps) h.postDelayed({
            val v = 0.85f * (1f - i / steps.toFloat())
            pool.setVolume(stream, v, v)
            if (i == steps) pool.stop(stream)
        }, fadeMs * i / steps)
    }

    /** O tique do tempo voltando: o traço mais grave da régua, bem baixo, no máximo um a cada 45 ms. */
    private var lastScrub = 0L
    @Synchronized fun scrubTick(ctx: Context, palette: String) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastScrub < 70 || silent(ctx)) return
        lastScrub = now
        prepare(ctx, palette)
        val id = ids.getOrNull(Sfx.TICK.ordinal)?.firstOrNull() ?: return
        if (id in loaded) pool.play(id, 0.32f, 0.32f, 0, 0, 0.985f + Random.nextFloat() * 0.03f)
    }

    private fun start(id: Int, priority: Int, detune: Boolean = false) {
        if (id !in loaded) { pending = id; return }
        // cada toque um pouco diferente: até 1 dB de volume; o toque solto ainda varia ±0,5% de altura
        val g = 1f - Random.nextFloat() * 0.11f
        val rate = if (detune) 0.995f + Random.nextFloat() * 0.01f else 1f
        lastStream = pool.play(id, g, g, priority, 0, rate)
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Baixa o volume de um stream até [to] em [ms], em 6 passos. */
    private fun duck(stream: Int, to: Float, ms: Long) {
        for (i in 1..6) main.postDelayed({ val v = 1f - (1f - to) * i / 6f; pool.setVolume(stream, v, v) }, ms * i / 6)
    }

    private fun vibrating(ctx: Context) = ctx.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_VIBRATE

    @Synchronized private fun onLoaded(p: SoundPool, id: Int, status: Int) {
        if (status != 0) return
        loaded += id
        if (id == pending) { pending = 0; lastStream = p.play(id, 1f, 1f, 0, 0, 1f) }
    }

    private val TABLE: Map<String, Array<IntArray>> = mapOf(
        "verde" to arrayOf(intArrayOf(R.raw.mfx_verde_feito_1, R.raw.mfx_verde_feito_2, R.raw.mfx_verde_feito_3, R.raw.mfx_verde_feito_4), intArrayOf(R.raw.sfx_verde_desfazer_1, R.raw.sfx_verde_desfazer_2, R.raw.sfx_verde_desfazer_3), intArrayOf(R.raw.mfx_verde_naofiz_1, R.raw.mfx_verde_naofiz_2, R.raw.mfx_verde_naofiz_3), intArrayOf(R.raw.mfx_verde_marco_1, R.raw.mfx_verde_marco_2, R.raw.mfx_verde_marco_3), intArrayOf(R.raw.sfx_verde_toque_1, R.raw.sfx_verde_toque_2, R.raw.sfx_verde_toque_3), intArrayOf(R.raw.mfx_verde_cor_1, R.raw.mfx_verde_cor_2), intArrayOf(R.raw.mfx_verde_abrir_1, R.raw.mfx_verde_abrir_2), intArrayOf(R.raw.mfx_verde_pagina_1, R.raw.mfx_verde_pagina_2, R.raw.mfx_verde_pagina_3), intArrayOf(R.raw.mfx_verde_tick_1, R.raw.mfx_verde_tick_2, R.raw.mfx_verde_tick_3, R.raw.mfx_verde_tick_4, R.raw.mfx_verde_tick_5, R.raw.mfx_verde_tick_6, R.raw.mfx_verde_tick_7), intArrayOf(R.raw.mfx_verde_segurar_1)),
        "rosa" to arrayOf(intArrayOf(R.raw.mfx_rosa_feito_1, R.raw.mfx_rosa_feito_2, R.raw.mfx_rosa_feito_3, R.raw.mfx_rosa_feito_4), intArrayOf(R.raw.sfx_rosa_desfazer_1, R.raw.sfx_rosa_desfazer_2, R.raw.sfx_rosa_desfazer_3), intArrayOf(R.raw.mfx_rosa_naofiz_1, R.raw.mfx_rosa_naofiz_2, R.raw.mfx_rosa_naofiz_3), intArrayOf(R.raw.mfx_rosa_marco_1, R.raw.mfx_rosa_marco_2, R.raw.mfx_rosa_marco_3), intArrayOf(R.raw.sfx_rosa_toque_1, R.raw.sfx_rosa_toque_2, R.raw.sfx_rosa_toque_3), intArrayOf(R.raw.mfx_rosa_cor_1, R.raw.mfx_rosa_cor_2), intArrayOf(R.raw.mfx_rosa_abrir_1, R.raw.mfx_rosa_abrir_2), intArrayOf(R.raw.mfx_rosa_pagina_1, R.raw.mfx_rosa_pagina_2, R.raw.mfx_rosa_pagina_3), intArrayOf(R.raw.mfx_rosa_tick_1, R.raw.mfx_rosa_tick_2, R.raw.mfx_rosa_tick_3, R.raw.mfx_rosa_tick_4, R.raw.mfx_rosa_tick_5, R.raw.mfx_rosa_tick_6, R.raw.mfx_rosa_tick_7), intArrayOf(R.raw.mfx_rosa_segurar_1)),
        "azul" to arrayOf(intArrayOf(R.raw.mfx_azul_feito_1, R.raw.mfx_azul_feito_2, R.raw.mfx_azul_feito_3, R.raw.mfx_azul_feito_4), intArrayOf(R.raw.sfx_azul_desfazer_1, R.raw.sfx_azul_desfazer_2, R.raw.sfx_azul_desfazer_3), intArrayOf(R.raw.mfx_azul_naofiz_1, R.raw.mfx_azul_naofiz_2, R.raw.mfx_azul_naofiz_3), intArrayOf(R.raw.mfx_azul_marco_1, R.raw.mfx_azul_marco_2, R.raw.mfx_azul_marco_3), intArrayOf(R.raw.sfx_azul_toque_1, R.raw.sfx_azul_toque_2, R.raw.sfx_azul_toque_3), intArrayOf(R.raw.mfx_azul_cor_1, R.raw.mfx_azul_cor_2), intArrayOf(R.raw.mfx_azul_abrir_1, R.raw.mfx_azul_abrir_2), intArrayOf(R.raw.mfx_azul_pagina_1, R.raw.mfx_azul_pagina_2, R.raw.mfx_azul_pagina_3), intArrayOf(R.raw.mfx_azul_tick_1, R.raw.mfx_azul_tick_2, R.raw.mfx_azul_tick_3, R.raw.mfx_azul_tick_4, R.raw.mfx_azul_tick_5, R.raw.mfx_azul_tick_6, R.raw.mfx_azul_tick_7), intArrayOf(R.raw.mfx_azul_segurar_1)),
        "oceano" to arrayOf(intArrayOf(R.raw.mfx_oceano_feito_1, R.raw.mfx_oceano_feito_2, R.raw.mfx_oceano_feito_3, R.raw.mfx_oceano_feito_4), intArrayOf(R.raw.sfx_oceano_desfazer_1, R.raw.sfx_oceano_desfazer_2, R.raw.sfx_oceano_desfazer_3), intArrayOf(R.raw.mfx_oceano_naofiz_1, R.raw.mfx_oceano_naofiz_2, R.raw.mfx_oceano_naofiz_3), intArrayOf(R.raw.mfx_oceano_marco_1, R.raw.mfx_oceano_marco_2, R.raw.mfx_oceano_marco_3), intArrayOf(R.raw.sfx_oceano_toque_1, R.raw.sfx_oceano_toque_2, R.raw.sfx_oceano_toque_3), intArrayOf(R.raw.mfx_oceano_cor_1, R.raw.mfx_oceano_cor_2), intArrayOf(R.raw.mfx_oceano_abrir_1, R.raw.mfx_oceano_abrir_2), intArrayOf(R.raw.mfx_oceano_pagina_1, R.raw.mfx_oceano_pagina_2, R.raw.mfx_oceano_pagina_3), intArrayOf(R.raw.mfx_oceano_tick_1, R.raw.mfx_oceano_tick_2, R.raw.mfx_oceano_tick_3, R.raw.mfx_oceano_tick_4, R.raw.mfx_oceano_tick_5, R.raw.mfx_oceano_tick_6, R.raw.mfx_oceano_tick_7), intArrayOf(R.raw.mfx_oceano_segurar_1)),
        "violeta" to arrayOf(intArrayOf(R.raw.mfx_violeta_feito_1, R.raw.mfx_violeta_feito_2, R.raw.mfx_violeta_feito_3, R.raw.mfx_violeta_feito_4), intArrayOf(R.raw.sfx_violeta_desfazer_1, R.raw.sfx_violeta_desfazer_2, R.raw.sfx_violeta_desfazer_3), intArrayOf(R.raw.mfx_violeta_naofiz_1, R.raw.mfx_violeta_naofiz_2, R.raw.mfx_violeta_naofiz_3), intArrayOf(R.raw.mfx_violeta_marco_1, R.raw.mfx_violeta_marco_2, R.raw.mfx_violeta_marco_3), intArrayOf(R.raw.sfx_violeta_toque_1, R.raw.sfx_violeta_toque_2, R.raw.sfx_violeta_toque_3), intArrayOf(R.raw.mfx_violeta_cor_1, R.raw.mfx_violeta_cor_2), intArrayOf(R.raw.mfx_violeta_abrir_1, R.raw.mfx_violeta_abrir_2), intArrayOf(R.raw.mfx_violeta_pagina_1, R.raw.mfx_violeta_pagina_2, R.raw.mfx_violeta_pagina_3), intArrayOf(R.raw.mfx_violeta_tick_1, R.raw.mfx_violeta_tick_2, R.raw.mfx_violeta_tick_3, R.raw.mfx_violeta_tick_4, R.raw.mfx_violeta_tick_5, R.raw.mfx_violeta_tick_6, R.raw.mfx_violeta_tick_7), intArrayOf(R.raw.mfx_violeta_segurar_1)),
        "ambar" to arrayOf(intArrayOf(R.raw.mfx_ambar_feito_1, R.raw.mfx_ambar_feito_2, R.raw.mfx_ambar_feito_3, R.raw.mfx_ambar_feito_4), intArrayOf(R.raw.sfx_ambar_desfazer_1, R.raw.sfx_ambar_desfazer_2, R.raw.sfx_ambar_desfazer_3), intArrayOf(R.raw.mfx_ambar_naofiz_1, R.raw.mfx_ambar_naofiz_2, R.raw.mfx_ambar_naofiz_3), intArrayOf(R.raw.mfx_ambar_marco_1, R.raw.mfx_ambar_marco_2, R.raw.mfx_ambar_marco_3), intArrayOf(R.raw.sfx_ambar_toque_1, R.raw.sfx_ambar_toque_2, R.raw.sfx_ambar_toque_3), intArrayOf(R.raw.mfx_ambar_cor_1, R.raw.mfx_ambar_cor_2), intArrayOf(R.raw.mfx_ambar_abrir_1, R.raw.mfx_ambar_abrir_2), intArrayOf(R.raw.mfx_ambar_pagina_1, R.raw.mfx_ambar_pagina_2, R.raw.mfx_ambar_pagina_3), intArrayOf(R.raw.mfx_ambar_tick_1, R.raw.mfx_ambar_tick_2, R.raw.mfx_ambar_tick_3, R.raw.mfx_ambar_tick_4, R.raw.mfx_ambar_tick_5, R.raw.mfx_ambar_tick_6, R.raw.mfx_ambar_tick_7), intArrayOf(R.raw.mfx_ambar_segurar_1)),
        "terracota" to arrayOf(intArrayOf(R.raw.mfx_terracota_feito_1, R.raw.mfx_terracota_feito_2, R.raw.mfx_terracota_feito_3, R.raw.mfx_terracota_feito_4), intArrayOf(R.raw.sfx_terracota_desfazer_1, R.raw.sfx_terracota_desfazer_2, R.raw.sfx_terracota_desfazer_3), intArrayOf(R.raw.mfx_terracota_naofiz_1, R.raw.mfx_terracota_naofiz_2, R.raw.mfx_terracota_naofiz_3), intArrayOf(R.raw.mfx_terracota_marco_1, R.raw.mfx_terracota_marco_2, R.raw.mfx_terracota_marco_3), intArrayOf(R.raw.sfx_terracota_toque_1, R.raw.sfx_terracota_toque_2, R.raw.sfx_terracota_toque_3), intArrayOf(R.raw.mfx_terracota_cor_1, R.raw.mfx_terracota_cor_2), intArrayOf(R.raw.mfx_terracota_abrir_1, R.raw.mfx_terracota_abrir_2), intArrayOf(R.raw.mfx_terracota_pagina_1, R.raw.mfx_terracota_pagina_2, R.raw.mfx_terracota_pagina_3), intArrayOf(R.raw.mfx_terracota_tick_1, R.raw.mfx_terracota_tick_2, R.raw.mfx_terracota_tick_3, R.raw.mfx_terracota_tick_4, R.raw.mfx_terracota_tick_5, R.raw.mfx_terracota_tick_6, R.raw.mfx_terracota_tick_7), intArrayOf(R.raw.mfx_terracota_segurar_1)),
        "grafite" to arrayOf(intArrayOf(R.raw.mfx_grafite_feito_1, R.raw.mfx_grafite_feito_2, R.raw.mfx_grafite_feito_3, R.raw.mfx_grafite_feito_4), intArrayOf(R.raw.sfx_grafite_desfazer_1, R.raw.sfx_grafite_desfazer_2, R.raw.sfx_grafite_desfazer_3), intArrayOf(R.raw.mfx_grafite_naofiz_1, R.raw.mfx_grafite_naofiz_2, R.raw.mfx_grafite_naofiz_3), intArrayOf(R.raw.mfx_grafite_marco_1, R.raw.mfx_grafite_marco_2, R.raw.mfx_grafite_marco_3), intArrayOf(R.raw.sfx_grafite_toque_1, R.raw.sfx_grafite_toque_2, R.raw.sfx_grafite_toque_3), intArrayOf(R.raw.mfx_grafite_cor_1, R.raw.mfx_grafite_cor_2), intArrayOf(R.raw.mfx_grafite_abrir_1, R.raw.mfx_grafite_abrir_2), intArrayOf(R.raw.mfx_grafite_pagina_1, R.raw.mfx_grafite_pagina_2, R.raw.mfx_grafite_pagina_3), intArrayOf(R.raw.mfx_grafite_tick_1, R.raw.mfx_grafite_tick_2, R.raw.mfx_grafite_tick_3, R.raw.mfx_grafite_tick_4, R.raw.mfx_grafite_tick_5, R.raw.mfx_grafite_tick_6, R.raw.mfx_grafite_tick_7), intArrayOf(R.raw.mfx_grafite_segurar_1)),
    )
}
