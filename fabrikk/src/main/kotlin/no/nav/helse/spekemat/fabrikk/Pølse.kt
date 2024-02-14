package no.nav.helse.spekemat.fabrikk

import java.util.*

data class Pølse(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    // hvorvidt generasjonen er åpen for endringer (dvs. til behandling) eller ikke (vedtak fattet / generasjon avsluttet)
    private val status: Pølsestatus,
    // tingen som gjorde at generasjonen ble opprettet
    private val kilde: UUID
) {
    fun erOpprettetFraSammeKilde(otherKilde: UUID) = this.kilde == otherKilde
    fun erNyPølseAv(other: Pølse): Boolean {
        // må være samme vedtaksperiode
        return this.vedtaksperiodeId == other.vedtaksperiodeId
    }
    fun nyRad() = Pølserad(setOf(this), this.kilde)
    fun nyRadFra(pølserad: Pølserad): Pølserad {
        return pølserad
            .leggTilNyPølse(this)
            .copy(kildeTilRad = this.kilde)
    }

    fun erÅpen() = status == Pølsestatus.ÅPEN

    fun dto() = PølseDto(
        vedtaksperiodeId = vedtaksperiodeId,
        generasjonId = generasjonId,
        status = status,
        kilde = kilde
    )

    fun oppdaterPølse(vedtaksperiodeId: UUID, generasjonId: UUID, status: Pølsestatus): Pølse {
        if (this.vedtaksperiodeId != vedtaksperiodeId) return this
        if (this.generasjonId != generasjonId) throw OppdatererEldreGenerasjonException("Det er gjort forsøk på å oppdatere en generasjon som ikke samsvarer med den som er registrert i nyeste rad")
        return this.copy(status = status)
    }

    override fun hashCode() = vedtaksperiodeId.hashCode()
    override fun equals(other: Any?) =
        other === this || (other is Pølse && other.vedtaksperiodeId == this.vedtaksperiodeId)

    companion object {
        fun fraDto(dto: PølseDto) = Pølse(
            vedtaksperiodeId = dto.vedtaksperiodeId,
            generasjonId = dto.generasjonId,
            status = dto.status,
            kilde = dto.kilde
        )
    }
}

class OppdatererEldreGenerasjonException(override val message: String?) : IllegalStateException()