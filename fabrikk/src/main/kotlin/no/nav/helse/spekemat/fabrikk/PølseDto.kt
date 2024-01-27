package no.nav.helse.spekemat.fabrikk

import java.util.*

data class PølseDto(
    val vedtaksperiodeId: UUID,
    val generasjonId: UUID,
    val status: Pølsestatus,
    // tingen som gjorde at generasjonen ble opprettet
    val kilde: UUID
)