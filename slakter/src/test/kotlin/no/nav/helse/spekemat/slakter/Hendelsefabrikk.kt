package no.nav.helse.spekemat.slakter

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import java.util.*

internal class Hendelsefabrikk(
    private val rapidsConnection: TestRapid,
    private val fnr: String
) {
    fun sendBehandlingOpprettet(vedtaksperiodeId: UUID = UUID.randomUUID(), kilde: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID(), behandlingId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagBehandlingOpprettet(meldingsreferanseId, vedtaksperiodeId, kilde, orgnr, behandlingId))
    }
    fun sendBehandlingLukket(vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID(), behandlingId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagBehandlingLukket(meldingsreferanseId, vedtaksperiodeId, orgnr, behandlingId))
    }
    fun sendBehandlingForkastet(vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID(), behandlingId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagBehandlingForkastet(meldingsreferanseId, vedtaksperiodeId, orgnr, behandlingId))
    }
    fun sendGenerasjonOpprettet(vedtaksperiodeId: UUID = UUID.randomUUID(), kilde: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID(), generasjonId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagGenerasjonOpprettet(meldingsreferanseId, vedtaksperiodeId, kilde, orgnr, generasjonId))
    }
    fun sendGenerasjonLukket(vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID(), generasjonId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagGenerasjonLukket(meldingsreferanseId, vedtaksperiodeId, orgnr, generasjonId))
    }
    fun sendGenerasjonForkastet(vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID(), generasjonId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagGenerasjonForkastet(meldingsreferanseId, vedtaksperiodeId, orgnr, generasjonId))
    }
    @Language("JSON")
    fun lagBehandlingOpprettet(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), kilde: UUID, orgnr: String, behandlingId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "behandling_opprettet",
        |  "@id": "$meldingsreferanseId",
        |  "kilde": {
        |    "meldingsreferanseId": "$kilde"
        |  },
        |  "fødselsnummer": "$fnr",
        |  "organisasjonsnummer": "$orgnr",
        |  "vedtaksperiodeId": "$vedtaksperiodeId",
        |  "behandlingId": "$behandlingId"
        |}""".trimMargin()
    @Language("JSON")
    fun lagBehandlingLukket(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, behandlingId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "behandling_lukket",
        |  "@id": "$meldingsreferanseId",
        |  "fødselsnummer": "$fnr",
        |  "organisasjonsnummer": "$orgnr",
        |  "vedtaksperiodeId": "$vedtaksperiodeId",
        |  "behandlingId": "$behandlingId"
        |}""".trimMargin()
    @Language("JSON")
    fun lagBehandlingForkastet(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, behandlingId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "behandling_forkastet",
        |  "@id": "$meldingsreferanseId",
        |  "fødselsnummer": "$fnr",
        |  "organisasjonsnummer": "$orgnr",
        |  "vedtaksperiodeId": "$vedtaksperiodeId",
        |  "behandlingId": "$behandlingId"
        |}""".trimMargin()

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
    @Language("JSON")
    fun lagGenerasjonLukket(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, generasjonId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "generasjon_lukket",
        |  "@id": "$meldingsreferanseId",
        |  "fødselsnummer": "$fnr",
        |  "organisasjonsnummer": "$orgnr",
        |  "vedtaksperiodeId": "$vedtaksperiodeId",
        |  "generasjonId": "$generasjonId"
        |}""".trimMargin()
    @Language("JSON")
    fun lagGenerasjonForkastet(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, generasjonId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "generasjon_forkastet",
        |  "@id": "$meldingsreferanseId",
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