package no.nav.helse.spekemat.foredler

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.naisful.test.TestContext
import com.github.navikt.tbd_libs.naisful.test.naisfulTestApp
import com.github.navikt.tbd_libs.test_support.CleanupStrategy
import com.github.navikt.tbd_libs.test_support.DatabaseContainers
import com.github.navikt.tbd_libs.test_support.TestDataSource
import com.zaxxer.hikari.HikariConfig
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.clearMocks
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spekemat.fabrikk.Pølsestatus.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class E2ETest {
    private companion object {
        const val FNR = "12345678911"
        const val A1 = "987654321"
        const val A2 = "112233445"
        private val databaseContainer = DatabaseContainers.container("spekemat", CleanupStrategy.tables("person, hendelse"))
    }

    private lateinit var dataSource: TestDataSource
    private val dao = PølseDao { dataSource.ds }
    private val mockHttpClient = mockk<java.net.http.HttpClient>(relaxed = true)
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    @BeforeEach
    fun setup() {
        dataSource = databaseContainer.nyTilkobling()
    }

    @AfterEach
    fun teardown() {
        databaseContainer.droppTilkobling(dataSource)
        clearMocks(mockHttpClient)
    }

    @Test
    fun `ugyldig json`() = foredlerTestApp {
        val response = client.post("/api/pølse") {
            contentType(ContentType.Application.Json)
            setBody("""dette er absolutt ikke json""")
        }
        val feilmelding = objectMapper.readValue<FeilResponse>(response.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(feilmelding.detail!!.contains("Failed to convert request body"))
    }

    @Test
    fun `oppdatering på tom fabrikk`() = foredlerTestApp {
        sendOppdaterPølseRequest(FNR, A1, UUID.randomUUID(), UUID.randomUUID(), PølsestatusDto.LUKKET, forventetStatusCode = HttpStatusCode.NotFound).also { response ->
            val feilmelding = objectMapper.readValue<FeilResponse>(response.bodyAsText())
            assertTrue(feilmelding.detail!!.contains("Ingen registrert pølsepakke for vedkommende"))
        }
    }

    @Test
    fun `oppdatering på pølse som ikke finnes`() = foredlerTestApp {
        sendNyPølseRequest(FNR, A1)
        sendOppdaterPølseRequest(FNR, A1, UUID.randomUUID(), UUID.randomUUID(), PølsestatusDto.LUKKET, forventetStatusCode = HttpStatusCode.NotFound).also { response ->
            val feilmelding = objectMapper.readValue<FeilResponse>(response.bodyAsText())
            assertTrue(feilmelding.detail!!.contains("Ingen pølse registrert"))
        }
    }

    @Test
    fun `oppdatering på eldre pølse`() = foredlerTestApp {
        val v1 = enVedtaksperiode()
        val g1 = enBehandling()
        val g2 = enBehandling()

        sendNyPølseRequest(FNR, A1, v1, behandlingId = g1)
        sendOppdaterPølseRequest(FNR, A1, v1, g1, status = PølsestatusDto.LUKKET) // vedtak fattet

        val revurderinghendelse = UUID.randomUUID()
        sendNyPølseRequest(FNR, A1, v1, behandlingId = g2, kildeId = revurderinghendelse)
        sendOppdaterPølseRequest(FNR, A1, v1, g2, status = PølsestatusDto.LUKKET, hendelseId = revurderinghendelse)

        sendOppdaterPølseRequest(FNR, A1, v1, g1, PølsestatusDto.LUKKET, forventetStatusCode = HttpStatusCode.OK).also { response ->
            val feilmelding = objectMapper.readValue<OkMeldingResponse>(response.bodyAsText())
            assertTrue(feilmelding.melding.contains("jeg tror du er litt out-of-order?"))
        }
    }

    @Test
    fun `ett vedtak`() = foredlerTestApp {
        val hendelseId = UUID.randomUUID()
        val kildeId = UUID.randomUUID()

        sendNyPølseRequest(FNR, A1, hendelseId = hendelseId, kildeId = kildeId).also { response ->
            assertTrue(response.status.isSuccess())
        }

        verifiserPersonFinnes(FNR)
        verifiserHendelseFinnes(hendelseId)
        verifiserPølsepakkeFinnes(FNR, A1, hendelseId, kildeId)

        sendHentPølserRequest(FNR).also { response ->
            assertTrue(response.status.isSuccess())
            val result = objectMapper.readValue<PølserResponse>(response.bodyAsText())
            assertEquals(1, result.yrkesaktiviteter.size)
            assertEquals(A1, result.yrkesaktiviteter.single().yrkesaktivitetidentifikator)
            assertEquals(1, result.yrkesaktiviteter.single().rader.size)
        }
    }

    @Test
    fun `et forkastet vedtak`() = foredlerTestApp {
        val v1 = enVedtaksperiode()
        val g1 = enBehandling()
        val g2 = enBehandling()

        sendNyPølseRequest(FNR, A1, v1, behandlingId = g1)
        sendOppdaterPølseRequest(FNR, A1, v1, g1, status = PølsestatusDto.LUKKET) // vedtak fattet

        // annullering
        val annulleringhendelse = UUID.randomUUID()
        sendNyPølseRequest(FNR, A1, v1, behandlingId = g2, kildeId = annulleringhendelse)
        sendOppdaterPølseRequest(FNR, A1, v1, g2, status = PølsestatusDto.FORKASTET, hendelseId = annulleringhendelse)

        sendHentPølserRequest(FNR).also { response ->
            val result = response.body<PølserResponse>()
            assertEquals(1, result.yrkesaktiviteter.size)
            result.yrkesaktiviteter[0].also { a1 ->
                assertEquals(A1, a1.yrkesaktivitetidentifikator)
                assertEquals(2, a1.rader.size)

                assertEquals(1, a1.rader[0].pølser.size)
                assertEquals(FORKASTET, a1.rader[0].pølser.single().status)

                assertEquals(1, a1.rader[1].pølser.size)
                assertEquals(LUKKET, a1.rader[1].pølser.single().status)
            }
        }
    }

    @Test
    fun `to vedtak to yrkesaktiviteter`() = foredlerTestApp{
        val v1 = enVedtaksperiode()
        val v2 = enVedtaksperiode()
        val v3 = enVedtaksperiode()
        val v4 = enVedtaksperiode()

        sendNyPølseRequest(FNR, A1, v1)
        sendNyPølseRequest(FNR, A1, v2)
        sendNyPølseRequest(FNR, A2, v3)
        sendNyPølseRequest(FNR, A2, v4)

        sendHentPølserRequest(FNR).also { response ->
            val result = response.body<PølserResponse>()
            assertEquals(2, result.yrkesaktiviteter.size)
            result.yrkesaktiviteter[0].also { a1 ->
                assertEquals(A1, a1.yrkesaktivitetidentifikator)
                assertEquals(1, a1.rader.size)
                assertEquals(2, a1.rader.single().pølser.size)
            }
            result.yrkesaktiviteter[1].also { a2 ->
                assertEquals(A2, a2.yrkesaktivitetidentifikator)
                assertEquals(1, a2.rader.size)
                assertEquals(2, a2.rader.single().pølser.size)
            }
        }
        assertEquals(2, antallHistorikkinnslag(FNR))
        assertEquals(1, antallHistorikkinnslag(FNR, A1))
        assertEquals(1, antallHistorikkinnslag(FNR, A2))
    }

    @Test
    fun `revurdering av førstegangsbehandling`() = foredlerTestApp{
        val v1 = enVedtaksperiode()
        val v2 = enVedtaksperiode()
        val g1 = enBehandling()
        val g2 = enBehandling()

        sendNyPølseRequest(FNR, A1, v1, behandlingId = g1)
        sendNyPølseRequest(FNR, A1, v2, behandlingId = g2)
        sendOppdaterPølseRequest(FNR, A1, v1, g1, status = PølsestatusDto.LUKKET) // vedtak fattet
        sendOppdaterPølseRequest(FNR, A1, v2, g2, status = PølsestatusDto.LUKKET) // vedtak fattet

        // revurdering i gang
        val revurderingkilde = UUID.randomUUID()
        sendNyPølseRequest(FNR, A1, v1, kildeId = revurderingkilde)
        sendNyPølseRequest(FNR, A1, v2, kildeId = revurderingkilde)

        sendHentPølserRequest(FNR).also { response ->
            val result = response.body<PølserResponse>()
            assertEquals(1, result.yrkesaktiviteter.size)
            result.yrkesaktiviteter[0].also { a1 ->
                assertEquals(A1, a1.yrkesaktivitetidentifikator)
                assertEquals(2, a1.rader.size)

                assertEquals(2, a1.rader[0].pølser.size)
                assertTrue(a1.rader[0].pølser.all { it.status == ÅPEN })

                assertEquals(2, a1.rader[1].pølser.size)
                assertTrue(a1.rader[1].pølser.all { it.status == LUKKET })
            }
        }
    }

    @Test
    fun `slette person`() = foredlerTestApp{
        val v1 = enVedtaksperiode()

        sendNyPølseRequest(FNR, A1, v1)
        verifiserYrkesaktivitetFinnes(FNR, A1)
        sendSlettRequest(FNR)
        verifiserYrkesaktivitetIkkeFinnes(FNR, A1)
    }

    @Test
    fun `metrics`() = foredlerTestApp {
        assertTrue(this.client.get("/metrics") {  }.bodyAsText().isNotBlank())
    }

    private fun enVedtaksperiode() = UUID.randomUUID()
    private fun enBehandling() = UUID.randomUUID()

    private fun antallHistorikkinnslag(fnr: String) =
        sessionOf(dataSource.ds).use {
            it.run(queryOf("SELECT COUNT(1) FROM polsepakke_historikk WHERE person_id=(SELECT id FROM person WHERE fnr=?)", fnr).map {
                it.long(1)
            }.asSingle)!!
        }
    private fun antallHistorikkinnslag(fnr: String, yrkesaktivitetidentifikator: String) =
        sessionOf(dataSource.ds).use {
            it.run(queryOf("SELECT COUNT(1) FROM polsepakke_historikk WHERE yrkesaktivitetidentifikator=? AND person_id=(SELECT id FROM person WHERE fnr=?)", yrkesaktivitetidentifikator, fnr).map {
                it.long(1)
            }.asSingle)!!
        }

    private fun verifiserPersonFinnes(fnr: String) {
        sessionOf(dataSource.ds).use {
            assertEquals(true, it.run(queryOf("SELECT EXISTS(SELECT 1 FROM person WHERE fnr = ?)", fnr).map { row -> row.boolean(1) }.asSingle))
        }
    }

    private fun verifiserYrkesaktivitetFinnes(fnr: String, yrkesaktivitetidentifikator: String) {
        assertTrue(yrkesaktivitetFinnes(fnr, yrkesaktivitetidentifikator))
    }
    private fun verifiserYrkesaktivitetIkkeFinnes(fnr: String, yrkesaktivitetidentifikator: String) {
        assertFalse(yrkesaktivitetFinnes(fnr, yrkesaktivitetidentifikator))
    }
    private fun yrkesaktivitetFinnes(fnr: String, yrkesaktivitetidentifikator: String) =
        sessionOf(dataSource.ds).use {
            it.run(queryOf("SELECT EXISTS(SELECT 1 FROM polsepakke WHERE yrkesaktivitetidentifikator = ? AND person_id = (SELECT id FROM person WHERE fnr = ?))", yrkesaktivitetidentifikator, fnr).map { row -> row.boolean(1) }.asSingle)
        } ?: false

    private fun verifiserHendelseFinnes(id: UUID) {
        sessionOf(dataSource.ds).use {
            assertEquals(true, it.run(queryOf("SELECT EXISTS(SELECT 1 FROM hendelse WHERE meldingsreferanse_id = ?)", id).map { row -> row.boolean(1) }.asSingle))
        }
    }
    private fun verifiserPølsepakkeFinnes(fnr: String, yrkesaktivitetidentifikator: String, hendelseId: UUID, kildeId: UUID) {
        sessionOf(dataSource.ds).use {
            assertEquals(true, it.run(queryOf(
                """
                SELECT EXISTS (
                    SELECT 1 FROM polsepakke
                    WHERE person_id = (SELECT id FROM person WHERE fnr = :fnr)
                    AND yrkesaktivitetidentifikator = :yid
                    AND hendelse_id = (SELECT id FROM hendelse WHERE meldingsreferanse_id = :hendelseId)
                    AND kilde_id = :kildeId
                )
            """.trimIndent(), mapOf(
                    "fnr" to fnr,
                    "yid" to yrkesaktivitetidentifikator,
                    "hendelseId" to hendelseId,
                    "kildeId" to kildeId
                )
            ).map { row -> row.boolean(1) }.asSingle))
        }
    }

    private fun foredlerTestApp(pølsetjeneste: Pølsetjeneste = Pølsetjenesten(dao), testblokk: suspend TestContext.() -> Unit) {
        naisfulTestApp(
            testApplicationModule = {
                authentication {
                    provider {
                        authenticate { context ->
                            JWTPrincipal(LokalePayload(mapOf(
                                "azp_name" to "spekemat-slakter"
                            )))
                        }
                    }
                }
                val migrateConfig = HikariConfig()
                (dataSource.ds.hikariConfigMXBean as HikariConfig).copyStateTo(migrateConfig)
                lagApplikasjonsmodul(migrateConfig, pølsetjeneste, true)
            },
            objectMapper = objectMapper,
            meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
            testblokk = testblokk
        )
    }
}
suspend fun TestContext.sendNyPølseRequest(
    fnr: String,
    yrkesaktivitetidentifikator: String,
    vedtaksperiodeId: UUID = UUID.randomUUID(),
    hendelseId: UUID = UUID.randomUUID(),
    kildeId: UUID = UUID.randomUUID(),
    behandlingId: UUID = UUID.randomUUID()
): HttpResponse {
    return client.post("/api/pølse") {
        contentType(ContentType.Application.Json)
        setBody(NyPølseRequest(
            fnr = fnr,
            yrkesaktivitetidentifikator = yrkesaktivitetidentifikator,
            pølse = NyPølseDto(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                kilde = kildeId
            ),
            meldingsreferanseId = hendelseId,
            hendelsedata = "{}"
        ))
    }.also {
        assertTrue(it.status.isSuccess())
    }
}
suspend fun TestContext.sendOppdaterPølseRequest(
    fnr: String,
    yrkesaktivitetidentifikator: String,
    vedtaksperiodeId: UUID,
    behandlingId: UUID,
    status: PølsestatusDto,
    hendelseId: UUID = UUID.randomUUID(),
    forventetStatusCode: HttpStatusCode = HttpStatusCode.OK
): HttpResponse {
    return client.patch("/api/pølse") {
        contentType(ContentType.Application.Json)
        setBody(OppdaterPølseRequest(
            fnr = fnr,
            yrkesaktivitetidentifikator = yrkesaktivitetidentifikator,
            vedtaksperiodeId = vedtaksperiodeId,
            behandlingId = behandlingId,
            status = status,
            meldingsreferanseId = hendelseId,
            hendelsedata = "{}"
        ))
    }.also {
        assertEquals(forventetStatusCode, it.status)
    }
}

suspend fun TestContext.sendHentPølserRequest(fnr: String) =
    client.post("/api/pølser") {
        contentType(ContentType.Application.Json)
        setBody(PølserRequest(
            fnr = fnr
        ))
    }.also {
        assertTrue(it.status.isSuccess())
    }

suspend fun TestContext.sendSlettRequest(fnr: String) =
    client.delete("/api/person") {
        contentType(ContentType.Application.Json)
        setBody(SlettRequest(fnr))
    }.also {
        assertTrue(it.status.isSuccess())
    }

private data class OkMeldingResponse(val melding: String)

class LokalePayload(claims: Map<String, String>) : Payload {
    private val claims = claims.mapValues { LokaleClaim(it.value) }
    override fun getIssuer(): String {
        return "lokal utsteder"
    }

    override fun getSubject(): String {
        return "lokal subjekt"
    }

    override fun getAudience(): List<String> {
        return listOf("lokal publikum")
    }

    override fun getExpiresAt(): Date {
        return Date.from(Instant.MAX)
    }

    override fun getNotBefore(): Date {
        return Date.from(Instant.EPOCH)
    }

    override fun getIssuedAt(): Date {
        return Date.from(Instant.now())
    }

    override fun getId(): String {
        return "lokal id"
    }

    override fun getClaim(name: String): Claim {
        return claims.getValue(name)
    }

    override fun getClaims(): Map<String, Claim> {
        return claims
    }
}

private class LokaleClaim(private val verdi: String) : Claim {
    override fun isNull() = false
    override fun isMissing() = false
    override fun asBoolean() = true
    override fun asInt() = 0
    override fun asLong() = 0L
    override fun asDouble() = 0.0
    override fun asString() = verdi
    override fun asDate() = Date.from(Instant.EPOCH)
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> asArray(clazz: Class<T>?) = emptyArray<Any>() as Array<T>
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> asList(clazz: Class<T>?) = emptyList<Any>() as List<T>
    override fun asMap() = emptyMap<String, Any>()
    override fun <T : Any?> `as`(clazz: Class<T>?) = throw NotImplementedError()
}