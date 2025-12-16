package no.nav.helse.spekemat.foredler

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.naisful.naisApp
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.header
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration

private val logg = LoggerFactory.getLogger(::main.javaClass)
private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private val objectmapper = jacksonObjectMapper().registerModules(JavaTimeModule())

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        logg.error("Exception på frifot: {}", e.message, e)
        sikkerlogg.error("Exception på frifot: {}", e.message, e)
    }

    launchApp(System.getenv())
}

fun launchApp(env: Map<String, String>) {
    val erUtvikling = env["NAIS_CLUSTER_NAME"] == "dev-gcp"
    val azureApp = AzureApp(
        jwkProvider = JwkProviderBuilder(URI(env.getValue("AZURE_OPENID_CONFIG_JWKS_URI")).toURL()).build(),
        issuer = env.getValue("AZURE_OPENID_CONFIG_ISSUER"),
        clientId = env.getValue("AZURE_APP_CLIENT_ID"),
    )

    val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", env.getValue("DATABASE_HOST"), env.getValue("DATABASE_PORT"), env.getValue("DATABASE_DATABASE"))
        username = env.getValue("DATABASE_USERNAME")
        password = env.getValue("DATABASE_PASSWORD")
        maximumPoolSize = 2
        initializationFailTimeout = Duration.ofMinutes(5).toMillis()
    }

    val dsProvider = StaticDataSource(hikariConfig)
    val dao = PølseDao(dsProvider)
    val pølsetjenesten = Pølsetjenesten(dao)

    val app = naisApp(
        meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
        objectMapper = objectmapper,
        applicationLogger = logg,
        callLogger = LoggerFactory.getLogger("no.nav.helse.spekemat.foredler.api.CallLogging"),
        timersConfig = { call, _ ->
            this
                .tag("azp_name", call.principal<JWTPrincipal>()?.get("azp_name") ?: "n/a")
                // https://github.com/linkerd/polixy/blob/main/DESIGN.md#l5d-client-id-client-id
                // eksempel: <APP>.<NAMESPACE>.serviceaccount.identity.linkerd.cluster.local
                .tag("konsument", call.request.header("L5d-Client-Id") ?: "n/a")
        },
        mdcEntries = mapOf(
            "azp_name" to { call: ApplicationCall -> call.principal<JWTPrincipal>()?.get("azp_name") },
            "konsument" to { call: ApplicationCall -> call.request.header("L5d-Client-Id") }
        ),
        applicationModule = {
            authentication { azureApp.konfigurerJwtAuth(this) }
            lagApplikasjonsmodul(hikariConfig, pølsetjenesten, erUtvikling)
        }
    )
    app.start(wait = true)
}

fun Application.lagApplikasjonsmodul(migrationConfig: HikariConfig, pølsetjeneste: Pølsetjeneste, erUtvikling: Boolean) {
    monitor.subscribe(ApplicationStarted) {
        migrate(migrationConfig)
    }
    routing {
        authenticate {
            api(pølsetjeneste, erUtvikling)
        }
    }
}

private class StaticDataSource(hikariConfig: HikariConfig) : DatasourceProvider {
    private val ds by lazy { // by lazy slik at vi lager én verdi, men først når noen trenger den
        hikariConfig.maximumPoolSize = 5
        HikariDataSource(hikariConfig)
    }

    override fun getDataSource() = ds
}

private fun migrate(config: HikariConfig) {
    HikariDataSource(config).use { ds ->
        Flyway.configure()
            .dataSource(ds)
            .validateMigrationNaming(true)
            .load()
            .migrate()
    }
}

data class FeilResponse(
    val type: URI,
    val title: String,
    val status: Int,
    val detail: String?,
    val callId: String?
)
