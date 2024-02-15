package no.nav.helse.spekemat.slakter

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.util.*

internal class GenerasjonForkastetRiver(
    rapidsConnection: RapidsConnection,
    private val pølsetjeneste: Pølsetjeneste
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(GenerasjonForkastetRiver::class.java)
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "generasjon_forkastet")
                it.requireKey("@id", "fødselsnummer", "vedtaksperiodeId", "generasjonId")
                it.requireKey("organisasjonsnummer") // a.k.a. yrkesaktivitetidentifikator
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke generasjon_forkastet pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke generasjon_forkastet pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asUUID()
        val generasjonId = packet["generasjonId"].asUUID()

        val meldingsreferanseId = packet["@id"].asUUID()
        val fnr = packet["fødselsnummer"].asText()
        val yrkesaktivitetidentifikator = packet["organisasjonsnummer"].asText()

        logg.info("Håndterer generasjon_forkastet {} {} {}", kv("meldingsreferanseId", meldingsreferanseId), kv("vedtaksperiodeId", vedtaksperiodeId), kv("generasjonId", generasjonId))
        sikkerlogg.info("Håndterer generasjon_forkastet {} {} {}", kv("meldingsreferanseId", meldingsreferanseId), kv("vedtaksperiodeId", vedtaksperiodeId), kv("generasjonId", generasjonId))

        feilhåndtering(logg, sikkerlogg) {
            pølsetjeneste.generasjonForkastet(fnr, yrkesaktivitetidentifikator, vedtaksperiodeId, generasjonId, meldingsreferanseId, packet.toJson())
        }
    }

    private fun JsonNode.asUUID() = UUID.fromString(asText())
}