package no.nav.helse.spekemat.slakter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import net.logstash.logback.argument.StructuredArguments.kv
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.jvm.optionals.getOrNull

interface Pølsetjeneste {
    fun håndter(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String)
    fun slett(fnr: String)
}

class Pølsetjenesten(
    private val httpClient: HttpClient,
    private val azure: AzureTokenProvider,
    private val scope: String,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : Pølsetjeneste {
    private companion object {
        private val logg = LoggerFactory.getLogger(this::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private const val CALL_ID_HEADER = "callId"
    }
    override fun slett(fnr: String) {
        val request = lagSlettRequest(fnr)
        sjekkOKResponse(request)
    }

    override fun håndter(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String) {
        val request = lagPølseRequest(fnr, yrkesaktivitetidentifikator, pølse, meldingsreferanseId, hendelsedata)
        sjekkOKResponse(request)
    }

    private fun sjekkOKResponse(request: HttpRequest) {
        val response = httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8))
        sjekkOKResponse(response)
        val callId = response.headers().firstValue(CALL_ID_HEADER).getOrNull() ?: "N/A"
        sikkerlogg.info("mottok {}:\n${response.body()}", kv("callId", callId))
    }

    private fun sjekkOKResponse(response: HttpResponse<String>) {
        if (response.statusCode() == 200) return
        sikkerlogg.error("Forventet HTTP 200. Fikk {}\nResponse:\n{}", response.statusCode(), response.body())
        throw RuntimeException("Forventet HTTP 200. Fikk ${response.statusCode()}")
    }

    private fun lagPølseRequest(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String): HttpRequest {
        val requestBody = objectMapper.writeValueAsString(PølseRequest(fnr, yrkesaktivitetidentifikator, meldingsreferanseId, pølse, hendelsedata))
        return lagPOSTRequest(URI("http://spekemat/api/pølse"), requestBody, callId = meldingsreferanseId)
    }

    private fun lagSlettRequest(fnr: String): HttpRequest {
        @Language("JSON")
        val requestBody = """{
            | "fnr": "$fnr"
            |}""".trimMargin()
        return lagPOSTRequest(URI("http://spekemat/api/slett"), requestBody)
    }

    private fun lagPOSTRequest(uri: URI, body: String, callId: UUID = UUID.randomUUID()): HttpRequest {
        sikkerlogg.info("sender POST til <$uri> med {} og body:\n$body", kv("callId", callId))
        return HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer ${azure.bearerToken(scope).token}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header(CALL_ID_HEADER, "$callId")
            .POST(BodyPublishers.ofString(body))
            .build()
    }

    private data class PølseRequest(
        val fnr: String,
        val yrkesaktivitetidentifikator: String,
        val meldingsreferanseId: UUID,
        val pølse: PølseDto,
        val hendelsedata: String
    )
}

data class PølseDto(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    val kilde: UUID
)