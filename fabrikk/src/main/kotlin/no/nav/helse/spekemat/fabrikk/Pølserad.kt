package no.nav.helse.spekemat.fabrikk

import java.util.*

data class Pølserad(
    val pølser: Set<Pølse>,
    val kildeTilRad: UUID
) {
    fun skalLageNyRad(other: Pølse): Boolean {
        if (other.erOpprettetFraSammeKilde(kildeTilRad)) return false
        return pølser.any { pølse -> other.erNyPølseAv(pølse) }
    }

    fun fjernPølserTilBehandling() =
        this.copy(pølser = pølser.filterNot { it.erÅpen() }.toSet())

    fun nyPølserad(pølse: Pølse): Pølserad {
        return pølse.nyRadFra(this)
    }
    fun leggTilNyPølse(pølse: Pølse): Pølserad {
        return this.copy(pølser = setOf(pølse) + pølser)
    }

    fun oppdaterPølse(vedtaksperiodeId: UUID, behandlingId: UUID, status: Pølsestatus): Pølserad {
        return this.copy(
            pølser = pølser.map { it.oppdaterPølse(vedtaksperiodeId, behandlingId, status) }.toSet()
        )
    }
    fun dto() = PølseradDto(pølser.map { it.dto() }, kildeTilRad)

    companion object {
        fun fraDto(dto: PølseradDto) = Pølserad(
            pølser = dto.pølser.map { Pølse.fraDto(it) }.toSet(),
            kildeTilRad = dto.kildeTilRad
        )
    }
}