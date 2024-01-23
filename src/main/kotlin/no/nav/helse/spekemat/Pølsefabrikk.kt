package no.nav.helse.spekemat

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
        // ny rad hvis pølsen finnes fra før
        if (pakken.isEmpty() || pakken[0].skalLageNyRad(pølse))
            return pakken.add(0, pakken.getOrNull(0)?.nyPølserad(pølse) ?: Pølserad(listOf(pølse), pølse.kilde))
        pakken[0] = pakken[0].nyPølse(pølse)
    }

    fun pakke() = pakken.map { it.dto() }
}

data class Pølserad(
    val pølser: List<Pølse>,
    val kildeTilRad: UUID
) {
    fun skalLageNyRad(other: Pølse) =
        pølser.any { pølse -> other.erNyPølseAv(pølse) && this.kildeTilRad != other.kilde }

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

    fun dto() = PølseradDto(pølser.map { it.dto() }, kildeTilRad)
    companion object {
        fun fraDto(dto: PølseradDto) = Pølserad(
            pølser = dto.pølser.map { Pølse.fraDto(it) },
            kildeTilRad = dto.kildeTilRad
        )
    }
}

data class Pølse(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    // tingen som gjorde at generasjonen ble opprettet
    val kilde: UUID
) {
    fun erNyPølseAv(other: Pølse) = this.vedtaksperiodeId == other.vedtaksperiodeId && this.kilde != other.kilde
    fun dto() = PølseDto(
        vedtaksperiodeId = vedtaksperiodeId,
        generasjonId = generasjonId,
        kilde = kilde
    )

    companion object {
        fun fraDto(dto: PølseDto) = Pølse(
            vedtaksperiodeId = dto.vedtaksperiodeId,
            generasjonId = dto.generasjonId,
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
    // tingen som gjorde at generasjonen ble opprettet
    val kilde: UUID
)