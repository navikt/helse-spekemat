package no.nav.helse.spekemat

import java.util.*

fun interface Pølsetjeneste {
    fun håndter(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String)
}

class Pølsetjenesten(private val dao: PølseDao) : Pølsetjeneste {
    override fun håndter(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String) {
        val fabrikk = dao.hent(fnr, yrkesaktivitetidentifikator) ?: Pølsefabrikk()
        fabrikk.nyPølse(Pølse.fraDto(pølse))

        val resultat = fabrikk.pakke()
        dao.opprett(fnr, yrkesaktivitetidentifikator, resultat, pølse.kilde, meldingsreferanseId, hendelsedata)
    }
}