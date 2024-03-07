package no.nav.helse.spekemat.slakter

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.util.*

internal class BehandlingOpprettetRiver(
    rapidsConnection: RapidsConnection,
    private val pølsetjeneste: Pølsetjeneste
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(BehandlingOpprettetRiver::class.java)
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "generasjon_opprettet")
                it.requireKey("@id", "fødselsnummer", "kilde.meldingsreferanseId", "vedtaksperiodeId", "generasjonId")
                it.requireKey("organisasjonsnummer") // a.k.a. yrkesaktivitetidentifikator
                it.interestedIn("behandlingId")
            }
        }.register(this)
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "behandling_opprettet")
                it.requireKey("@id", "fødselsnummer", "kilde.meldingsreferanseId", "vedtaksperiodeId", "behandlingId")
                it.requireKey("organisasjonsnummer") // a.k.a. yrkesaktivitetidentifikator
                it.interestedIn("generasjonId")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke behandling_opprettet pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke behandling_opprettet pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val behandlingId = packet["generasjonId"].takeIf(JsonNode::isTextual)?.asUUID() ?: packet["behandlingId"].asUUID()
        val pølse = PølseDto(
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUUID(),
            generasjonId = behandlingId,
            kilde = packet["kilde.meldingsreferanseId"].asUUID()
        )

        val meldingsreferanseId = packet["@id"].asUUID()
        val fnr = packet["fødselsnummer"].asText()
        val yrkesaktivitetidentifikator = packet["organisasjonsnummer"].asText()

        logg.info("Håndterer behandling_opprettet {}", kv("meldingsreferanseId", meldingsreferanseId))
        sikkerlogg.info("Håndterer behandling_opprettet {}", kv("meldingsreferanseId", meldingsreferanseId))

        pølsetjeneste.behandlingOpprettet(fnr, yrkesaktivitetidentifikator, pølse, meldingsreferanseId, packet.toJson())
    }

    private fun JsonNode.asUUID() = UUID.fromString(asText())
}