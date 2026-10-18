package com.rafael.habito.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Percorre o caminho que mais importa (abrir, rolar a tela, rolar o ano) para o Android
 * pré-compilar esse código na instalação. Rodar com: ./gradlew :app:generateReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun abrirERolar() = rule.collect(packageName = "com.rafael.habito", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(2) {
            device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20)
            device.waitForIdle()
            device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 20)
            device.waitForIdle()
        }
        device.swipe(w * 8 / 10, h * 45 / 100, w * 2 / 10, h * 45 / 100, 20) // o ano, na horizontal
        device.waitForIdle()
    }
}
