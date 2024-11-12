package no.nav.helse.spekemat.slakter

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import net.logstash.logback.argument.StructuredArguments.kv
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
                it.demandValue("@event_name", "behandling_opprettet")
                it.requireKey("@id", "fødselsnummer", "kilde.meldingsreferanseId", "vedtaksperiodeId", "behandlingId")
                it.requireKey("organisasjonsnummer") // a.k.a. yrkesaktivitetidentifikator
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.info("Håndterer ikke behandling_opprettet pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke behandling_opprettet pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val behandlingId = packet["behandlingId"].asUUID()
        val pølse = PølseDto(
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUUID(),
            behandlingId = behandlingId,
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