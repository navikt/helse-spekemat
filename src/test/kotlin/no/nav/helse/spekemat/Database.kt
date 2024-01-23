package no.nav.helse.spekemat

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

object Database {
    private lateinit var flyway: Flyway

    private val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:15").apply postgres@ {
            withCreateContainerCmdModifier { command -> command.withName("spekemat") }
            withReuse(true)
            withLabel("app-navn", "spekemat")
            start()

            val ds = HikariDataSource(HikariConfig().apply {
                jdbcUrl = this@postgres.jdbcUrl
                username = this@postgres.username
                password = this@postgres.password
                initializationFailTimeout = 10000
            })
            flyway = Flyway.configure()
                .dataSource(ds)
                .validateMigrationNaming(true)
                .cleanDisabled(false)
                .load()

            flyway.migrate()
        }
    }

    val dataSource: HikariDataSource by lazy {
        HikariDataSource().apply {
            username = instance.username
            password = instance.password
            jdbcUrl = instance.jdbcUrl

        }
    }

    fun reset() {
        flyway.clean()
        flyway.migrate()
    }
}