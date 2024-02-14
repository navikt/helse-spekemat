package no.nav.helse.spekemat.slakter

import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.azure.createDefaultAzureTokenClient
import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.LoggerFactory
import java.net.http.HttpClient

private val logg = LoggerFactory.getLogger("no.nav.helse.spekemat.App")
private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val env = System.getenv()
    val httpClient = HttpClient.newHttpClient()

    val azure = createAzureTokenClientFromEnvironment(env)
    val cluster = System.getenv("NAIS_CLUSTER_NAME")
    val scope = "api://$cluster.tbd.spekemat/.default"
    val pølsetjeneste = Pølsetjenesten(httpClient, azure, scope)

    val erUtvikling = cluster == "dev-gcp"

    RapidApplication.create(env)
        .apply {
            logg.info("Hei, er verden klar for pølser?")
            if (erUtvikling) SlettPersonRiver(this, pølsetjeneste)
            GenerasjonOpprettetRiver(this, pølsetjeneste)
            GenerasjonLukketRiver(this, pølsetjeneste)
            GenerasjonForkastetRiver(this, pølsetjeneste)
            AvstemmingRiver(this, pølsetjeneste)
            MigreringRiver(this, pølsetjeneste)
        }
        .start()
}
