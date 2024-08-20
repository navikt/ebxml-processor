package no.nav.emottak.utbetaling

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnBrukersUtbetalteYtelserRequest
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnBrukersUtbetalteYtelserResponse
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnUtbetalingListeRequest
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnUtbetalingListeResponse
import no.nav.emottak.cxf.ServiceBuilder
import no.nav.emottak.util.getEnvVar
import org.slf4j.LoggerFactory
import javax.xml.namespace.QName

class InntektsForesporselClient {

    val log = LoggerFactory.getLogger(InntektsForesporselClient::class.java)

    val YRP_URL_TEST = "https://ytelser-rest-proxy.intern.dev.nav.no"
    val YRP_URL_PROD = "https://ytelser-rest-proxy.intern.nav.no"
    val RESOLVED_UTBETAL_URL =
        when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
            "local" -> YRP_URL_TEST
            "dev-fss" -> YRP_URL_TEST
            "prod-fss" -> YRP_URL_PROD
            else -> YRP_URL_TEST
        }
    val UTBETAL_SOAP_ENDPOINT = RESOLVED_UTBETAL_URL + "/Utbetaling"

    val httpClient = HttpClient(CIO)

    fun sendInntektsforesporsel(payloadBytes: ByteArray): suspend () -> ByteArray {
        log.info("Sender inntektsforespørsel til $UTBETAL_SOAP_ENDPOINT:\n" + String(payloadBytes))
        return {
            httpClient.post(UTBETAL_SOAP_ENDPOINT) {
                setBody(payloadBytes)
            }.readBytes().also {
                log.info("(DEBUG) Inntektsforesporsel response: " + String(it)) // TODO delete debug logging
            }
        }
    }

    suspend fun finnUtbetalingListe(p0: FinnUtbetalingListeRequest): FinnUtbetalingListeResponse {
        val response = withContext(Dispatchers.IO) {
            httpClient.post(UTBETAL_SOAP_ENDPOINT) {
                setBody(xmlMarshaller.marshal(p0))
            }.bodyAsText()
        }
        return xmlMarshaller.unmarshal(response, FinnUtbetalingListeResponse::class.java)
    }

    suspend fun finnBrukersUtbetalteYtelser(p0: FinnBrukersUtbetalteYtelserRequest): FinnBrukersUtbetalteYtelserResponse {
        val response = withContext(Dispatchers.IO) {
            httpClient.post(UTBETAL_SOAP_ENDPOINT) {
                setBody(xmlMarshaller.marshal(p0))
            }.bodyAsText()
        }
        return xmlMarshaller.unmarshal(response, FinnBrukersUtbetalteYtelserResponse::class.java)
    }
}

val inntektsforesporselSoapEndpoint: no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.Utbetaling =
    ServiceBuilder(
        no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.Utbetaling::class.java
    )
        .withAddress(getEnvVar("UTBETALING_URL", "https://ytelser-rest-proxy.intern.nav.no/Utbetaling"))
        .withWsdl("classpath:no/nav/ekstern/virkemiddelokonomi/utbetaling/utbetaling.wsdl")
        .withServiceName(QName("http://nav.no/ekstern/virkemiddelokonomi/tjenester/utbetaling/v1", "Utbetaling"))
        .withEndpointName(QName("http://nav.no/ekstern/virkemiddelokonomi/tjenester/utbetaling/v1", "UtbetalingPort"))
        .build()
        // .withBasicSecurity()
        .get()
