package no.nav.helse.spekemat.fabrikk

import java.util.*

data class Pølserad(
    val pølser: List<Pølse>,
    val kildeTilRad: UUID,
    val sisteKildeId: UUID
) {
    constructor(pølse: Pølse) : this(listOf(pølse), pølse.kilde, pølse.kilde)
    fun skalLageNyRad(other: Pølse) =
        pølser.any { pølse -> erNyVersjonAvPølse(other, pølse) } && !kanGjenbrukeRad()

    private fun kanGjenbrukeRad() =
        allePølserKyttetTilForrigeKildeErForkastet() || allePølserRadenErOpprettetMedErÅpen()

    private fun erNyVersjonAvPølse(other: Pølse, pølse: Pølse) =
        other.erNyPølseAv(pølse) && this.kildeTilRad != other.kilde

    private fun allePølserKyttetTilForrigeKildeErForkastet() = pølser
        .filter { it.kilde == sisteKildeId }
        .also {
            check(it.isNotEmpty()) { "Finner ingen pølser knyttet til forrige kildeID, dette må være en feil" }
        }
        .all { it.status == Pølsestatus.FORKASTET }

    private fun allePølserRadenErOpprettetMedErÅpen() =
        pølser
            .filter { it.kilde == kildeTilRad }
            .also {
                check(it.isNotEmpty()) { "Finner ingen pølser knyttet til kildeID som opprettet raden, dette må være en feil" }
            }
            .all { it.status == Pølsestatus.ÅPEN }

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