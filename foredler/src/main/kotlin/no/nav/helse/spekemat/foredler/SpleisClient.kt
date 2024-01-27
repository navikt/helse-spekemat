package no.nav.helse.spekemat.foredler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import no.nav.helse.spekemat.foredler.SpleisResponse.SpleisPersonResponse.SpleisArbeidsgiverResponse
import no.nav.helse.spekemat.foredler.SpleisResponse.SpleisPersonResponse.SpleisArbeidsgiverResponse.SpleisGenerasjonerResponse
import no.nav.helse.spekemat.foredler.SpleisResponse.SpleisPersonResponse.SpleisArbeidsgiverResponse.SpleisGenerasjonerResponse.SpleisPeriodeResponse
import no.nav.helse.spekemat.foredler.SpleisResponse.SpleisPersonResponse.SpleisArbeidsgiverResponse.SpleisGenerasjonerResponse.SpleisPeriodeResponse.SpleisPeriodetilstand
import no.nav.helse.spekemat.foredler.SpleisResponse.SpleisPersonResponse.SpleisArbeidsgiverResponse.SpleisGenerasjonerResponse.SpleisPeriodeResponse.SpleisPeriodetilstand.*
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets
import java.util.*

class SpleisClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val tokenProvider: AzureTokenProvider,
    private val scope: String,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
) {
    private companion object {
        private val logg = LoggerFactory.getLogger(this::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
    fun hentSpeilJson(fnr: String): List<YrkesaktivitetDto> {
        val request = lagRequest(fnr)
        val response = httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8))
        val responseBody = response.body()
        sikkerlogg.info("mottok ${response.statusCode()}:\n$responseBody")
        return parsePølsefabrikker(responseBody)
    }

    private fun lagRequest(fnr: String) = HttpRequest.newBuilder(URI("http://spleis-api/api/speil-person/"))
        .header("fnr", fnr)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer ${tokenProvider.bearerToken(scope).token}")
        .GET()
        .build()

    private fun parsePølsefabrikker(body: String): List<YrkesaktivitetDto> {
        val response = objectMapper.readValue<SpleisResponse>(body)
        return response.data.person.arbeidsgivere.map { arbeidsgiver ->
            parseYrkesaktivitet(arbeidsgiver)
        }
    }

    private fun parseYrkesaktivitet(arbeidsgiver: SpleisArbeidsgiverResponse): YrkesaktivitetDto {
        return YrkesaktivitetDto(
            yrkesaktivitetidentifikator = arbeidsgiver.organisasjonsnummer,
            rader = arbeidsgiver.generasjoner.map { parsePølserad(it) }
        )
    }

    private fun parsePølserad(pølserad: SpleisGenerasjonerResponse): PølseradDto {
        return PølseradDto(
            kildeTilRad = pølserad.kildeTilGenerasjon,
            pølser = pølserad.perioder.map { mapPølse(it) }
        )
    }

    private fun mapPølse(pølse: SpleisPeriodeResponse): PølseDto {
        return PølseDto(
            vedtaksperiodeId = pølse.vedtaksperiodeId,
            generasjonId = pølse.generasjonId,
            status = parseStatus(pølse.periodetilstand),
            kilde = pølse.kilde
        )
    }

    private fun parseStatus(periodetilstand: SpleisPeriodetilstand): Pølsestatus {
        return when (periodetilstand) {
            TilUtbetaling,
            TilAnnullering,
            Utbetalt,
            UtbetalingFeilet,
            IngenUtbetaling -> Pølsestatus.LUKKET

            Annullert,
            AnnulleringFeilet,
            TilInfotrygd -> Pølsestatus.FORKASTET

            RevurderingFeilet,
            ForberederGodkjenning,
            ManglerInformasjon,
            VenterPaAnnenPeriode,
            UtbetaltVenterPaAnnenPeriode,
            TilSkjonnsfastsettelse,
            TilGodkjenning -> Pølsestatus.ÅPEN
        }
    }
}


data class SpleisResponse(
    val data: SpleisDataResponse
) {
    data class SpleisDataResponse(
        val person: SpleisPersonResponse
    )
    data class SpleisPersonResponse(
        val arbeidsgivere: List<SpleisArbeidsgiverResponse>
    ) {
        data class SpleisArbeidsgiverResponse(
            val organisasjonsnummer: String,
            val generasjoner: List<SpleisGenerasjonerResponse>
        ) {
            data class SpleisGenerasjonerResponse(
                val kildeTilGenerasjon: UUID,
                val perioder: List<SpleisPeriodeResponse>
            ) {
                data class SpleisPeriodeResponse(
                    val vedtaksperiodeId: UUID,
                    val generasjonId: UUID,
                    val kilde: UUID,
                    val periodetilstand: SpleisPeriodetilstand
                ) {
                    enum class SpleisPeriodetilstand {
                        TilUtbetaling,
                        TilAnnullering,
                        Utbetalt,
                        Annullert,
                        AnnulleringFeilet,
                        IngenUtbetaling,
                        RevurderingFeilet,
                        TilInfotrygd,
                        UtbetalingFeilet,
                        ForberederGodkjenning,
                        ManglerInformasjon,
                        VenterPaAnnenPeriode,
                        UtbetaltVenterPaAnnenPeriode,
                        TilSkjonnsfastsettelse,
                        TilGodkjenning
                    }
                }
            }
        }
    }
}