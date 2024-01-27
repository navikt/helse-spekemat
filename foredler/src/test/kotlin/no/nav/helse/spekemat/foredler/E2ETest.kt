package no.nav.helse.spekemat.foredler

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.mock.MockHttpResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.testing.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spekemat.foredler.Pølsestatus.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

@Isolated
class E2ETest {
    private companion object {
        const val FNR = "12345678911"
        const val A1 = "987654321"
        const val A2 = "112233445"
    }
    private val dao = PølseDao { Database.dataSource }
    private val tokenProvider = object : AzureTokenProvider {
        override fun bearerToken(scope: String) = AzureToken("a token", LocalDateTime.MAX)
        override fun onBehalfOfToken(scope: String, token: String): AzureToken {
            throw NotImplementedError("ikke implementert i mock")
        }
    }
    private val mockHttpClient = mockk<java.net.http.HttpClient>(relaxed = true)
    private val spleisClient = SpleisClient(mockHttpClient, tokenProvider, "spleis-scope")
    private val pølsetjeneste = Pølsetjenesten(dao, spleisClient)
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    @BeforeEach
    fun setup() {
        mockSpleisResponse(listOf(
            SpleisResponse.SpleisPersonResponse.SpleisArbeidsgiverResponse(
                organisasjonsnummer = A1,
                generasjoner = emptyList()
            )
        ))
    }

    @AfterEach
    fun teardown() {
        Database.reset()
        clearMocks(mockHttpClient)
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
        val g1 = enGenerasjonId()
        val g2 = enGenerasjonId()

        sendNyPølseRequest(FNR, A1, v1, generasjonId = g1)
        sendOppdaterPølseRequest(FNR, A1, v1, g1, status = PølsestatusDto.LUKKET) // vedtak fattet

        // annullering
        val annulleringhendelse = UUID.randomUUID()
        sendNyPølseRequest(FNR, A1, v1, generasjonId = g2, kildeId = annulleringhendelse)
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
    }

    @Test
    fun `revurdering av førstegangsbehandling`() = foredlerTestApp{
        val v1 = enVedtaksperiode()
        val v2 = enVedtaksperiode()
        val g1 = enGenerasjonId()
        val g2 = enGenerasjonId()

        sendNyPølseRequest(FNR, A1, v1, generasjonId = g1)
        sendNyPølseRequest(FNR, A1, v2, generasjonId = g2)
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
        assertNotNull(dao.hent(FNR, A1))
        sendSlettRequest(FNR)
        assertNull(dao.hent(FNR, A1))
    }

    @Test
    fun `migrerer person fra spleis`() = foredlerTestApp {
        val testfil = this::class.java.classLoader.getResourceAsStream("spleispersoner/normal_muffins.json") ?: fail { "Klarte ikke å lese filen" }
        val normalMuffins = objectMapper.readValue<SpleisResponse>(testfil)
        mockSpleisResponse(normalMuffins.data.person.arbeidsgivere)

        val v3 = UUID.fromString("82c4aa22-280b-4679-a702-379e43cf0f2d")
        val g3 = UUID.fromString("9458f7f3-d23c-48c0-a1f7-ab0ff0322d17")
        sendOppdaterPølseRequest(FNR, A1, v3, generasjonId = g3, status = PølsestatusDto.LUKKET)

        sendHentPølserRequest(FNR).also { response ->
            val result = response.body<PølserResponse>()
            assertEquals(2, result.yrkesaktiviteter.size)

            result.yrkesaktiviteter.single { it.yrkesaktivitetidentifikator == A1 }.also { ag ->
                assertEquals(A1, ag.yrkesaktivitetidentifikator)
                assertEquals(5, ag.rader.size)

                ag.rader[0].also { rad ->
                    assertEquals("9458f7f3-d23c-48c0-a1f7-ab0ff0322d17", rad.kildeTilRad.toString())
                    assertEquals(5, rad.pølser.size)
                }

                ag.rader[1].also { rad ->
                    assertEquals("9fd21855-b072-4f54-b5f1-cf1a6c660ad2", rad.kildeTilRad.toString())
                    assertEquals(3, rad.pølser.size)
                }

                ag.rader[2].also { rad ->
                    assertEquals("7f694a95-eb2b-4b4d-b49c-aff255d8fe9c", rad.kildeTilRad.toString())
                    assertEquals(3, rad.pølser.size)
                }

                ag.rader[3].also { rad ->
                    assertEquals("0e3ce01c-6d8e-4c64-b8e6-981ee2f0147b", rad.kildeTilRad.toString())
                    assertEquals(3, rad.pølser.size)
                }

                ag.rader[4].also { rad ->
                    assertEquals("5ac7d24c-1fed-4067-9ad0-0a590599bb03", rad.kildeTilRad.toString())
                    assertEquals(2, rad.pølser.size)
                }
            }
            result.yrkesaktiviteter.single { it.yrkesaktivitetidentifikator == A2 }.also { ag ->
                assertEquals(A2, ag.yrkesaktivitetidentifikator)
                assertTrue(ag.rader.isEmpty())
            }
        }
    }

    private fun enVedtaksperiode() = UUID.randomUUID()
    private fun enGenerasjonId() = UUID.randomUUID()

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

    private fun mockSpleisResponse(arbeidsgivere: List<SpleisResponse.SpleisPersonResponse.SpleisArbeidsgiverResponse>) {
        val responseBody = objectMapper.writeValueAsString(SpleisResponse(
            data = SpleisResponse.SpleisDataResponse(
                person = SpleisResponse.SpleisPersonResponse(
                    arbeidsgivere = arbeidsgivere
                )
            )
        ))
        every {
            mockHttpClient.send<String>(any(), any())
        } returns MockHttpResponse(responseBody, statusCode = 200)
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
}

private class TestContext(
    val client: HttpClient
) {
    suspend fun sendNyPølseRequest(
        fnr: String,
        yrkesaktivitetidentifikator: String,
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        hendelseId: UUID = UUID.randomUUID(),
        kildeId: UUID = UUID.randomUUID(),
        generasjonId: UUID = UUID.randomUUID()
    ): HttpResponse {
        return client.post("/api/pølse") {
            contentType(ContentType.Application.Json)
            setBody(NyPølseRequest(
                fnr = fnr,
                yrkesaktivitetidentifikator = yrkesaktivitetidentifikator,
                pølse = NyPølseDto(
                    vedtaksperiodeId = vedtaksperiodeId,
                    generasjonId = generasjonId,
                    kilde = kildeId
                ),
                meldingsreferanseId = hendelseId,
                hendelsedata = "{}"
            ))
        }.also {
            assertTrue(it.status.isSuccess())
        }
    }
    suspend fun sendOppdaterPølseRequest(
        fnr: String,
        yrkesaktivitetidentifikator: String,
        vedtaksperiodeId: UUID,
        generasjonId: UUID,
        status: PølsestatusDto,
        hendelseId: UUID = UUID.randomUUID()
    ): HttpResponse {
        return client.patch("/api/pølse") {
            contentType(ContentType.Application.Json)
            setBody(OppdaterPølseRequest(
                fnr = fnr,
                yrkesaktivitetidentifikator = yrkesaktivitetidentifikator,
                vedtaksperiodeId = vedtaksperiodeId,
                generasjonId = generasjonId,
                status = status,
                meldingsreferanseId = hendelseId,
                hendelsedata = "{}"
            ))
        }.also {
            assertTrue(it.status.isSuccess())
        }
    }

    suspend fun sendHentPølserRequest(fnr: String) =
        client.post("/api/pølser") {
            contentType(ContentType.Application.Json)
            setBody(PølserRequest(
                fnr = fnr
            ))
        }.also {
            assertTrue(it.status.isSuccess())
        }

    suspend fun sendSlettRequest(fnr: String) =
        client.post("/api/slett") {
            contentType(ContentType.Application.Json)
            setBody(SlettRequest(fnr))
        }.also {
            assertTrue(it.status.isSuccess())
        }
}

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