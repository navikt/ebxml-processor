package no.nav.emottak.cpa.validation

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import no.nav.emottak.cpa.configuration.Nhn
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.cpa.model.Certificate
import no.nav.emottak.cpa.model.CommunicationParty

class AdresseregisterValidator(
    val httpClient: HttpClient,
    nhnConfig: Nhn = config.nhn
) {
    private val cpapiCommunicationPartyUrl = nhnConfig.cpApiCommunicationPartyUrl
    private val cpapiCertificateUrl = nhnConfig.cpApiCertificateUrl
    val cpapiActive = nhnConfig.cpApiActive

    suspend fun getCommunicationParty(herId: String): CommunicationParty =
        httpClient.getDataFromArAPI("$cpapiCommunicationPartyUrl/$herId").body<CommunicationParty>()

    suspend fun getSigningCertificate(herId: String): Certificate =
        httpClient.getDataFromArAPI("$cpapiCertificateUrl/$herId/signing").body<Certificate>()

    suspend fun getEncryptionCertificate(herId: String): Certificate =
        httpClient.getDataFromArAPI("$cpapiCertificateUrl/$herId/encryption").body<Certificate>()

    suspend fun getEdiAddress(herId: String): String? = getCommunicationParty(herId).ediAddress

    private suspend fun HttpClient.getDataFromArAPI(endpointUrl: String): HttpResponse = try {
        this.get(endpointUrl).also {
            when (it.status) {
                HttpStatusCode.OK -> log.debug("Data mottatt: ${it.bodyAsText()}")
                else -> log.warn("Feil ved oppslag: ${it.status}")
            }
        }
    } catch (e: Exception) {
        log.error("Kunne ikke koble til $endpointUrl: ${e.localizedMessage}", e)
        throw e
    }
}
