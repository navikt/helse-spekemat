package no.nav.helse.spekemat.slakter

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.intellij.lang.annotations.Language
import java.util.*

internal class Hendelsefabrikk(
    private val rapidsConnection: TestRapid,
    private val fnr: String
) {
    fun sendBehandlingOpprettetArbeidstaker(vedtaksperiodeId: UUID = UUID.randomUUID(), kilde: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID(), behandlingId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagBehandlingOpprettetArbeidstaker(meldingsreferanseId, vedtaksperiodeId, kilde, orgnr, behandlingId))
    }

    fun sendBehandlingOpprettetSelvstendig(vedtaksperiodeId: UUID = UUID.randomUUID(), kilde: UUID = UUID.randomUUID(), meldingsreferanseId: UUID = UUID.randomUUID(), behandlingId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagBehandlingOpprettetSelvstendig(meldingsreferanseId, vedtaksperiodeId, kilde, behandlingId))
    }

    fun sendBehandlingLukketArbeidstaker(vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID(), behandlingId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagBehandlingLukketArbeidstaker(meldingsreferanseId, vedtaksperiodeId, orgnr, behandlingId))
    }
    fun sendBehandlingForkastetArbeidstaker(vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, meldingsreferanseId: UUID = UUID.randomUUID(), behandlingId: UUID = UUID.randomUUID()) {
        rapidsConnection.sendTestMessage(lagBehandlingForkastetArbeidstaker(meldingsreferanseId, vedtaksperiodeId, orgnr, behandlingId))
    }
    @Language("JSON")
    fun lagBehandlingOpprettetArbeidstaker(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), kilde: UUID, orgnr: String, behandlingId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "behandling_opprettet",
        |  "@id": "$meldingsreferanseId",
        |  "kilde": {
        |    "meldingsreferanseId": "$kilde"
        |  },
        |  "fødselsnummer": "$fnr",
        |  "yrkesaktivitetstype": "ARBEIDSTAKER",
        |  "organisasjonsnummer": "$orgnr",
        |  "vedtaksperiodeId": "$vedtaksperiodeId",
        |  "behandlingId": "$behandlingId"
        |}""".trimMargin()
    @Language("JSON")
    fun lagBehandlingLukketArbeidstaker(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, behandlingId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "behandling_lukket",
        |  "@id": "$meldingsreferanseId",
        |  "fødselsnummer": "$fnr",
        |  "yrkesaktivitetstype": "ARBEIDSTAKER",
        |  "organisasjonsnummer": "$orgnr",
        |  "vedtaksperiodeId": "$vedtaksperiodeId",
        |  "behandlingId": "$behandlingId"
        |}""".trimMargin()
    @Language("JSON")
    fun lagBehandlingForkastetArbeidstaker(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), orgnr: String, behandlingId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "behandling_forkastet",
        |  "@id": "$meldingsreferanseId",
        |  "fødselsnummer": "$fnr",
        |  "yrkesaktivitetstype": "ARBEIDSTAKER",
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

    @Language("JSON")
    fun lagBehandlingOpprettetSelvstendig(meldingsreferanseId: UUID, vedtaksperiodeId: UUID = UUID.randomUUID(), kilde: UUID, behandlingId: UUID = UUID.randomUUID()) = """{
        |  "@event_name": "behandling_opprettet",
        |  "@id": "$meldingsreferanseId",
        |  "kilde": {
        |    "meldingsreferanseId": "$kilde"
        |  },
        |  "fødselsnummer": "$fnr",
        |  "yrkesaktivitetstype": "SELVSTENDIG",
        |  "vedtaksperiodeId": "$vedtaksperiodeId",
        |  "behandlingId": "$behandlingId"
        |}""".trimMargin()
}
