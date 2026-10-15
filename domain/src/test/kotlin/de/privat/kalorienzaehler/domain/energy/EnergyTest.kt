package de.privat.kalorienzaehler.domain.energy

import kotlin.test.Test
import kotlin.test.assertEquals

class EnergyTest {

    @Test
    fun kcalToKjUsesFactor4184() {
        assertEquals(4.184, Energy.kcalToKj(1.0), 1e-9)
        assertEquals(8368.0, Energy.kcalToKj(2000.0), 1e-9)
    }

    @Test
    fun kjToKcalIsTheInverse() {
        assertEquals(2000.0, Energy.kjToKcal(8368.0), 1e-9)
        assertEquals(1234.5, Energy.kjToKcal(Energy.kcalToKj(1234.5)), 1e-9)
    }
}
