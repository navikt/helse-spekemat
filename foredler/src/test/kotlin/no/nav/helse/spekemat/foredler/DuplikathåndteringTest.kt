package no.nav.helse.spekemat.foredler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class DuplikathåndteringTest : PølseTest() {

    @Test
    fun `en duplikat ny pølse`() {
        val p1 = 1.januar til 5.januar
        val p2 = p1 fordi UUID.randomUUID()

        fabrikk.nyPølse(p1)
        fabrikk.nyPølse(p2)

        val result = fabrikk.pakke()
        assertEquals(1, result.size) // forventer én rad
        assertEquals(setOf(p1), result.single()) // rekkefølgen på rad 1
    }

    @Test
    fun `en duplikat ny pølse etter at pølsen er lukket`() {
        val p1 = 1.januar til 5.januar
        val p2 = p1 fordi UUID.randomUUID()

        fabrikk.nyPølse(p1)
        fabrikk.lukketPølse(p1)
        fabrikk.nyPølse(p2) // f.eks. at spekemat har lest inn generasjon_opprettet på nytt, eller at noen har sendt feil

        val result = fabrikk.pakke()
        assertEquals(1, result.size) // forventer én rad
        assertEquals(setOf(p1.lukket()), result.single()) // rekkefølgen på rad 1
    }

    @Test
    fun `en duplikat ny pølse etter at pølsen er revurdert`() {
        val p1 = 1.januar til 5.januar
        val p1Revurdering = p1.nyGenerasjon()
        val p2 = p1 fordi UUID.randomUUID()

        fabrikk.nyPølse(p1)
        fabrikk.lukketPølse(p1)
        fabrikk.nyPølse(p1Revurdering)
        fabrikk.lukketPølse(p1Revurdering.lukket())
        fabrikk.nyPølse(p2) // f.eks. at spekemat har lest inn generasjon_opprettet på nytt, eller at noen har sendt feil

        val result = fabrikk.pakke()
        assertEquals(2, result.size)
        assertEquals(setOf(p1Revurdering.lukket()), result[0].pølser)
        assertEquals(setOf(p1.lukket()), result[1].pølser)
    }
}