package no.nav.helse.spekemat.slakter

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class E2ETest {
    private companion object {
        const val FNR = "12345678911"
        const val ORGN = "987654321"
    }
    private val pølsetjeneste = object : Pølsetjeneste {
        var slettetFnr: String? = null
        var pølsedata: PølseTestData? = null

        override fun håndter(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String) {
            pølsedata = PølseTestData(fnr, yrkesaktivitetidentifikator, pølse, meldingsreferanseId, hendelsedata)
        }
        override fun slett(fnr: String) {
            slettetFnr = fnr
        }

        fun reset() {
            pølsedata = null
            slettetFnr = null
        }
    }
    private val testRapid = TestRapid().apply {
        GenerasjonOpprettetRiver(this, pølsetjeneste)
        SlettPersonRiver(this, pølsetjeneste)
    }
    private val hendelsefabrikk = Hendelsefabrikk(testRapid, FNR)

    @AfterEach
    fun teardown() {
        pølsetjeneste.reset()
    }

    @Test
    fun `generasjon opprettet`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val kilde = UUID.randomUUID()
        val meldingsreferanseId = UUID.randomUUID()
        hendelsefabrikk.sendGenerasjonOpprettet(vedtaksperiodeId, kilde, ORGN, meldingsreferanseId)
        assertEquals(FNR, pølsetjeneste.pølsedata?.fnr)
        assertEquals(ORGN, pølsetjeneste.pølsedata?.yrkesaktivitetidentifikator)
        assertEquals(meldingsreferanseId, pølsetjeneste.pølsedata?.meldingsreferanseId)
        assertEquals(vedtaksperiodeId, pølsetjeneste.pølsedata?.pølse?.vedtaksperiodeId)
        assertEquals(kilde, pølsetjeneste.pølsedata?.pølse?.kilde)
    }

    @Test
    fun `slette person`() {
        hendelsefabrikk.sendSlettPerson()
        assertEquals(FNR, pølsetjeneste.slettetFnr)
    }
}

private data class PølseTestData(
    val fnr: String,
    val yrkesaktivitetidentifikator: String,
    val pølse: PølseDto,
    val meldingsreferanseId: UUID,
    val hendelsedata: String
)

private class Hendelsefabrikk(
    private val rapidsConnection: TestRapid,
    private val fnr: String
) {
    fun sendGenerasjonOpprettet(vedtaksperiodeId: UUID = UUID.randomUUID(), kilde: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagGenerasjonOpprettet(meldingsreferanseId, vedtaksperiodeId, kilde, orgnr))
    }
    @Language("JSON")
    fun lagGenerasjonOpprettet(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), kilde: UUID, orgnr: String, generasjonId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "generasjon_opprettet",
        |  "@id": "$meldingsreferanseId",
        |  "kilde": {
        |    "meldingsreferanseId": "$kilde"
        |  },
        |  "fødselsnummer": "$fnr",
        |  "organisasjonsnummer": "$orgnr",
        |  "vedtaksperiodeId": "$vedtaksperiodeId",
        |  "generasjonId": "$generasjonId"
        |}""".trimMargin()
    fun sendSlettPerson() {
        rapidsConnection.sendTestMessage(lagSlettPerson())
    }
    @Language("JSON")
    fun lagSlettPerson() = """{
        |  "@event_name": "slett_person",
        |  "@id": "${UUID.randomUUID()}",
        |  "fødselsnummer": "$fnr"
        |}""".trimMargin()
}
