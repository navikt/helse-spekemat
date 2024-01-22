package no.nav.helse.spekemat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class PølsefabrikkTest {

    private lateinit var fabrikk: Pølsefabrikk

    @BeforeEach
    fun setup() {
        fabrikk = Pølsefabrikk()
    }

    @Test
    fun `en ny pølse`() {
        val p1 = 1.januar til 5.januar

        fabrikk.nyPølse(p1)

        val result = fabrikk.pakke()
        assertEquals(1, result.size) // forventer én rad
        assertEquals(listOf(p1), result.single()) // rekkefølgen på rad 1
    }

    @Test
    fun `to pølser`() {
        val p1 = 1.januar til 5.januar
        val p2 = 6.januar til 10.januar

        fabrikk.nyPølse(p1)
        fabrikk.nyPølse(p2)

        val result = fabrikk.pakke()
        assertEquals(1, result.size) // forventer én rad
        assertEquals(listOf(p2, p1), result.single()) // rekkefølgen på rad 1
    }

    private fun pølse(fom: LocalDate, tom: LocalDate) = Pølse(UUID.randomUUID(), fom, tom)

    infix fun LocalDate.til(tom: LocalDate) = pølse(this, tom)

    private val mandag = LocalDate.of(2018, 1, 1)
    val Int.januar get() = mandag.withDayOfMonth(this).withMonth(1)
}