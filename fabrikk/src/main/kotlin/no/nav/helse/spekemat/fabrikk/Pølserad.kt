package no.nav.helse.spekemat.fabrikk

import java.util.*

data class Pølserad(
    val pølser: List<Pølse>,
    val kildeTilRad: UUID,
    val sisteKildeId: UUID
) {
    constructor(pølse: Pølse) : this(listOf(pølse), pølse.kilde, pølse.kilde)
    fun skalLageNyRad(other: Pølse) =
        pølser.any { pølse -> erNyPølseMedNyKilde(other, pølse) && ikkeAllePølserFraForrigeKildeErForkastet() }

    private fun erNyPølseMedNyKilde(other: Pølse, pølse: Pølse) =
        other.erNyPølseAv(pølse) && this.kildeTilRad != other.kilde

    private fun ikkeAllePølserFraForrigeKildeErForkastet() = pølser
        .filter { it.kilde == sisteKildeId }
        .any { it.status != Pølsestatus.FORKASTET }

    fun fjernPølserTilBehandling() =
        this.copy(pølser = pølser.filterNot { it.status == Pølsestatus.ÅPEN })

    fun nyPølserad(pølse: Pølse): Pølserad {
        return this
            .nyPølse(pølse)
            .copy(
                kildeTilRad = pølse.kilde,
                sisteKildeId = pølse.kilde
            )
    }
    fun nyPølse(pølse: Pølse): Pølserad {
        return this.copy(
            pølser = pølser
                .filterNot { it.vedtaksperiodeId == pølse.vedtaksperiodeId }
                .plusElement(pølse),
            sisteKildeId = pølse.kilde
        )
    }

    fun oppdaterPølse(vedtaksperiodeId: UUID, generasjonId: UUID, status: Pølsestatus): Pølserad {
        return this.copy(
            pølser = pølser.map { it.oppdaterPølse(vedtaksperiodeId, generasjonId, status) }
        )
    }
    fun dto() = PølseradDto(pølser.map { it.dto() }, kildeTilRad, sisteKildeId)

    companion object {
        fun fraDto(dto: PølseradDto) = Pølserad(
            pølser = dto.pølser.map { Pølse.fraDto(it) },
            kildeTilRad = dto.kildeTilRad,
            sisteKildeId = dto.sisteKildeId
        )
    }
}