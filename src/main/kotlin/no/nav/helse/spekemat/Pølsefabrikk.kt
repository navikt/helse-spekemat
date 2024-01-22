package no.nav.helse.spekemat

import java.time.LocalDate
import java.util.*

class Pølsefabrikk {

    private val pakken: MutableList<List<Pølse>> = mutableListOf()
    private val gjeldendeRad = mutableListOf<Pølse>()

    fun nyPølse(pølse: Pølse) {
        gjeldendeRad.add(pølse)
    }

    fun pakke(): List<List<Pølse>> {
        val resultat = pakken.toList().toMutableList()
        resultat.add(0, gjeldendeRad.sortedByDescending { it.fom })
        return resultat
    }
}

data class Pølse(val id: UUID, val fom: LocalDate, val tom: LocalDate)