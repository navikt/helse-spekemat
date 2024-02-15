package no.nav.helse.spekemat.foredler

import net.logstash.logback.argument.StructuredArguments.kv
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
        generasjonId: UUID,
        status: Pølsestatus,
        meldingsreferanseId: UUID,
        hendelsedata: String,
        callId: String
    )
    fun opprettManglendePerson(fnr: String, callId: String)
    fun hent(fnr: String): List<YrkesaktivitetDto>
    fun slett(fnr: String)
}

class Pølsetjenesten(
    private val dao: PølseDao,
    private val spleisClient: SpleisClient,
    private val migrerVedOpprettelse: Boolean = false
) : Pølsetjeneste {
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
        hentPølsefabrikk(fnr, yrkesaktivitetidentifikator, meldingsreferanseId, hendelsedata, callId) { fabrikk ->
            fabrikk.nyPølse(Pølse.fraDto(pølse))
            pølse
        }
    }

    override fun oppdaterPølse(
        fnr: String,
        yrkesaktivitetidentifikator: String,
        vedtaksperiodeId: UUID,
        generasjonId: UUID,
        status: Pølsestatus,
        meldingsreferanseId: UUID,
        hendelsedata: String,
        callId: String
    ) {
        hentPølsefabrikk(fnr, yrkesaktivitetidentifikator, meldingsreferanseId, hendelsedata, callId) { fabrikk ->
            fabrikk.oppdaterPølse(vedtaksperiodeId, generasjonId, status)
        }
    }

    override fun opprettManglendePerson(fnr: String, callId: String) {
        val oppretting = { spleisClient.hentSpeilJson(fnr, callId).also { result ->
            "Opprettet ${result.size} pølsepakker for ${fnr.maskertFnr}".also {
                logg.info(it, kv("callId", callId))
                sikkerlogg.info(it, kv("fnr", fnr), kv("callId", callId))
            }
        } }
        dao.opprettPerson(fnr, oppretting)
    }

    private fun hentPølsefabrikk(fnr: String, yrkesaktivitetidentifikator: String, meldingsreferanseId: UUID, hendelsedata: String, callId: String, behandling: (Pølsefabrikk) -> PølseDto) {
        val oppretting = { if (migrerVedOpprettelse) spleisClient.hentSpeilJson(fnr, callId) else emptyList() }
        dao.behandle(fnr, yrkesaktivitetidentifikator, meldingsreferanseId, hendelsedata, oppretting, behandling)
    }

    override fun hent(fnr: String): List<YrkesaktivitetDto> {
        return dao.hent(fnr)
    }

    override fun slett(fnr: String) {
        dao.slett(fnr)
    }
}