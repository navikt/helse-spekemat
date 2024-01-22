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

    @Test
    fun `to pølser - omvendt rekkefølge`() {
        val p1 = 1.januar til 5.januar
        val p2 = 6.januar til 10.januar

        fabrikk.nyPølse(p2)
        fabrikk.nyPølse(p1)

        val result = fabrikk.pakke()
        assertEquals(1, result.size) // forventer én rad
        assertEquals(listOf(p2, p1), result.single()) // rekkefølgen på rad 1
    }

    @Test
    fun `revurdering`() {
        val v1 = UUID.randomUUID()
        val v2 = UUID.randomUUID()

        val p1 = 1.januar til 5.januar som v1
        val p2 = 6.januar til 10.januar som v2
        val p2Revurdering = p2.fordi(UUID.randomUUID())

        fabrikk.nyPølse(p1)
        fabrikk.nyPølse(p2)
        fabrikk.nyPølse(p2Revurdering) // en ny pølse for p2 må bety at forrige pølse er avsluttet

        val result = fabrikk.pakke()
        assertEquals(2, result.size) // forventer to rader
        assertEquals(listOf(p2Revurdering, p1), result[0]) // rekkefølgen på rad 1
        assertEquals(listOf(p2, p1), result[1]) // rekkefølgen på rad 1
    }

    private fun pølse(
        vedtaksperiodeId: UUID,
        fom: LocalDate,
        tom: LocalDate,
        kilde: UUID = UUID.randomUUID()
    ) = Pølse(vedtaksperiodeId, UUID.randomUUID(), kilde, fom, tom)

    infix fun LocalDate.til(tom: LocalDate) = pølse(UUID.randomUUID(), this, tom)
    infix fun Pølse.som(vedtaksperiodeId: UUID) = this.copy(vedtaksperiodeId = vedtaksperiodeId)
    infix fun Pølse.fordi(kilde: UUID) = this.copy(kilde = kilde)

    private val mandag = LocalDate.of(2018, 1, 1)
    val Int.januar get() = mandag.withDayOfMonth(this).withMonth(1)
}