package no.nav.emottak.cpa.validation

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import no.nav.emottak.cpa.configuration.Nhn
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.cpa.nhn.adresseregisteret.model.Certificate
import no.nav.emottak.cpa.nhn.adresseregisteret.model.CommunicationParty
import no.nav.emottak.cpa.persistence.CommunicationPartyCacheRepository

class AdresseregisterValidator(
    val httpClient: HttpClient,
    nhnConfig: Nhn = config.nhn,
    private val cache: CommunicationPartyCacheRepository? = null
) {
    private val cpapiCommunicationPartyUrl = nhnConfig.cpApiCommunicationPartyUrl
    private val cpapiCertificateUrl = nhnConfig.cpApiCertificateUrl
    val cpapiActive = nhnConfig.cpApiActive

    suspend fun getCommunicationParty(herId: String): CommunicationParty {
        cache?.findCommunicationParty(herId.toLong())?.let { return it }
        return httpClient.getDataFromArAPI("$cpapiCommunicationPartyUrl/$herId")
            .body<CommunicationParty>()
            .also { cache?.upsertCommunicationParty(herId.toLong(), it) }
    }

    suspend fun getSigningCertificate(herId: String): Certificate {
        cache?.findSigningCertificate(herId.toLong())?.let { return it }
        return httpClient.getDataFromArAPI("$cpapiCertificateUrl/$herId/signing")
            .body<Certificate>()
            .also { cache?.upsertSigningCertificate(herId.toLong(), it) }
    }

    suspend fun getEncryptionCertificate(herId: String): Certificate {
        cache?.findEncryptionCertificate(herId.toLong())?.let { return it }
        return httpClient.getDataFromArAPI("$cpapiCertificateUrl/$herId/encryption")
            .body<Certificate>()
            .also { cache?.upsertEncryptionCertificate(herId.toLong(), it) }
    }

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
