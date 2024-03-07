package no.nav.helse.spekemat.fabrikk

import java.util.*

data class PølseDto(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val status: Pølsestatus,
    // tingen som gjorde at behandlingen ble opprettet
    val kilde: UUID
)