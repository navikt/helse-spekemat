package no.nav.helse.spekemat.slakter

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage

internal fun JsonMessage.validerYrkesaktivitetidentifikator() {
    requireKey("yrkesaktivitetstype")
    interestedIn("organisasjonsnummer")
}

internal fun JsonMessage.yrkesaktivitetidentifikator(): String {
    val yrkesaktivitetstype = get("yrkesaktivitetstype").asString()
    if (yrkesaktivitetstype != "ARBEIDSTAKER") return yrkesaktivitetstype
    return get("organisasjonsnummer").asString()
}
