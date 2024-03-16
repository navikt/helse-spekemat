package no.nav.helse.spekemat.foredler

import no.nav.helse.spekemat.fabrikk.Pølse
import no.nav.helse.spekemat.fabrikk.PølseDto
import no.nav.helse.spekemat.fabrikk.Pølsefabrikk
import no.nav.helse.spekemat.fabrikk.Pølsestatus
import org.slf4j.LoggerFactory
import java.util.*

interface Pølsetjeneste {
    fun nyPølse(
        fnr: String,
        yrkesaktivitetidentifikator: String,
        pølse: PølseDto,
        meldingsreferanseId: UUID,
        hendelsedata: String,
        callId: String
    )
    fun oppdaterPølse(
        fnr: String,
        yrkesaktivitetidentifikator: String,
        vedtaksperiodeId: UUID,
        behandlingId: UUID,
        status: Pølsestatus,
        meldingsreferanseId: UUID,
        hendelsedata: String,
        callId: String
    )
    fun hent(fnr: String): List<YrkesaktivitetDto>
    fun slett(fnr: String)
}

class Pølsetjenesten(private val dao: PølseDao) : Pølsetjeneste {
    private companion object {
        private val logg = LoggerFactory.getLogger(this::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val String.maskertFnr get() = take(6).padEnd(11, '*')
    }
    override fun nyPølse(
        fnr: String,
        yrkesaktivitetidentifikator: String,
        pølse: PølseDto,
        meldingsreferanseId: UUID,
        hendelsedata: String,
        callId: String
    ) {
        hentPølsefabrikk(fnr, yrkesaktivitetidentifikator, meldingsreferanseId, hendelsedata) { fabrikk ->
            fabrikk.nyPølse(Pølse.fraDto(pølse))
            pølse
        }
    }

    override fun oppdaterPølse(
        fnr: String,
        yrkesaktivitetidentifikator: String,
        vedtaksperiodeId: UUID,
        behandlingId: UUID,
        status: Pølsestatus,
        meldingsreferanseId: UUID,
        hendelsedata: String,
        callId: String
    ) {
        hentPølsefabrikk(fnr, yrkesaktivitetidentifikator, meldingsreferanseId, hendelsedata) { fabrikk ->
            fabrikk.oppdaterPølse(vedtaksperiodeId, behandlingId, status)
        }
    }

    private fun hentPølsefabrikk(fnr: String, yrkesaktivitetidentifikator: String, meldingsreferanseId: UUID, hendelsedata: String, behandling: (Pølsefabrikk) -> PølseDto) {
        dao.behandle(fnr, yrkesaktivitetidentifikator, meldingsreferanseId, hendelsedata, behandling)
    }

    override fun hent(fnr: String): List<YrkesaktivitetDto> {
        return dao.hent(fnr)
    }

    override fun slett(fnr: String) {
        dao.slett(fnr)
    }
}