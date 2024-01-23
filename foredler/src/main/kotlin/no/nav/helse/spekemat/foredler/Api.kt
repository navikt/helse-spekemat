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
        call.respond(HttpStatusCode.OK)
    }

    post("/api/pølse") {
        val request = call.receiveNullable<PølseRequest>() ?: return@post call.respond(HttpStatusCode.BadRequest, FeilResponse(
            feilmelding = "Ugyldig request"
        ))
        pølsetjeneste.håndter(request.fnr, request.yrkesaktivitetidentifikator, request.pølse, request.meldingsreferanseId, request.hendelsedata)
        call.respond(HttpStatusCode.OK)
    }
}

data class SlettRequest(val fnr: String)
data class FeilResponse(val feilmelding: String)
data class PølseRequest(
    val fnr: String,
    val yrkesaktivitetidentifikator: String,
    val pølse: PølseDto,
    val meldingsreferanseId: UUID,
    val hendelsedata: String
)