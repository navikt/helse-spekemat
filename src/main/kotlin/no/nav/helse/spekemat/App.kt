package no.nav.helse.spekemat

import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.LoggerFactory

private val logg = LoggerFactory.getLogger("no.nav.helse.spekemat.App")
private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    RapidApplication.create(System.getenv())
        .also {
            logg.info("Hei, er verden klar for pølser?")
        }
        .start()
}