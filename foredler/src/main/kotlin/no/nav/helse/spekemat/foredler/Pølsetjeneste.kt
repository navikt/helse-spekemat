package no.nav.helse.spekemat.foredler

import org.slf4j.LoggerFactory
import java.util.*

interface Pølsetjeneste {
    fun nyPølse(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String)
    fun oppdaterPølse(fnr: String, yrkesaktivitetidentifikator: String, vedtaksperiodeId: UUID, generasjonId: UUID, status: Pølsestatus, meldingsreferanseId: UUID, hendelsedata: String)
    fun hent(fnr: String): List<YrkesaktivitetDto>
    fun slett(fnr: String)
}

class Pølsetjenesten(
    private val dao: PølseDao,
    private val spleisClient: SpleisClient
) : Pølsetjeneste {
    private companion object {
        private val logg = LoggerFactory.getLogger(this::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val UKJENT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    }
    override fun nyPølse(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String) {
        val fabrikk = hentPølsefabrikk(fnr, yrkesaktivitetidentifikator)
        fabrikk.nyPølse(Pølse.fraDto(pølse))

        val resultat = fabrikk.pakke()
        dao.opprett(fnr, yrkesaktivitetidentifikator, resultat, pølse.kilde, meldingsreferanseId, hendelsedata)
    }

    override fun oppdaterPølse(
        fnr: String,
        yrkesaktivitetidentifikator: String,
        vedtaksperiodeId: UUID,
        generasjonId: UUID,
        status: Pølsestatus,
        meldingsreferanseId: UUID,
        hendelsedata: String
    ) {
        val fabrikk = hentPølsefabrikk(fnr, yrkesaktivitetidentifikator)
        val pølse = fabrikk.oppdaterPølse(vedtaksperiodeId, generasjonId, status)

        val resultat = fabrikk.pakke()
        dao.opprett(fnr, yrkesaktivitetidentifikator, resultat, pølse.kilde, meldingsreferanseId, hendelsedata)
    }

    private fun hentPølsefabrikk(fnr: String, yrkesaktivitetidentifikator: String): Pølsefabrikk {
        val person = dao.hent(fnr).takeUnless(List<*>::isEmpty) ?: hentPersonFraSpleis(fnr)
        return person.singleOrNull { it.yrkesaktivitetidentifikator == yrkesaktivitetidentifikator }?.let {
            Pølsefabrikk.gjenopprett(it.rader)
        } ?: Pølsefabrikk()
    }

    private fun hentPersonFraSpleis(fnr: String): List<YrkesaktivitetDto> {
        val yrkesaktiviteter = spleisClient.hentSpeilJson(fnr)
        yrkesaktiviteter.forEach {
            dao.opprett(fnr, it.yrkesaktivitetidentifikator, it.rader, UKJENT_UUID, UUID.randomUUID(), "{}")
        }
        return yrkesaktiviteter
    }

    override fun hent(fnr: String): List<YrkesaktivitetDto> {
        return dao.hent(fnr)
    }

    override fun slett(fnr: String) {
        dao.slett(fnr)
    }
}