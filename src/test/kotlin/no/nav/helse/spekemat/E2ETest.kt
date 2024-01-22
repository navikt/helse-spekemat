package no.nav.helse.spekemat

import com.zaxxer.hikari.HikariDataSource
import kotliquery.sessionOf
import kotliquery.using
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

class E2ETest {
    @Test
    fun foo() {
        using(sessionOf(Database.dataSource)) {
            assertTrue(true)
        }
    }
}

private object Database {
    private val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:15").apply {
            withCreateContainerCmdModifier { command -> command.withName("spekemat") }
            withReuse(true)
            withLabel("app-navn", "spekemat")
            start()
        }
    }

    val dataSource: HikariDataSource by lazy {
        HikariDataSource().apply {
            username = instance.username
            password = instance.password
            jdbcUrl = instance.jdbcUrl
            initializationFailTimeout = 10000
        }
            .also(::migrate)
    }

    private fun migrate(ds: DataSource) {
        Flyway.configure()
            .dataSource(ds)
            .validateMigrationNaming(true)
            .cleanDisabled(false)
            .load()
            .also { it.clean() }
            .migrate()
    }
}