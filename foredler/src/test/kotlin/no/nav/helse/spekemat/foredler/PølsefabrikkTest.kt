package no.nav.helse.spekemat.foredler

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
        assertEquals(setOf(p1), result.single()) // rekkefølgen på rad 1
    }

    @Test
    fun `to pølser`() {
        val p1 = 1.januar til 5.januar
        val p2 = 6.januar til 10.januar

        fabrikk.nyPølse(p1)
        fabrikk.nyPølse(p2)

        val result = fabrikk.pakke()
        assertEquals(1, result.size) // forventer én rad
        assertEquals(setOf(p2, p1), result.single()) // rekkefølgen på rad 1
    }

    @Test
    fun `to pølser - ett vedtak før forlengelse`() {
        val p1 = 1.januar til 5.januar
        val p2 = 6.januar til 10.januar

        fabrikk.nyPølse(p1)
        fabrikk.lukketPølse(p1)
        fabrikk.nyPølse(p2)

        val result = fabrikk.pakke()
        assertEquals(1, result.size) // forventer én rad
        assertEquals(setOf(p2, p1.lukket()), result.single()) // rekkefølgen på rad 1
    }

    @Test
    fun `to pølser - ett vedtak etter forlengelse`() {
        val p1 = 1.januar til 5.januar
        val p2 = 6.januar til 10.januar

        fabrikk.nyPølse(p1)
        fabrikk.nyPølse(p2)
        fabrikk.lukketPølse(p1)

        val result = fabrikk.pakke()
        assertEquals(1, result.size) // forventer én rad
        assertEquals(setOf(p2, p1.lukket()), result.single()) // rekkefølgen på rad 1
    }

    @Test
    fun `ny rad ved revurdering`() {
        val v1 = UUID.randomUUID()
        val v2 = UUID.randomUUID()

        val p1 = 1.januar til 5.januar som v1
        val p2 = 6.januar til 10.januar som v2
        val revurderingkilde = UUID.randomUUID()
        val p2Revurdering = p2.fordi(revurderingkilde)

        fabrikk.nyPølse(p1)
        fabrikk.nyPølse(p2)
        fabrikk.lukketPølse(p1) // vedtak fattet
        fabrikk.lukketPølse(p2) // vedtak fattet
        fabrikk.nyPølse(p2Revurdering) // en ny pølse for p2 må bety at forrige pølse er avsluttet

        val result = fabrikk.pakke()
        assertEquals(2, result.size) // forventer to rader
        assertEquals(revurderingkilde, result[0].kildeTilRad)
        assertEquals(setOf(p2Revurdering, p1.lukket()), result[0]) // rekkefølgen på rad 1
        assertEquals(setOf(p2.lukket(), p1.lukket()), result[1]) // rekkefølgen på rad 2
    }

    @Test
    fun `lager ikke flere rader dersom mange pølser opprettes fra samme revurdering`() {
        val v1 = UUID.randomUUID()
        val v2 = UUID.randomUUID()
        val revurderingkilde = UUID.randomUUID()

        val p1 = 1.januar til 5.januar som v1
        val p2 = 6.januar til 10.januar som v2
        val p2Revurdering = p2.fordi(revurderingkilde)
        val p1Revurdering = p1.fordi(revurderingkilde)

        fabrikk.nyPølse(p1)
        fabrikk.nyPølse(p2)
        fabrikk.lukketPølse(p1)
        fabrikk.lukketPølse(p2)
        fabrikk.nyPølse(p2Revurdering) // en ny pølse for p2 må bety at forrige pølse er avsluttet
        fabrikk.nyPølse(p1Revurdering) // en ny pølse for p2 må bety at forrige pølse er avsluttet

        val result = fabrikk.pakke()
        assertEquals(2, result.size) // forventer to rader
        assertEquals(setOf(p2Revurdering, p1Revurdering), result[0]) // rekkefølgen på rad 1
        assertEquals(setOf(p2.lukket(), p1.lukket()), result[1]) // rekkefølgen på rad 2 er uvesentlig:
    }

    @Test
    fun `out of order`() {
        val v1 = UUID.randomUUID()
        val v2 = UUID.randomUUID()
        val v3 = UUID.randomUUID()
        val oufOrderOrderkilde = UUID.randomUUID()

        val p1 = 10.januar til 20.januar som v1
        val p2 = 21.januar til 31.januar som v2
        val p3 = 1.januar til 5.januar som v3 fordi oufOrderOrderkilde
        val p2Revurdering = p2.fordi(oufOrderOrderkilde)
        val p1Revurdering = p1.fordi(oufOrderOrderkilde)

        fabrikk.nyPølse(p1)
        fabrikk.nyPølse(p2)

        fabrikk.lukketPølse(p1) // vedtak fattet
        fabrikk.lukketPølse(p2) // vedtak fattet

        fabrikk.nyPølse(p3)
        fabrikk.nyPølse(p2Revurdering)
        fabrikk.nyPølse(p1Revurdering)

        val result = fabrikk.pakke()
        assertEquals(2, result.size) // forventer to rader
        assertEquals(setOf(p3, p2Revurdering, p1Revurdering), result[0])
        assertEquals(setOf(p2.lukket(), p1.lukket()), result[1])
    }

    @Test
    fun `ny revurdering av tidligere revurdert periode, men med forlengelser fremdeles til behandling`() {
        val v1 = UUID.randomUUID()
        val v2 = UUID.randomUUID()
        val v3 = UUID.randomUUID()
        val revurderingkilde = UUID.randomUUID()

        val p3 = 1.januar til 5.januar som v1
        val p1 = 10.januar til 20.januar som v2
        val p2 = 21.januar til 31.januar som v3

        val p3Revurdering = p3.fordi(revurderingkilde)
        val p2Revurdering = p2.fordi(revurderingkilde)
        val p1Revurdering = p1.fordi(revurderingkilde)
        val p1Revurdering2 = p1.fordi(UUID.randomUUID())

        fabrikk.nyPølse(p1)
        fabrikk.nyPølse(p2)
        fabrikk.nyPølse(p3)

        fabrikk.lukketPølse(p1)
        fabrikk.lukketPølse(p2)
        fabrikk.lukketPølse(p3)

        // endring på p1 trigger revurdering av alle påfølgende perioder
        fabrikk.nyPølse(p1Revurdering)
        fabrikk.nyPølse(p2Revurdering)
        fabrikk.nyPølse(p3Revurdering)

        fabrikk.lukketPølse(p1Revurdering)

        // korrigering av ferdigstilt revurdering på p1
        fabrikk.nyPølse(p1Revurdering2)
        // fordi p2 og p3 fremdeles er til behandling kommer det ingen nye pølser for dem

        val result = fabrikk.pakke()
        assertEquals(3, result.size) // forventer to rader
        assertEquals(setOf(p3Revurdering, p2Revurdering, p1Revurdering2), result[0])
        assertEquals(setOf(p1Revurdering.lukket()), result[1])
        assertEquals(setOf(p3.lukket(), p2.lukket(), p1.lukket()), result[2])
    }

    private fun pølse(
        vedtaksperiodeId: UUID,
        kilde: UUID = UUID.randomUUID(),
        åpen: Boolean = true,
    ) = Pølse(vedtaksperiodeId, UUID.randomUUID(), åpen, kilde)

    private infix fun LocalDate.til(tom: LocalDate) = pølse(UUID.randomUUID())
    private infix fun Pølse.som(vedtaksperiodeId: UUID) = this.copy(vedtaksperiodeId = vedtaksperiodeId)
    private infix fun Pølse.fordi(kilde: UUID) = this.copy(kilde = kilde)

    private fun Pølse.lukket() = this.copy(åpen = false)

    private val mandag = LocalDate.of(2018, 1, 1)
    private val Int.januar get() = mandag.withDayOfMonth(this).withMonth(1)

    private fun assertEquals(expected: Set<Pølse>, actual: PølseradDto) {
        assertEquals(expected, actual.pølser)
    }
    private fun assertEquals(expected: Set<Pølse>, actual: List<PølseDto>) {
        assertEquals(expected.size, actual.size)
        val ingenMatch = expected.map { it.dto() }.filterNot { it in actual }
        assertEquals(emptyList<PølseDto>(), ingenMatch) { "Det er pølser som ikke finnes i actual" }
    }

    private fun Pølsefabrikk.lukketPølse(pølse: Pølse) {
        this.oppdaterPølse(pølse.vedtaksperiodeId, pølse.generasjonId, åpen = false)
    }
}