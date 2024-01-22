package no.nav.helse.spekemat

import java.time.LocalDate
import java.util.*

class Pølsefabrikk {

    private val pakken: MutableList<Pølserad> = mutableListOf()
    private var gjeldendeRad = Pølserad(emptyList(), UUID.fromString("00000000-0000-0000-0000-000000000000"))

    fun nyPølse(pølse: Pølse) {
        // ny rad hvis pølsen finnes fra før
        if (gjeldendeRad.harPølse(pølse)) {
            pakken.add(0, gjeldendeRad)
            gjeldendeRad = gjeldendeRad.nyPølserad(pølse)
            return
        }
        gjeldendeRad = gjeldendeRad.nyPølse(pølse)
    }

    fun pakke(): List<List<Pølse>> {
        val resultat = pakken.map { it.pølser }
        return listOf(gjeldendeRad.pølser) + resultat
    }
}

data class Pølserad(val pølser: List<Pølse>, val kildeTilRad: UUID) {
    fun harPølse(other: Pølse) = pølser.any { pølse -> other.erNyPølseAv(pølse) }

    fun nyPølserad(pølse: Pølse): Pølserad {
        return this
            .copy(pølser = pølser.filterNot { it.vedtaksperiodeId == pølse.vedtaksperiodeId })
            .nyPølse(pølse)
    }
    fun nyPølse(pølse: Pølse): Pølserad {
        return this.copy(
            pølser = pølser.plusElement(pølse).sortedByDescending { it.fom }
        )
    }
}

data class Pølse(
    val vedtaksperiodeId: UUID,
    // generasjonsId?
    val id: UUID,
    // tingen som gjorde at generasjonen ble opprettet
    val kilde: UUID,
    // fom på perioden, hovedsaklig for sortering
    val fom: LocalDate,
    // tom på perioden, hovedsaklig for lesbarhet for oss utviklere?
    val tom: LocalDate
) {
    fun erNyPølseAv(other: Pølse) = this.vedtaksperiodeId == other.vedtaksperiodeId && this.kilde != other.kilde
}