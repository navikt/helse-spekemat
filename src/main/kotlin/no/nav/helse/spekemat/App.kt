package no.nav.helse.spekemat

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.time.Duration

private val logg = LoggerFactory.getLogger("no.nav.helse.spekemat.App")
private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val env = System.getenv()
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", env.getValue("DATABASE_HOST"), env.getValue("DATABASE_PORT"), env.getValue("DATABASE_DATABASE"))
        username = env.getValue("DATABASE_USERNAME")
        password = env.getValue("DATABASE_PASSWORD")
        maximumPoolSize = 2
        initializationFailTimeout = Duration.ofMinutes(5).toMillis()
    }
    val dataSource = {
        hikariConfig.maximumPoolSize = 1
        HikariDataSource(hikariConfig)
    }
    RapidApplication.create(env)
        .apply {
            logg.info("Hei, er verden klar for pølser?")
            register(object : RapidsConnection.StatusListener {
                override fun onStartup(rapidsConnection: RapidsConnection) {
                    migrate(hikariConfig)
                }
            })
        }
        .start()
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