package com.doanh.chroni.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputsTest {
    @Test fun virguleEtPoint() {
        assertEquals(0.55, Inputs.parseDecimal("0,55")!!, 1e-12)
        assertEquals(0.55, Inputs.parseDecimal(" 0.55 ")!!, 1e-12)
        assertEquals(1200.0, Inputs.parseDecimal("1 200")!!, 1e-12)
        assertNull(Inputs.parseDecimal(""))
        assertNull(Inputs.parseDecimal("abc"))
        assertNull(Inputs.parseDecimal("1,2,3"))
        assertNull(Inputs.parseDecimal("NaN"))
    }

    @Test fun parametresComplets() {
        val (p, e) = Inputs.toParams("0,55", "10", "")
        assertEquals(ShotParams(0.55, 10.0, null), p)
        assertEquals(emptyList<InputError>(), e)
        val (p2, _) = Inputs.toParams("0,55", "10", "-5")
        assertEquals(-5.0, p2!!.temperatureC!!, 1e-12)
    }

    @Test fun erreursDeSaisie() {
        assertEquals(listOf(InputError.MASS, InputError.DISTANCE), Inputs.toParams("", "x", "").second)
        assertEquals(listOf(InputError.TEMPERATURE), Inputs.toParams("1", "10", "chaud").second)
        assertEquals(listOf(InputError.DISTANCE), Inputs.toParams("1", "0", "").second)
        assertEquals(listOf(InputError.MASS), Inputs.toParams("5000", "10", "").second)
    }
}
