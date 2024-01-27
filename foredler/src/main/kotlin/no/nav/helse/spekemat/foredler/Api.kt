package no.nav.helse.spekemat.foredler

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.api(pølsetjeneste: Pølsetjeneste) {

    post("/api/slett") {
        val request = call.receiveNullable<SlettRequest>() ?: return@post call.respond(HttpStatusCode.BadRequest, FeilResponse(
            feilmelding = "Ugyldig request"
        ))
        pølsetjeneste.slett(request.fnr)
        call.respondText(ContentType.Application.Json, HttpStatusCode.OK) { """{ "melding": "takk for ditt bidrag" }""" }
    }

    post("/api/pølse") {
        val request = call.receiveNullable<NyPølseRequest>() ?: return@post call.respond(HttpStatusCode.BadRequest, FeilResponse(
            feilmelding = "Ugyldig request"
        ))
        val pølse = PølseDto(
            vedtaksperiodeId = request.pølse.vedtaksperiodeId,
            generasjonId = request.pølse.generasjonId,
            status = Pølsestatus.ÅPEN,
            kilde = request.pølse.kilde
        )
        pølsetjeneste.nyPølse(request.fnr, request.yrkesaktivitetidentifikator, pølse, request.meldingsreferanseId, request.hendelsedata)
        call.respondText(ContentType.Application.Json, HttpStatusCode.OK) { """{ "melding": "takk for ditt bidrag" }""" }
    }

    patch("/api/pølse") {
        val request = call.receiveNullable<OppdaterPølseRequest>() ?: return@patch call.respond(HttpStatusCode.BadRequest, FeilResponse(
            feilmelding = "Ugyldig request"
        ))
        val status = when (request.status) {
            PølsestatusDto.ÅPEN -> Pølsestatus.ÅPEN
            PølsestatusDto.LUKKET -> Pølsestatus.LUKKET
            PølsestatusDto.FORKASTET -> Pølsestatus.FORKASTET
        }
        pølsetjeneste.oppdaterPølse(request.fnr, request.yrkesaktivitetidentifikator, request.vedtaksperiodeId, request.generasjonId, status, request.meldingsreferanseId, request.hendelsedata)
        call.respondText(ContentType.Application.Json, HttpStatusCode.OK) { """{ "melding": "takk for ditt bidrag" }""" }
    }

    post("/api/pølser") {
        val request = call.receiveNullable<PølserRequest>() ?: return@post call.respond(HttpStatusCode.BadRequest, FeilResponse(
            feilmelding = "Ugyldig request"
        ))
        call.respond(HttpStatusCode.OK, PølserResponse(pølsetjeneste.hent(request.fnr)))
    }
}

data class SlettRequest(val fnr: String)
data class PølserRequest(val fnr: String)
data class PølserResponse(val yrkesaktiviteter: List<YrkesaktivitetDto>)
data class NyPølseRequest(
    val fnr: String,
    val yrkesaktivitetidentifikator: String,
    val pølse: NyPølseDto,
    val meldingsreferanseId: UUID,
    val hendelsedata: String
)
data class NyPølseDto(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    // tingen som gjorde at generasjonen ble opprettet
    val kilde: UUID
)
enum class PølsestatusDto { ÅPEN, LUKKET, FORKASTET }
data class OppdaterPølseRequest(
    val fnr: String,
    val yrkesaktivitetidentifikator: String,
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    val status: PølsestatusDto,
    val meldingsreferanseId: UUID,
    val hendelsedata: String
)

data class YrkesaktivitetDto(
    val yrkesaktivitetidentifikator: String,
    val rader: List<PølseradDto>
)