package no.nav.helse.spekemat.slakter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.LocalDateTime
import java.util.*

class E2ETest {
    private companion object {
        const val FNR = "12345678911"
        const val ORGN = "987654321"
        private val objectMapper = jacksonObjectMapper()
    }

    private val azureTokenProvider = object : AzureTokenProvider {
        override fun bearerToken(scope: String) = AzureToken("liksom-token", LocalDateTime.MAX)
        override fun onBehalfOfToken(scope: String, token: String): AzureToken {
            throw NotImplementedError("ikke implementert i mock")
        }
    }
    private var httpClientMock = mockk<HttpClient>()
    private val pølsetjeneste = Pølsetjenesten(httpClientMock, azureTokenProvider, "scope-til-spekemat")
    private val testRapid = TestRapid().apply {
        GenerasjonOpprettetRiver(this, pølsetjeneste)
        GenerasjonLukketRiver(this, pølsetjeneste)
        GenerasjonForkastetRiver(this, pølsetjeneste)
        SlettPersonRiver(this, pølsetjeneste)
    }
    private val hendelsefabrikk = Hendelsefabrikk(testRapid, FNR)

    @AfterEach
    fun teardown() {
        clearMocks(httpClientMock)
    }

    @Test
    fun `generasjon opprettet`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val kilde = UUID.randomUUID()
        val meldingsreferanseId = UUID.randomUUID()

        mockResponse("OK", 200, mapOf("callId" to UUID.randomUUID().toString()))
        hendelsefabrikk.sendGenerasjonOpprettet(vedtaksperiodeId, kilde, ORGN, meldingsreferanseId)

        verifiserRequest(httpClientMock) { request ->
            val node = objectMapper.readTree(request.bodyAsString())
            val hendelseData = objectMapper.readTree(node.path("hendelsedata").asText())

            request.method() == "POST"
                    && node.path("fnr").asText() == FNR
                    && node.path("yrkesaktivitetidentifikator").asText() == ORGN
                    && node.path("meldingsreferanseId").asText() == meldingsreferanseId.toString()
                    && node.path("pølse").path("vedtaksperiodeId").asText() == vedtaksperiodeId.toString()
                    && node.path("pølse").path("kilde").asText() == kilde.toString()
                    && node.path("pølse").hasNonNull("generasjonId")
                    && hendelseData.path("@event_name").asText() == "generasjon_opprettet"
        }
    }

    @Test
    fun `generasjon lukket`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val kilde = UUID.randomUUID()
        val meldingsreferanseId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()

        mockResponse("OK", 200, mapOf("callId" to UUID.randomUUID().toString()))

        hendelsefabrikk.sendGenerasjonOpprettet(vedtaksperiodeId, kilde, ORGN, generasjonId = generasjonId)
        hendelsefabrikk.sendGenerasjonLukket(vedtaksperiodeId, ORGN, meldingsreferanseId, generasjonId)

        verifiserRequest(httpClientMock) { request ->
            val node = objectMapper.readTree(request.bodyAsString())
            val hendelseData = objectMapper.readTree(node.path("hendelsedata").asText())

            request.method() == "PATCH"
                    && node.path("fnr").asText() == FNR
                    && node.path("yrkesaktivitetidentifikator").asText() == ORGN
                    && node.path("meldingsreferanseId").asText() == meldingsreferanseId.toString()
                    && node.path("vedtaksperiodeId").asText() == vedtaksperiodeId.toString()
                    && node.path("generasjonId").asText() == generasjonId.toString()
                    && node.path("status").asText() == "LUKKET"
                    && hendelseData.path("@event_name").asText() == "generasjon_lukket"
        }
    }

    @Test
    fun `generasjon forkastet`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val kilde = UUID.randomUUID()
        val meldingsreferanseId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()

        mockResponse("OK", 200, mapOf("callId" to UUID.randomUUID().toString()))

        hendelsefabrikk.sendGenerasjonOpprettet(vedtaksperiodeId, kilde, ORGN, generasjonId = generasjonId)
        hendelsefabrikk.sendGenerasjonForkastet(vedtaksperiodeId, ORGN, meldingsreferanseId, generasjonId)

        verifiserRequest(httpClientMock) { request ->
            val node = objectMapper.readTree(request.bodyAsString())
            val hendelseData = objectMapper.readTree(node.path("hendelsedata").asText())

            request.method() == "PATCH"
                    && node.path("fnr").asText() == FNR
                    && node.path("yrkesaktivitetidentifikator").asText() == ORGN
                    && node.path("meldingsreferanseId").asText() == meldingsreferanseId.toString()
                    && node.path("vedtaksperiodeId").asText() == vedtaksperiodeId.toString()
                    && node.path("generasjonId").asText() == generasjonId.toString()
                    && node.path("status").asText() == "FORKASTET"
                    && hendelseData.path("@event_name").asText() == "generasjon_forkastet"
        }
    }

    @Test
    fun `slette person`() {
        mockResponse("OK", 200, mapOf("callId" to UUID.randomUUID().toString()))
        hendelsefabrikk.sendSlettPerson()
        verifiserRequest(httpClientMock) { request ->
            val node = objectMapper.readTree(request.bodyAsString())
            node.path("fnr").asText() == FNR
        }
    }

    private fun mockResponse(response: String, statusCode: Int? = null, headers: Map<String, String>? = null) {
        every {
            httpClientMock.send<String>(any(), any())
        } returns MockHttpResponse(response, statusCode, headers)
    }

    private fun verifiserRequest(httpClient: HttpClient, sjekk: (HttpRequest) -> Boolean) {
        verify {
            httpClient.send<String>(match {
                sjekk(it)
            }, any())
        }
    }
}

private class Hendelsefabrikk(
    private val rapidsConnection: TestRapid,
    private val fnr: String
) {
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
