package no.nav.helse.spekemat.foredler

import java.util.*

interface Pølsetjeneste {
    fun håndter(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String)
    fun slett(fnr: String)
}

class Pølsetjenesten(private val dao: PølseDao) : Pølsetjeneste {
    override fun håndter(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String) {
        val fabrikk = dao.hent(fnr, yrkesaktivitetidentifikator) ?: Pølsefabrikk()
        fabrikk.nyPølse(Pølse.fraDto(pølse))

        val resultat = fabrikk.pakke()
        dao.opprett(fnr, yrkesaktivitetidentifikator, resultat, pølse.kilde, meldingsreferanseId, hendelsedata)
    }

    override fun slett(fnr: String) {
        dao.slett(fnr)
    }
}