package no.nav.helse.spekemat.slakter

import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.Logger

private val erUtvikling = System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp"
fun feilhåndtering(logg: Logger, sikkerlogg: Logger, kodeblokk: () -> Unit) {
    try {
        kodeblokk()
    } catch (err: IkkeFunnetException) {
        if (!erUtvikling) throw err
        // tillater at ting ikke finnes i dev
        logg.info("Testpersonen / vedtaksperioden finnes ikke: ${err.feilmeldingResponse?.detail}", kv("callId", err.callId), err)
        sikkerlogg.info("Testpersonen / vedtaksperioden finnes ikke: ${err.feilmeldingResponse?.detail}", kv("callId", err.callId), err)
    }
}