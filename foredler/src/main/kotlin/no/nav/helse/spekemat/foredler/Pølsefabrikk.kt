package no.nav.helse.spekemat.foredler

import no.nav.helse.spekemat.foredler.Pølsestatus.ÅPEN
import java.util.*

class Pølsefabrikk private constructor(
    private val pakken: MutableList<Pølserad>
) {
    constructor() : this(mutableListOf())

    companion object {
        fun gjenopprett(rader: List<PølseradDto>) =
            Pølsefabrikk(rader.map { Pølserad.fraDto(it) }.toMutableList())
    }

    fun nyPølse(pølse: Pølse) {
        if (skalLageNyrad(pølse)) return lagNyRad(pølse)
        pakken[0] = pakken[0].nyPølse(pølse)
    }

    fun oppdaterPølse(vedtaksperiodeId: UUID, generasjonId: UUID, status: Pølsestatus): PølseDto {
        pakken[0] = pakken[0].oppdaterPølse(vedtaksperiodeId, generasjonId, status)
        return pakken[0].pølser.single { it.vedtaksperiodeId == vedtaksperiodeId }.dto()
    }

    private fun skalLageNyrad(pølse: Pølse) =
        pakken.isEmpty() || pakken[0].skalLageNyRad(pølse)

    private fun lagNyRad(pølse: Pølse) {
        if (pakken.isEmpty()) {
            pakken.add(Pølserad(listOf(pølse), pølse.kilde))
            return
        }

        val pølserad = pakken[0].nyPølserad(pølse)
        pakken[0] = pakken[0].fjernPølserTilBehandling()
        pakken.add(0, pølserad)
    }
    fun pakke() = pakken.map { it.dto() }
}

data class Pølserad(
    val pølser: List<Pølse>,
    val kildeTilRad: UUID
) {
    fun skalLageNyRad(other: Pølse) =
        pølser.any { pølse -> other.erNyPølseAv(pølse) && this.kildeTilRad != other.kilde }

    fun fjernPølserTilBehandling() =
        this.copy(pølser = pølser.filterNot { it.status == ÅPEN })

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

enum class Pølsestatus { ÅPEN, LUKKET, FORKASTET }

data class Pølse(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    // hvorvidt generasjonen er åpen for endringer (dvs. til behandling) eller ikke (vedtak fattet / generasjon avsluttet)
    val status: Pølsestatus,
    // tingen som gjorde at generasjonen ble opprettet
    val kilde: UUID
) {
    fun erNyPølseAv(other: Pølse) = this.vedtaksperiodeId == other.vedtaksperiodeId && this.kilde != other.kilde && this.status == ÅPEN
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

data class PølseradDto(
    val pølser: List<PølseDto>,
    val kildeTilRad: UUID
)
data class PølseDto(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    val status: Pølsestatus,
    // tingen som gjorde at generasjonen ble opprettet
    val kilde: UUID
)