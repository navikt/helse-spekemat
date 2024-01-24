package no.nav.helse.spekemat.slakter

import com.github.navikt.tbd_libs.azure.AzureTokenProvider
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

interface Pølsetjeneste {
    fun håndter(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String)
    fun slett(fnr: String)
}

class Pølsetjenesten(
    private val httpClient: HttpClient,
    private val azure: AzureTokenProvider,
    private val scope: String
) : Pølsetjeneste {
    private companion object {
        private val logg = LoggerFactory.getLogger(this::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
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
        sikkerlogg.info("mottok ${response.body()}")
    }

    private fun sjekkOKResponse(response: HttpResponse<String>) {
        if (response.statusCode() == 200) return
        sikkerlogg.error("Forventet HTTP 200. Fikk {}\nResponse:\n{}", response.statusCode(), response.body())
        throw RuntimeException("Forventet HTTP 200. Fikk ${response.statusCode()}")
    }

    private fun lagPølseRequest(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String): HttpRequest {
        @Language("JSON")
        val requestBody = """{
            | "fnr": "$fnr",
            | "yrkesaktivitetidentifikator": "$yrkesaktivitetidentifikator",
            | "meldingsreferanseId": "$meldingsreferanseId",
            | "pølse": {
            |   "vedtaksperiodeId": "${pølse.vedtaksperiodeId}",
            |   "generasjonId": "${pølse.generasjonId}",
            |   "kilde": "${pølse.kilde}"
            | },
            | "hendelsedata": "$hendelsedata"
            |}""".trimMargin()
        return lagPOSTRequest(URI("http://spekemat/api/pølse"), requestBody)
    }

    private fun lagSlettRequest(fnr: String): HttpRequest {
        @Language("JSON")
        val requestBody = """{
            | "fnr": "$fnr"
            |}""".trimMargin()
        return lagPOSTRequest(URI("http://spekemat/api/slett"), requestBody)
    }

    private fun lagPOSTRequest(uri: URI, body: String): HttpRequest {
        sikkerlogg.info("sender POST til $uri med body:\n$body")
        return HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer ${azure.bearerToken(scope).token}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build()
    }
}

data class PølseDto(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    val kilde: UUID
)