package no.nav.helse.spekemat.fabrikk

import java.util.*

data class PølseradDto(
    val pølser: List<PølseDto>,
    val kildeTilRad: UUID,
    val sisteKildeId: UUID
)