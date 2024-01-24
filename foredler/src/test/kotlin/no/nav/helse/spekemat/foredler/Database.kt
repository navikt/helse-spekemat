package no.nav.helse.spekemat.foredler

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer

object Database {
    private lateinit var flyway: Flyway

    private val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:15").apply postgres@ {
            withCreateContainerCmdModifier { command -> command.withName("spekemat") }
            withReuse(true)
            withLabel("app-navn", "spekemat")
            DockerClientFactory.lazyClient().apply {
                this
                    .listContainersCmd()
                    .exec()
                    .filter { it.labels["app-navn"] == "spekemat" }
                    .forEach {
                        killContainerCmd(it.id).exec()
                        removeContainerCmd(it.id).withForce(true).exec()
                    }
            }
            start()
        }
    }

    val hikariConfig get() = HikariConfig().apply {
        jdbcUrl = instance.jdbcUrl
        username = instance.username
        password = instance.password
        initializationFailTimeout = 10000
    }

    val dataSource: HikariDataSource by lazy { HikariDataSource(hikariConfig) }

    fun reset() {
        flyway = Flyway.configure()
            .dataSource(dataSource)
            .validateMigrationNaming(true)
            .cleanDisabled(false)
            .load()
        flyway.clean()
        flyway.migrate()
    }
}