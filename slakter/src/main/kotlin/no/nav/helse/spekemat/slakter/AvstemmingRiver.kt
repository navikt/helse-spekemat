package no.nav.helse.spekemat.slakter

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

internal class AvstemmingRiver(
    rapidsConnection: RapidsConnection,
    private val tjeneste: Pølsetjeneste
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(AvstemmingRiver::class.java)
        private val String.maskertFnr get() = take(6).padEnd(11, '*')
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "avstemming")
                it.requireKey("@id", "fødselsnummer")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        sikkerlogg.info("Oppretter eventuell manglende person med fødselsnummer: $fødselsnummer")
        logg.info("Oppretter eventuell manglende person med fødselsnummer: ${fødselsnummer.maskertFnr}")
        tjeneste.opprett(fødselsnummer)
    }
}