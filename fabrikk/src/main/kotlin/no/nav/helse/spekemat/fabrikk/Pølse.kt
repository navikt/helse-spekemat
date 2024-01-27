package no.nav.helse.spekemat.fabrikk

import java.util.*

data class Pølse(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    // hvorvidt generasjonen er åpen for endringer (dvs. til behandling) eller ikke (vedtak fattet / generasjon avsluttet)
    val status: Pølsestatus,
    // tingen som gjorde at generasjonen ble opprettet
    val kilde: UUID
) {
    fun erNyPølseAv(other: Pølse): Boolean {
        // må være samme vedtaksperiode
        return this.vedtaksperiodeId == other.vedtaksperiodeId
    }
    fun dto() = PølseDto(
        vedtaksperiodeId = vedtaksperiodeId,
        generasjonId = generasjonId,
        status = status,
        kilde = kilde
    )

    fun oppdaterPølse(vedtaksperiodeId: UUID, generasjonId: UUID, status: Pølsestatus): Pølse {
        if (this.vedtaksperiodeId != vedtaksperiodeId) return this
        check(this.generasjonId == generasjonId) {
            "Det er gjort forsøk på å oppdatere en generasjon som ikke samsvarer med den som er registrert i nyeste rad"
        }
        return this.copy(status = status)
    }

    companion object {
        fun fraDto(dto: PølseDto) = Pølse(
            vedtaksperiodeId = dto.vedtaksperiodeId,
            generasjonId = dto.generasjonId,
            status = dto.status,
            kilde = dto.kilde
        )
    }
}