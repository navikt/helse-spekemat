package no.nav.helse.spekemat

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

internal class SlettPersonRiver(
    rapidsConnection: RapidsConnection,
    private val pølseDao: PølseDao
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(SlettPersonRiver::class.java)
        private val String.maskertFnr get() = take(6).padEnd(11, '*')
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "slett_person")
                it.requireKey("@id", "fødselsnummer")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        sikkerlogg.info("Sletter person med fødselsnummer: $fødselsnummer")
        logg.info("Sletter person med fødselsnummer: ${fødselsnummer.maskertFnr}")
        pølseDao.slett(fødselsnummer)
    }
}