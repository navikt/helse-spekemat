package no.nav.helse.spekemat.slakter

import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.result_object.Result
import com.github.navikt.tbd_libs.result_object.ok
import io.mockk.every
import io.mockk.mockk
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.time.LocalDateTime
import java.util.*

class PølsetjenestenTest {
    private companion object {
        const val FNR = "12345678911"
        const val ORGN = "987654321"
    }

    private val azureTokenProvider = object : AzureTokenProvider {
        override fun bearerToken(scope: String) = AzureToken("liksom-token", LocalDateTime.MAX).ok()
        override fun onBehalfOfToken(scope: String, token: String): Result<AzureToken> {
            throw NotImplementedError("ikke implementert i mock")
        }
    }
    private var httpClientMock = mockk<HttpClient>()
    private val pølsetjeneste = Pølsetjenesten(httpClientMock, azureTokenProvider, "scope-til-spekemat")

    @Test
    fun `personen finnes ikke`() {
        @Language("JSON")
        val errorBody = """{
  "type": "urn:error:not_found",
  "title": "Not Found",
  "status": 404,
  "detail":"Ikke funnet: Pølse finnes ikke: Ingen pølse registrert"
}"""
        every { httpClientMock.send<String>(any(), any()) } returns MockHttpResponse(errorBody, 404, mapOf("callId" to "liksom call id"))

        val error = assertThrows<IkkeFunnetException> {
            pølsetjeneste.behandlingForkastet(FNR, ORGN, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "{}")
        }
        assertNotNull(error.feilmeldingResponse)
        assertTrue(error.feilmeldingResponse!!.detail!!.contains("Ikke funnet"))
    }
}