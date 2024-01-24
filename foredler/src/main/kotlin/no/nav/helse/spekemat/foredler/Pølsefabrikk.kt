package no.nav.helse.spekemat.foredler

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

    private fun skalLageNyrad(pølse: Pølse) =
        pakken.isEmpty() || pakken[0].skalLageNyRad(pølse)

    private fun lagNyRad(pølse: Pølse) {
        if (pakken.isEmpty()) {
            pakken.add(Pølserad(listOf(pølse), pølse.kilde))
            return
        }

        val pølserad = pakken[0].nyPølserad(pølse)
        // fjerner pølser med samme kilde fordi det må antyde en out of order-situasjon hvor vi:
        // 1) først opprettes en ny pølse med kilde X, dvs. den legges bare til gjeldende rad. Den nye perioden trigger dog revurdering av senere perioder
        // 2) så mottas generasjon_opprettet på etterfølgende perioder med kilde X, og det skal lages ny rad. Da må pølsen opprettet i steg 1) fjernes
        //    fra raden som "pushes ned", og kun beholdes på siste/nyeste rad
        pakken[0] = pakken[0].fjernPølserMedSammeKilde(pølse)
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

    fun fjernPølserMedSammeKilde(other: Pølse) =
        this.copy(
            pølser = pølser.filterNot { it.kilde == other.kilde }
        )

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

data class YrkesaktivitetDto(
    val yrkesaktivitetidentifikator: String,
    val rader: List<PølseradDto>
)