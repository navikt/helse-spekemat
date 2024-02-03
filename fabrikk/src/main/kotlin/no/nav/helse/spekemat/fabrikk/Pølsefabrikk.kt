package no.nav.helse.spekemat.fabrikk

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
        if (håndtertFør(pølse)) return
        if (skalLageNyrad(pølse)) return lagNyRad(pølse)
        pakken[0] = pakken[0].nyPølse(pølse)
    }

    private fun håndtertFør(pølse: Pølse) =
        pakken.any { rad ->
            rad.pølser.any { it.generasjonId == pølse.generasjonId }
        }

    fun oppdaterPølse(vedtaksperiodeId: UUID, generasjonId: UUID, status: Pølsestatus): PølseDto {
        if (pakken.isEmpty()) throw TomPølsepakkeException("Pølsepakken er tom")
        pakken[0] = pakken[0].oppdaterPølse(vedtaksperiodeId, generasjonId, status)
        return pakken[0].pølser.singleOrNull { it.vedtaksperiodeId == vedtaksperiodeId }?.dto() ?: throw PølseFinnesIkkeException("Ingen pølse registrert for vedtaksperiodeId=$vedtaksperiodeId og generasjon=$generasjonId")
    }

    private fun skalLageNyrad(pølse: Pølse) =
        pakken.isEmpty() || pakken[0].skalLageNyRad(pølse)

    private fun lagNyRad(pølse: Pølse) {
        if (pakken.isEmpty()) {
            pakken.add(Pølserad(pølse))
            return
        }

        val pølserad = pakken[0].nyPølserad(pølse)
        pakken[0] = pakken[0].fjernPølserTilBehandling()
        pakken.add(0, pølserad)
    }
    fun pakke() = pakken.map { it.dto() }
}

class TomPølsepakkeException(override val message: String?) : IllegalStateException()
class PølseFinnesIkkeException(override val message: String?) : IllegalStateException()