package no.nav.helse.spekemat.foredler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.spekemat.fabrikk.PølseDto
import no.nav.helse.spekemat.fabrikk.Pølsefabrikk
import no.nav.helse.spekemat.fabrikk.PølseradDto
import no.nav.helse.spekemat.fabrikk.Pølsestatus
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource

fun interface DatasourceProvider {
    fun getDataSource(): DataSource
}

class PølseDao(private val dataSource: DatasourceProvider) {

    fun slett(fnr: String) {
        sessionOf(dataSource.getDataSource()).use {
            it.run(queryOf("DELETE FROM person WHERE fnr=?", fnr).asExecute)
        }
    }

    fun hent(fnr: String) = hentPerson(fnr).map { (yrkesaktivitetidentifikator, rader) ->
        YrkesaktivitetDto(yrkesaktivitetidentifikator, Pølsefabrikk.gjenopprett(rader.tilModellDto()).pakke())
    }

    fun opprettPerson(fnr: String, oppretting: () -> List<YrkesaktivitetDto>) {
        sessionOf(dataSource.getDataSource()).use {
            it.transaction { session ->
                session.hentEllerOpprettPerson(fnr, oppretting)
            }
        }
    }

    fun behandle(
        fnr: String,
        yrkesaktivitetidentifikator: String,
        meldingsreferanseId: UUID,
        hendelsedata: String,
        oppretting: () -> List<YrkesaktivitetDto>,
        behandling: (Pølsefabrikk) -> PølseDto
    ) {
        sessionOf(dataSource.getDataSource()).use {
            it.transaction { session ->
                if (session.hendelseHåndtertFør(meldingsreferanseId)) return logg.info("hendelsen {} er håndtert fra før", kv("meldingsreferanseId", meldingsreferanseId))

                val personId = session.hentEllerOpprettPerson(fnr, oppretting)

                val fabrikk = session.hentPølsepakke(personId, yrkesaktivitetidentifikator)?.let { personrad ->
                    Pølsefabrikk.gjenopprett(personrad.tilModellDto())
                } ?: Pølsefabrikk()

                val resultat = behandling(fabrikk)
                session.lagre(personId, yrkesaktivitetidentifikator, fabrikk.pakke(), resultat.kilde, meldingsreferanseId, hendelsedata)
            }
        }
    }

    private fun TransactionalSession.hentEllerOpprettPerson(fnr: String, oppretting: () -> List<YrkesaktivitetDto>): Long {
        return hentPersonOgLåsForBehandling(fnr) ?: opprettPerson(fnr, oppretting)
    }

    private fun hentPerson(fnr: String) =
        sessionOf(dataSource.getDataSource()).use { session ->
            session.hentPølsepakker(fnr)
        }

    private fun TransactionalSession.lagre(
        personId: Long,
        yrkesaktivitetidentifikator: String,
        resultat: List<PølseradDto>,
        kildeId: UUID,
        meldingsreferanseId: UUID,
        hendelsedata: String
    ) {
        val hendelseId = opprettHendelse(meldingsreferanseId, hendelsedata)
        val pølsepakkejson = objectMapper.writeValueAsString(Personrad.fraModellDto(resultat))

        run(queryOf(KOPIER_PØLSEPAKKE, mapOf("personId" to personId, "yid" to yrkesaktivitetidentifikator)).asExecute)
        run(queryOf(
            OPPRETT_PØLSEPAKKE, mapOf(
            "personId" to personId,
            "yid" to yrkesaktivitetidentifikator,
            "hendelseId" to hendelseId,
            "kildeId" to kildeId,
            "data" to pølsepakkejson
        )).asExecute)
    }

    private fun TransactionalSession.hendelseHåndtertFør(meldingsreferanseId: UUID): Boolean {
        return checkNotNull(run(queryOf(HENT_HENDELSE, mapOf("id" to meldingsreferanseId)).map { rad ->
            rad.boolean("finnes")
        }.asSingle)) { "Forventer ikke å få null her" }
    }

    private fun TransactionalSession.opprettHendelse(meldingsreferanseId: UUID, hendelsedata: String): Long {
        return checkNotNull(run(queryOf(OPPRETT_HENDELSE, mapOf("id" to meldingsreferanseId, "data" to hendelsedata)).map { rad ->
            rad.long("id")
        }.asSingle)) {
            "Forventet å lage en ny hendelse"
        }
    }
    private fun TransactionalSession.hentPersonOgLåsForBehandling(fnr: String): Long? {
        return run(queryOf(HENT_PERSON_MED_LÅS, mapOf("fnr" to fnr)).map { it.long("id") }.asSingle)
    }
    private fun Session.hentPølsepakke(personId: Long, yrkesaktivitetidentifikator: String) =
        run(queryOf(HENT_PØLSEPAKKE_MED_LÅS, mapOf("pid" to personId, "yid" to yrkesaktivitetidentifikator)).map { rad ->
            objectMapper.readValue<Personrad>(rad.string("data"))
        }.asSingle)
    private fun Session.hentPølsepakker(fnr: String) =
        run(queryOf(HENT_PØLSEPAKKER, mapOf("fnr" to fnr)).map { rad ->
            rad.string("yrkesaktivitetidentifikator") to objectMapper.readValue<Personrad>(rad.string("data"))
        }.asList)
    private fun TransactionalSession.opprettPerson(fnr: String, oppretting: () -> List<YrkesaktivitetDto>): Long {
        return checkNotNull(run(queryOf(OPPRETT_PERSON, mapOf("fnr" to fnr)).map { rad -> rad.long("id") }.asSingle)) {
            "Forventet å finne person eller opprette en ny"
        }.also {  personId ->
            oppretting()
                .filter { it.rader.isNotEmpty() }
                .forEach { lagre(personId, it.yrkesaktivitetidentifikator, it.rader, UKJENT_UUID, UUID.randomUUID(), "{}") }
        }
    }

    private data class Personrad(
        val rader: List<PølseradDbDto>
    ) {
        companion object {
            fun fraModellDto(rader: List<PølseradDto>) = Personrad(
                rader = rader.map { rad ->
                    PølseradDbDto(
                        kildeTilRad = rad.kildeTilRad,
                        pølser = rad.pølser.map { pølse ->
                            PølseradDbDto.PølseDbDto(
                                vedtaksperiodeId = pølse.vedtaksperiodeId,
                                generasjonId = pølse.behandlingId,
                                status = when (pølse.status) {
                                    Pølsestatus.ÅPEN -> PølseradDbDto.PølseDbDto.PølseDbstatus.ÅPEN
                                    Pølsestatus.LUKKET -> PølseradDbDto.PølseDbDto.PølseDbstatus.LUKKET
                                    Pølsestatus.FORKASTET -> PølseradDbDto.PølseDbDto.PølseDbstatus.FORKASTET
                                },
                                kilde = pølse.kilde
                            )
                        }
                    )
                }
            )
        }

        fun tilModellDto() = rader.map { rad ->
            PølseradDto(
                kildeTilRad = rad.kildeTilRad,
                pølser = rad.pølser.map { pølse ->
                    PølseDto(
                        vedtaksperiodeId = pølse.vedtaksperiodeId,
                        behandlingId = pølse.generasjonId,
                        status = when (pølse.status) {
                            PølseradDbDto.PølseDbDto.PølseDbstatus.ÅPEN -> Pølsestatus.ÅPEN
                            PølseradDbDto.PølseDbDto.PølseDbstatus.LUKKET -> Pølsestatus.LUKKET
                            PølseradDbDto.PølseDbDto.PølseDbstatus.FORKASTET -> Pølsestatus.FORKASTET
                        },
                        kilde = pølse.kilde
                    )
                }
            )
        }

        data class PølseradDbDto(
            val pølser: List<PølseDbDto>,
            val kildeTilRad: UUID
        ) {
            data class PølseDbDto(
                val vedtaksperiodeId: UUID,
                // TODO: migrer navnet i json lagret i databasen med <behandlingId>
                val generasjonId: UUID,
                val status: PølseDbstatus,
                // tingen som gjorde at behandlingen ble opprettet
                val kilde: UUID
            ) {
                enum class PølseDbstatus { ÅPEN, LUKKET, FORKASTET }
            }
        }
    }
    private companion object {
        private val logg = LoggerFactory.getLogger(this::class.java)
        private val objectMapper = jacksonObjectMapper()
        private val UKJENT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

        @Language("PostgreSQL")
        private const val HENT_PØLSEPAKKE_MED_LÅS = """SELECT data FROM polsepakke WHERE yrkesaktivitetidentifikator = :yid AND person_id = :pid FOR UPDATE;"""
        @Language("PostgreSQL")
        private const val HENT_PØLSEPAKKER = """SELECT yrkesaktivitetidentifikator, data FROM polsepakke WHERE person_id = (SELECT id FROM person WHERE fnr=:fnr);"""

        @Language("PostgreSQL")
        private const val KOPIER_PØLSEPAKKE = """
            INSERT INTO polsepakke_historikk (person_id, yrkesaktivitetidentifikator, hendelse_id, kilde_id, data, opprettet)
            SELECT person_id, yrkesaktivitetidentifikator, hendelse_id, kilde_id, data, oppdatert
            FROM polsepakke p
            WHERE p.person_id = :personId AND p.yrkesaktivitetidentifikator = :yid
        """

        @Language("PostgreSQL")
        private const val OPPRETT_PØLSEPAKKE = """
            INSERT INTO polsepakke (person_id, yrkesaktivitetidentifikator, hendelse_id, kilde_id, data)
            VALUES (:personId, :yid, :hendelseId, :kildeId, CAST(:data AS json))
            ON CONFLICT (person_id, yrkesaktivitetidentifikator) DO UPDATE SET 
                hendelse_id = EXCLUDED.hendelse_id,
                kilde_id = EXCLUDED.kilde_id,
                data = EXCLUDED.data
                
        """

        @Language("PostgreSQL")
        private const val HENT_PERSON_MED_LÅS = """SELECT id FROM person WHERE fnr = :fnr FOR UPDATE"""

        @Language("PostgreSQL")
        private const val OPPRETT_PERSON = """INSERT INTO person (fnr) VALUES (:fnr) RETURNING id"""

        @Language("PostgreSQL")
        private const val HENT_HENDELSE = """SELECT EXISTS(SELECT 1 FROM hendelse WHERE meldingsreferanse_id = :id) as finnes"""

        @Language("PostgreSQL")
        private const val OPPRETT_HENDELSE = """INSERT INTO hendelse (meldingsreferanse_id, data) VALUES (:id, :data) RETURNING id"""
    }
}