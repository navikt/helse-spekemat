package no.nav.helse.spekemat.foredler

import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import java.util.*

interface Pølsetjeneste {
    fun nyPølse(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String)
    fun oppdaterPølse(fnr: String, yrkesaktivitetidentifikator: String, vedtaksperiodeId: UUID, generasjonId: UUID, status: Pølsestatus, meldingsreferanseId: UUID, hendelsedata: String)
    fun hent(fnr: String): List<YrkesaktivitetDto>
    fun slett(fnr: String)
}

class Pølsetjenesten(private val dao: PølseDao) : Pølsetjeneste {
    private companion object {
        private val logg = LoggerFactory.getLogger(this::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
    override fun nyPølse(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String) {
        val fabrikk = dao.hent(fnr, yrkesaktivitetidentifikator) ?: Pølsefabrikk()
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
        val fabrikk = dao.hent(fnr, yrkesaktivitetidentifikator) ?: return oppdatererIkkePølse(fnr, vedtaksperiodeId, generasjonId)
        val pølse = fabrikk.oppdaterPølse(vedtaksperiodeId, generasjonId, status)

        val resultat = fabrikk.pakke()
        dao.opprett(fnr, yrkesaktivitetidentifikator, resultat, pølse.kilde, meldingsreferanseId, hendelsedata)
    }

    private fun oppdatererIkkePølse(fnr: String, vedtaksperiodeId: UUID, generasjonId: UUID) {
        logg.info("oppdaterer ikke pølse for {} {} fordi personen er ikke registrert", kv("vedtaksperiodeId", vedtaksperiodeId), kv("generasjonId", generasjonId))
        sikkerlogg.info("oppdaterer ikke pølse for {} {} {} fordi personen er ikke registrert", kv("fødselsnummer", fnr), kv("vedtaksperiodeId", vedtaksperiodeId), kv("generasjonId", generasjonId))
    }

    override fun hent(fnr: String): List<YrkesaktivitetDto> {
        return dao.hent(fnr)
    }

    override fun slett(fnr: String) {
        dao.slett(fnr)
    }
}