package no.nav.helse.spekemat.foredler

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.testing.*
import io.ktor.client.plugins.contentnegotiation.*
import kotliquery.queryOf
import kotliquery.sessionOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.parallel.Isolated
import java.time.Instant
import java.util.*

@Isolated
class E2ETest {
    private companion object {
        const val FNR = "12345678911"
        const val ORGN = "987654321"
    }
    private val dao = PølseDao { Database.dataSource }
    private val pølsetjeneste = Pølsetjenesten(dao)
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @AfterEach
    fun teardown() {
        Database.reset()
    }

    private fun foredlerTestApp(testblokk: suspend TestContext.() -> Unit) {
        testApplication {
            application {
                authentication {
                    provider {
                        authenticate { context ->
                            JWTPrincipal(LokalePayload(mapOf(
                                "azp_name" to "spekemat-slakter"
                            )))
                        }
                    }
                }
                lagApplikasjonsmodul(Database.hikariConfig, objectMapper, pølsetjeneste)
            }
            startApplication()

            do {
                val response = client.get("/isready")
                println("Venter på at isready svarer OK…:${response.status}")
            } while (!response.status.isSuccess())

            testblokk(TestContext(createClient {
                install(ContentNegotiation) {
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
                }
            }))
        }
    }

    @Test
    fun `ett vedtak`() = foredlerTestApp {
        val hendelseId = UUID.randomUUID()
        val kildeId = UUID.randomUUID()

        val response = client.post("/api/pølse") {
            contentType(ContentType.Application.Json)
            setBody(PølseRequest(
                fnr = FNR,
                yrkesaktivitetidentifikator = ORGN,
                pølse = PølseDto(
                    vedtaksperiodeId = UUID.randomUUID(),
                    generasjonId = UUID.randomUUID(),
                    kilde = kildeId
                ),
                meldingsreferanseId = hendelseId,
                hendelsedata = "{}"
            ))
        }

        assertTrue(response.status.isSuccess())
        verifiserPersonFinnes(FNR)
        verifiserHendelseFinnes(hendelseId)
        verifiserPølsepakkeFinnes(FNR, ORGN, hendelseId, kildeId)
    }

    @Test
    fun `to vedtak`() = foredlerTestApp{
        val v1 = enVedtaksperiode()
        val v2 = enVedtaksperiode()

        val response1 = client.post("/api/pølse") {
            contentType(ContentType.Application.Json)
            setBody(PølseRequest(
                fnr = FNR,
                yrkesaktivitetidentifikator = ORGN,
                pølse = PølseDto(
                    vedtaksperiodeId = v1,
                    generasjonId = UUID.randomUUID(),
                    kilde = UUID.randomUUID()
                ),
                meldingsreferanseId = UUID.randomUUID(),
                hendelsedata = "{}"
            ))
        }
        val response2 = client.post("/api/pølse") {
            contentType(ContentType.Application.Json)
            setBody(PølseRequest(
                fnr = FNR,
                yrkesaktivitetidentifikator = ORGN,
                pølse = PølseDto(
                    vedtaksperiodeId = v2,
                    generasjonId = UUID.randomUUID(),
                    kilde = UUID.randomUUID()
                ),
                meldingsreferanseId = UUID.randomUUID(),
                hendelsedata = "{}"
            ))
        }

        assertTrue(response1.status.isSuccess())
        assertTrue(response2.status.isSuccess())
        val fabrikk = dao.hent(FNR, ORGN)?.pakke() ?: fail { "Forventet å finne person" }
        assertEquals(1, fabrikk.size)
        assertEquals(2, fabrikk.single().pølser.size)
    }

    @Test
    fun `slette person`() {
        val v1 = enVedtaksperiode()

        pølsetjeneste.håndter(FNR, ORGN, PølseDto(v1, UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID(), "{}")
        assertNotNull(dao.hent(FNR, ORGN))
        pølsetjeneste.slett(FNR)
        assertNull(dao.hent(FNR, ORGN))
    }

    private fun enVedtaksperiode() = UUID.randomUUID()

    private fun verifiserPersonFinnes(fnr: String) {
        sessionOf(Database.dataSource).use {
            assertEquals(true, it.run(queryOf("SELECT EXISTS(SELECT 1 FROM person WHERE fnr = ?)", fnr).map { row -> row.boolean(1) }.asSingle))
        }
    }
    private fun verifiserHendelseFinnes(id: UUID) {
        sessionOf(Database.dataSource).use {
            assertEquals(true, it.run(queryOf("SELECT EXISTS(SELECT 1 FROM hendelse WHERE meldingsreferanse_id = ?)", id).map { row -> row.boolean(1) }.asSingle))
        }
    }
    private fun verifiserPølsepakkeFinnes(fnr: String, yrkesaktivitetidentifikator: String, hendelseId: UUID, kildeId: UUID) {
        sessionOf(Database.dataSource).use {
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
}

private class TestContext(
    val client: HttpClient
)

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