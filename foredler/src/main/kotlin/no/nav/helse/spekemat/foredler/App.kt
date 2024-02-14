package no.nav.helse.spekemat.foredler

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import net.logstash.logback.argument.StructuredArguments.*
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.CharArrayWriter
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

private val logg = LoggerFactory.getLogger(::main.javaClass)
private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private val objectmapper = jacksonObjectMapper().registerModules(JavaTimeModule())

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        logg.error("Ufanget exception: {}", e.message, e)
        sikkerlogg.error("Ufanget exception: {}", e.message, e)
    }

    launchApp(System.getenv())
}

fun launchApp(env: Map<String, String>) {
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

    val httpClient = HttpClient.newHttpClient()
    val azure = createAzureTokenClientFromEnvironment(env)
    val cluster = System.getenv("NAIS_CLUSTER_NAME")
    val scope = "api://$cluster.tbd.spleis-api/.default"
    val spleisClient = SpleisClient(httpClient, azure, scope)

    val dsProvider = StaticDataSource(hikariConfig)
    val dao = PølseDao(dsProvider)
    val pølsetjenesten = Pølsetjenesten(dao, spleisClient)

    val app = embeddedServer(
        factory = CIO,
        environment = applicationEngineEnvironment {
            log = logg
            connectors.add(EngineConnectorBuilder().apply {
                this.port = 8080
            })
            module {
                authentication { azureApp.konfigurerJwtAuth(this) }
                lagApplikasjonsmodul(hikariConfig, objectmapper, pølsetjenesten)
            }
        }
    )
    app.start(wait = true)
}

fun Application.lagApplikasjonsmodul(migrationConfig: HikariConfig, objectMapper: ObjectMapper, pølsetjeneste: Pølsetjeneste) {
    val readyToggle = AtomicBoolean(false)

    environment.monitor.subscribe(ApplicationStarted) {
        migrate(migrationConfig)
        readyToggle.set(true)
    }

    install(CallId) {
        header("callId")
        verify { it.isNotEmpty() }
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        logger = LoggerFactory.getLogger("no.nav.helse.spekemat.foredler.api.CallLogging")
        level = Level.INFO
        callIdMdc("callId")
        disableDefaultColors()
        filter { call -> call.request.path().startsWith("/api/") }
    }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, FeilResponse(
                feilmelding = "Ugyldig request: ${cause.message}\n${cause.stackTraceToString()}",
                callId = call.callId
            ))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, FeilResponse(
                feilmelding = "Ikke funnet: ${cause.message}\n${cause.stackTraceToString()}",
                callId = call.callId
            ))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, FeilResponse(
                feilmelding = "Tjeneren møtte på ein feilmelding: ${cause.message}\n${cause.stackTraceToString()}",
                callId = call.callId
            ))
        }
    }
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
    requestResponseTracing(LoggerFactory.getLogger("no.nav.helse.spekemat.foredler.api.Tracing"))
    nais(readyToggle)
    routing {
        authenticate {
            api(pølsetjeneste)
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
    val feilmelding: String,
    val callId: String?
)

private const val isaliveEndpoint = "/isalive"
private const val isreadyEndpoint = "/isready"
private const val metricsEndpoint = "/metrics"

private val ignoredPaths = listOf(metricsEndpoint, isaliveEndpoint, isreadyEndpoint)

private fun Application.requestResponseTracing(logger: Logger) {
    intercept(ApplicationCallPipeline.Monitoring) {
        try {
            if (call.request.uri in ignoredPaths) return@intercept proceed()
            val headers = call.request.headers.toMap()
                .filterNot { (key, _) -> key.lowercase() in listOf("authorization") }
                .map { (key, values) ->
                    keyValue("req_header_$key", values.joinToString(separator = ";"))
                }.toTypedArray()
            logger.info("{} {}", v("method", call.request.httpMethod.value), v("uri", call.request.uri), *headers)
            proceed()
        } catch (err: Throwable) {
            logger.error("ukjent feil: ${err.message}", err)
            throw err
        }
    }

    sendPipeline.intercept(ApplicationSendPipeline.After) { message ->
        val status = call.response.status() ?: (when (message) {
            is OutgoingContent -> message.status
            is HttpStatusCode -> message
            else -> null
        } ?: HttpStatusCode.OK).also { status ->
            call.response.status(status)
        }

        if (call.request.uri in ignoredPaths) return@intercept
        logger.info("svarer status=${status.value} ${call.request.uri}")
    }
}

private fun Application.nais(readyToggle: AtomicBoolean) {
    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
        )
    }

    routing {
        get(isaliveEndpoint) {
            call.respondText("ALIVE", ContentType.Text.Plain)
        }

        get(isreadyEndpoint) {
            if (!readyToggle.get()) return@get call.respondText("NOT READY", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
            call.respondText("READY", ContentType.Text.Plain)
        }

        get(metricsEndpoint) {
            val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: emptySet()
            val formatted = CharArrayWriter(1024)
                .also { TextFormat.write004(it, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(names)) }
                .use { it.toString() }

            call.respondText(
                contentType = ContentType.parse(TextFormat.CONTENT_TYPE_004),
                text = formatted
            )
        }
    }
}