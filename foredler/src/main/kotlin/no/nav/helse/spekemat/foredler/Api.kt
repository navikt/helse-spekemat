package no.nav.helse.spekemat.foredler

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spekemat.fabrikk.*
import java.util.UUID

fun Route.api(
    pølsetjeneste: Pølsetjeneste,
    erUtvikling: Boolean,
) {
    route("/api/person") {
        if (erUtvikling) {
            delete {
                val request = call.receiveNullable<SlettRequest>() ?: throw BadRequestException("Ugyldig request")
                pølsetjeneste.slett(request.fnr)
                call.respondText(ContentType.Application.Json, HttpStatusCode.OK) { """{ "melding": "takk for ditt bidrag" }""" }
            }
        }
    }

    route("/api/pølse") {
        post {
            val request = call.receiveNullable<NyPølseRequest>() ?: throw BadRequestException("Ugyldig request")
            val pølse =
                PølseDto(
                    vedtaksperiodeId = request.pølse.vedtaksperiodeId,
                    behandlingId = request.pølse.behandlingId,
                    status = Pølsestatus.ÅPEN,
                    kilde = request.pølse.kilde,
                )
            val callId = call.callId ?: throw BadRequestException("Mangler callId-header")
            pølsetjeneste.nyPølse(
                request.fnr,
                request.yrkesaktivitetidentifikator,
                pølse,
                request.meldingsreferanseId,
                request.hendelsedata,
                callId,
            )
            call.respondText(ContentType.Application.Json, HttpStatusCode.OK) { """{ "melding": "takk for ditt bidrag" }""" }
        }

        patch {
            val request = call.receiveNullable<OppdaterPølseRequest>() ?: throw BadRequestException("Ugyldig request")
            val status =
                when (request.status) {
                    PølsestatusDto.ÅPEN -> Pølsestatus.ÅPEN
                    PølsestatusDto.LUKKET -> Pølsestatus.LUKKET
                    PølsestatusDto.FORKASTET -> Pølsestatus.FORKASTET
                }
            val callId = call.callId ?: throw BadRequestException("Mangler callId-header")
            try {
                pølsetjeneste.oppdaterPølse(
                    request.fnr,
                    request.yrkesaktivitetidentifikator,
                    request.vedtaksperiodeId,
                    request.behandlingId,
                    status,
                    request.meldingsreferanseId,
                    request.hendelsedata,
                    callId,
                )
                call.respondText(ContentType.Application.Json, HttpStatusCode.OK) { """{ "melding": "takk for ditt bidrag" }""" }
            } catch (_: OppdatererEldreBehandlingException) {
                call.respondText(ContentType.Application.Json, HttpStatusCode.OK) { """{ "melding": "takk for ditt bidrag, men jeg tror du er litt out-of-order? Endringen er allerede hensyntatt 😚" }""" }
            } catch (err: TomPølsepakkeException) {
                throw NotFoundException("Ingen registrert pølsepakke for vedkommende: ${err.message}")
            } catch (err: PølseFinnesIkkeException) {
                throw NotFoundException("Pølse finnes ikke: ${err.message}")
            }
        }
    }

    post("/api/pølser") {
        val request = call.receiveNullable<PølserRequest>() ?: throw BadRequestException("Ugyldig request")
        call.respond(HttpStatusCode.OK, PølserResponse(pølsetjeneste.hent(request.fnr)))
    }
}

data class SlettRequest(
    val fnr: String,
)

data class PølserRequest(
    val fnr: String,
)

data class PølserResponse(
    val yrkesaktiviteter: List<YrkesaktivitetDto>,
)

data class NyPølseRequest(
    val fnr: String,
    val yrkesaktivitetidentifikator: String,
    val pølse: NyPølseDto,
    val meldingsreferanseId: UUID,
    val hendelsedata: String,
)

data class NyPølseDto(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    // tingen som gjorde at behandlingen ble opprettet
    val kilde: UUID,
)

enum class PølsestatusDto { ÅPEN, LUKKET, FORKASTET }

data class OppdaterPølseRequest(
    val fnr: String,
    val yrkesaktivitetidentifikator: String,
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val status: PølsestatusDto,
    val meldingsreferanseId: UUID,
    val hendelsedata: String,
)

data class YrkesaktivitetDto(
    val yrkesaktivitetidentifikator: String,
    val rader: List<PølseradDto>,
)
