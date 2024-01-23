package no.nav.helse.spekemat

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import net.logstash.logback.argument.StructuredArguments.kv
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

    fun hent(fnr: String, yrkesaktivitetidentifikator: String) = hentPerson(fnr, yrkesaktivitetidentifikator)?.let {
        Pølsefabrikk.gjenopprett(it.rader)
    }

    private fun hentPerson(fnr: String, yrkesaktivitetidentifikator: String) =
        sessionOf(dataSource.getDataSource()).use { session ->
            session.run(queryOf(HENT_PØLSEPAKKE, mapOf("fnr" to fnr, "yid" to yrkesaktivitetidentifikator)).map { rad ->
                objectMapper.readValue<Personrad>(rad.string("data"))
            }.asSingle)
        }

    fun opprett(
        fnr: String,
        yrkesaktivitetidentifikator: String?,
        resultat: List<PølseradDto>,
        kildeId: UUID,
        meldingsreferanseId: UUID,
        hendelsedata: String
    ) {
        sessionOf(dataSource.getDataSource()).use {
            it.transaction { session ->
                if (session.hendelseHåndtertFør(meldingsreferanseId)) return logg.info("hendelsen {} er håndtert fra før", kv("meldingsreferanseId", meldingsreferanseId))

                val personId = session.lagEllerHentPersonId(fnr)
                val hendelseId = session.opprettHendelse(meldingsreferanseId, hendelsedata)
                val pølsepakkejson = objectMapper.writeValueAsString(Personrad(rader = resultat))

                session.run(queryOf(KOPIER_PØLSEPAKKE, mapOf("personId" to personId)).asExecute)
                session.run(queryOf(OPPRETT_PØLSEPAKKE, mapOf(
                    "personId" to personId,
                    "yid" to yrkesaktivitetidentifikator,
                    "hendelseId" to hendelseId,
                    "kildeId" to kildeId,
                    "data" to pølsepakkejson
                )).asExecute)
            }
        }
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
    private fun TransactionalSession.lagEllerHentPersonId(fnr: String): Long {
        return hentPerson(fnr) ?: opprettPerson(fnr)
    }
    private fun TransactionalSession.hentPerson(fnr: String): Long? {
        return run(queryOf(HENT_PERSON, mapOf("fnr" to fnr)).map { it.long("id") }.asSingle)
    }
    private fun TransactionalSession.opprettPerson(fnr: String): Long {
        return checkNotNull(run(queryOf(OPPRETT_PERSON, mapOf("fnr" to fnr)).map { rad -> rad.long("id") }.asSingle)) {
            "Forventet å finne person eller opprette en ny"
        }
    }

    private data class Personrad(
        val rader: List<PølseradDto>
    )
    private companion object {
        private val logg = LoggerFactory.getLogger(Pølse::class.java)
        private val objectMapper = jacksonObjectMapper()

        @Language("PostgreSQL")
        private const val HENT_PØLSEPAKKE = """SELECT data FROM polsepakke WHERE yrkesaktivitetidentifikator = :yid AND person_id = (SELECT id FROM person WHERE fnr=:fnr);"""

        @Language("PostgreSQL")
        private const val KOPIER_PØLSEPAKKE = """
            INSERT INTO polsepakke_historikk (person_id, yrkesaktivitetidentifikator, hendelse_id, kilde_id, data, opprettet)
            SELECT person_id, yrkesaktivitetidentifikator, hendelse_id, kilde_id, data, oppdatert
            FROM polsepakke p
            WHERE p.person_id = :personId
        """

        @Language("PostgreSQL")
        private const val OPPRETT_PØLSEPAKKE = """
            INSERT INTO polsepakke (person_id, yrkesaktivitetidentifikator, hendelse_id, kilde_id, data)
            VALUES (:personId, :yid, :hendelseId, :kildeId, :data)
            ON CONFLICT (person_id, yrkesaktivitetidentifikator) DO UPDATE SET 
                hendelse_id = EXCLUDED.hendelse_id,
                kilde_id = EXCLUDED.kilde_id,
                data = EXCLUDED.data
                
        """

        @Language("PostgreSQL")
        private const val HENT_PERSON = """SELECT id FROM person WHERE fnr = :fnr"""

        @Language("PostgreSQL")
        private const val OPPRETT_PERSON = """INSERT INTO person (fnr) VALUES (:fnr) RETURNING id"""

        @Language("PostgreSQL")
        private const val HENT_HENDELSE = """SELECT EXISTS(SELECT 1 FROM hendelse WHERE meldingsreferanse_id = :id) as finnes"""

        @Language("PostgreSQL")
        private const val OPPRETT_HENDELSE = """INSERT INTO hendelse (meldingsreferanse_id, data) VALUES (:id, :data) RETURNING id"""
    }
}