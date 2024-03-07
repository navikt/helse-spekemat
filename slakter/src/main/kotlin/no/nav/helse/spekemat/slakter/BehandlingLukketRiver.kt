package no.nav.helse.spekemat.slakter

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.util.*

internal class BehandlingLukketRiver(
    rapidsConnection: RapidsConnection,
    private val pølsetjeneste: Pølsetjeneste
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(BehandlingLukketRiver::class.java)
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "behandling_lukket")
                it.requireKey("@id", "fødselsnummer", "vedtaksperiodeId", "behandlingId")
                it.requireKey("organisasjonsnummer") // a.k.a. yrkesaktivitetidentifikator
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke behandling_lukket pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke behandling_lukket pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asUUID()
        val behandlingId = packet["behandlingId"].asUUID()

        val meldingsreferanseId = packet["@id"].asUUID()
        val fnr = packet["fødselsnummer"].asText()
        val yrkesaktivitetidentifikator = packet["organisasjonsnummer"].asText()

        logg.info("Håndterer behandling_lukket {} {} {}", kv("meldingsreferanseId", meldingsreferanseId), kv("vedtaksperiodeId", vedtaksperiodeId), kv("behandlingId", behandlingId))
        sikkerlogg.info("Håndterer behandling_lukket {} {} {}", kv("meldingsreferanseId", meldingsreferanseId), kv("vedtaksperiodeId", vedtaksperiodeId), kv("behandlingId", behandlingId))

        feilhåndtering(logg, sikkerlogg) {
            pølsetjeneste.behandlingLukket(fnr, yrkesaktivitetidentifikator, vedtaksperiodeId, behandlingId, meldingsreferanseId, packet.toJson())
        }
    }

    private fun JsonNode.asUUID() = UUID.fromString(asText())
}