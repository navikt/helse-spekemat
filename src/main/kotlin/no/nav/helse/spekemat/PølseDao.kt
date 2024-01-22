package no.nav.helse.spekemat

import kotliquery.sessionOf
import javax.sql.DataSource

fun interface DatasourceProvider {
    fun getDataSource(): DataSource
}

class PølseDao(private val dataSource: DatasourceProvider) {

    fun slett(fnr: String) {
        sessionOf(dataSource.getDataSource()).use {

        }
    }
}