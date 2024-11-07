package no.nav.helse.spekemat.slakter

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
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