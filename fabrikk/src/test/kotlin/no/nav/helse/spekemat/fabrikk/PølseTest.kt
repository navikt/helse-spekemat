package no.nav.helse.spekemat.fabrikk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import java.util.*

abstract class PølseTest {
    protected lateinit var fabrikk: Pølsefabrikk

    @BeforeEach
    fun setup() {
        fabrikk = Pølsefabrikk()
    }

    private fun pølse(
        vedtaksperiodeId: UUID,
        kilde: UUID = UUID.randomUUID(),
        status: Pølsestatus = Pølsestatus.ÅPEN
    ) = UUID.randomUUID().let { behandlingId -> Pølse(vedtaksperiodeId, behandlingId, behandlingId, status, kilde) }

    protected infix fun LocalDate.til(tom: LocalDate) = pølse(UUID.randomUUID())
    protected infix fun Pølse.som(vedtaksperiodeId: UUID) = this.copy(vedtaksperiodeId = vedtaksperiodeId)
    protected fun Pølse.nyBehandling(behandlingId: UUID = UUID.randomUUID(), kilde: UUID = UUID.randomUUID()) = fordi(kilde).copy(behandlingId = behandlingId, generasjonId = behandlingId)
    protected infix fun Pølse.fordi(kilde: UUID) = this.copy(kilde = kilde)

    protected fun Pølse.lukket() = this.copy(status = Pølsestatus.LUKKET)
    protected fun Pølse.forkastet() = this.copy(status = Pølsestatus.FORKASTET)

    protected val mandag = LocalDate.of(2018, 1, 1)
    protected val Int.januar get() = mandag.withDayOfMonth(this).withMonth(1)

    protected fun assertEquals(expected: Set<Pølse>, actual: PølseradDto) {
        assertEquals(expected, actual.pølser)
    }
    protected fun assertEquals(expected: Set<Pølse>, actual: List<PølseDto>) {
        assertEquals(expected.size, actual.size)
        val ingenMatch = expected.map { it.dto() }.filterNot { it in actual }
        assertEquals(emptyList<PølseDto>(), ingenMatch) { "Det er pølser som ikke finnes i actual" }
    }

    protected fun Pølsefabrikk.lukketPølse(pølse: Pølse) {
        this.oppdaterPølse(pølse.vedtaksperiodeId, pølse.behandlingId, Pølsestatus.LUKKET)
    }

    protected fun Pølsefabrikk.pølseForkastet(pølse: Pølse) {
        this.oppdaterPølse(pølse.vedtaksperiodeId, pølse.behandlingId, Pølsestatus.FORKASTET)
    }
}