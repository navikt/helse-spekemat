package no.nav.helse.spekemat

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.*

class E2ETest {
    private companion object {
        const val FNR = "12345678911"
        const val ORGN = "987654321"
    }
    private val dao = PølseDao { Database.dataSource }
    private val testRapid = TestRapid().apply {
        GenerasjonOpprettetRiver(this, Pølsetjenesten(dao))
        SlettPersonRiver(this, dao)
    }
    private val hendelsefabrikk = Hendelsefabrikk(testRapid, FNR)

    @AfterEach
    fun teardown() {
        Database.reset()
    }

    @Test
    fun `ett vedtak`() {
        val hendelseId = UUID.randomUUID()
        val kildeId = UUID.randomUUID()

        hendelsefabrikk.sendGenerasjonOpprettet(meldingsreferanseId = hendelseId, kilde = kildeId, orgnr = ORGN)

        verifiserPersonFinnes(FNR)
        verifiserHendelseFinnes(hendelseId)
        verifiserPølsepakkeFinnes(FNR, ORGN, hendelseId, kildeId)
    }

    @Test
    fun `to vedtak`() {
        val v1 = enVedtaksperiode()
        val v2 = enVedtaksperiode()

        hendelsefabrikk.sendGenerasjonOpprettet(v1, orgnr = ORGN)
        hendelsefabrikk.sendGenerasjonOpprettet(v2, orgnr = ORGN)

        val fabrikk = dao.hent(FNR, ORGN)?.pakke() ?: fail { "Forventet å finne person" }
        assertEquals(1, fabrikk.size)
        assertEquals(2, fabrikk.single().pølser.size)
    }

    @Test
    fun `slette person`() {
        val v1 = enVedtaksperiode()

        hendelsefabrikk.sendGenerasjonOpprettet(v1, orgnr = ORGN)
        assertNotNull(dao.hent(FNR, ORGN))
        hendelsefabrikk.sendSlettPerson()
        assertNull(dao.hent(FNR, ORGN))
    }

    private fun enVedtaksperiode() = UUID.randomUUID()

    private fun verifiserPersonFinnes(fnr: String) {
        sessionOf(Database.dataSource).use {
            assertEquals(true, it.run(queryOf("SELECT EXISTS(SELECT 1 FROM person WHERE fnr = ?)", fnr).map { row -> row.boolean(1) }.asSingle))
        }
    }
    private fun verifiserHendelseFinnes(id: UUID) {
        sessionOf(Database.dataSource).use {
            assertEquals(true, it.run(queryOf("SELECT EXISTS(SELECT 1 FROM hendelse WHERE meldingsreferanse_id = ?)", id).map { row -> row.boolean(1) }.asSingle))
        }
    }
    private fun verifiserPølsepakkeFinnes(fnr: String, yrkesaktivitetidentifikator: String, hendelseId: UUID, kildeId: UUID) {
        sessionOf(Database.dataSource).use {
            assertEquals(true, it.run(queryOf("""
                SELECT EXISTS (
                    SELECT 1 FROM polsepakke
                    WHERE person_id = (SELECT id FROM person WHERE fnr = :fnr)
                    AND yrkesaktivitetidentifikator = :yid
                    AND hendelse_id = (SELECT id FROM hendelse WHERE meldingsreferanse_id = :hendelseId)
                    AND kilde_id = :kildeId
                )
            """.trimIndent(), mapOf(
                "fnr" to fnr,
                "yid" to yrkesaktivitetidentifikator,
                "hendelseId" to hendelseId,
                "kildeId" to kildeId
            )).map { row -> row.boolean(1) }.asSingle))
        }
    }
}

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
