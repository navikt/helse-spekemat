package no.nav.helse.spekemat.fabrikk

import java.util.*

data class Pølserad(
    val pølser: List<Pølse>,
    val kildeTilRad: UUID
) {
    fun skalLageNyRad(other: Pølse) =
        pølser.any { pølse -> other.erNyPølseAv(pølse) && this.kildeTilRad != other.kilde }

    fun fjernPølserTilBehandling() =
        this.copy(pølser = pølser.filterNot { it.status == Pølsestatus.ÅPEN })

    fun nyPølserad(pølse: Pølse): Pølserad {
        return this
            .nyPølse(pølse)
            .copy(kildeTilRad = pølse.kilde)
    }
    fun nyPølse(pølse: Pølse): Pølserad {
        return this.copy(
            pølser = pølser
                .filterNot { it.vedtaksperiodeId == pølse.vedtaksperiodeId }
                .plusElement(pølse)
        )
    }

    fun oppdaterPølse(vedtaksperiodeId: UUID, generasjonId: UUID, status: Pølsestatus): Pølserad {
        return this.copy(
            pølser = pølser.map { it.oppdaterPølse(vedtaksperiodeId, generasjonId, status) }
        )
    }
    fun dto() = PølseradDto(pølser.map { it.dto() }, kildeTilRad)

    companion object {
        fun fraDto(dto: PølseradDto) = Pølserad(
            pølser = dto.pølser.map { Pølse.fraDto(it) },
            kildeTilRad = dto.kildeTilRad
        )
    }
}