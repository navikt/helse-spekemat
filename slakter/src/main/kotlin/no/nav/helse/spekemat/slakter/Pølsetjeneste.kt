package no.nav.helse.spekemat.slakter

import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import org.intellij.lang.annotations.Language
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
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
    override fun slett(fnr: String) {
        val request = lagSlettRequest(fnr)
        val response = httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8))
        check(response.statusCode() == 200) {
            "Forventet HTTP 200. Response:\n${response.body()}"
        }
    }

    override fun håndter(fnr: String, yrkesaktivitetidentifikator: String, pølse: PølseDto, meldingsreferanseId: UUID, hendelsedata: String) {
        val request = lagPølseRequest(fnr, yrkesaktivitetidentifikator, pølse, meldingsreferanseId, hendelsedata)
        val response = httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8))
        check(response.statusCode() == 200) {
            "Forventet HTTP 200. Response:\n${response.body()}"
        }
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
        return HttpRequest.newBuilder(URI("http://spekemat/api/pølse"))
            .header("Authorization", "Bearer ${azure.bearerToken(scope).token}")
            .POST(BodyPublishers.ofString(requestBody))
            .build()
    }

    private fun lagSlettRequest(fnr: String): HttpRequest {
        @Language("JSON")
        val requestBody = """{
            | "fnr": "$fnr"
            |}""".trimMargin()
        return HttpRequest.newBuilder(URI("http://spekemat/api/slett"))
            .header("Authorization", "Bearer ${azure.bearerToken(scope).token}")
            .POST(BodyPublishers.ofString(requestBody))
            .build()
    }
}

data class PølseDto(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    val kilde: UUID
)