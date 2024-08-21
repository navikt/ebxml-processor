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
import javax.xml.soap.SOAPElement
import javax.xml.soap.SOAPFactory

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
    fun createSecurityElement(username: String, password: String): SOAPElement {
        val WSSE_URI = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
        val WSSE_PW_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText"
        try {
            // https://www.ibm.com/docs/en/was/9.0.5?topic=authentication-username-token
            val soapFactory = SOAPFactory.newInstance()
            val usernameElement = soapFactory
                .createElement("Username", "wsse", WSSE_URI).addTextNode(username)
            val passwordElement = soapFactory
                .createElement("Password", "wsse", WSSE_URI).addTextNode(password)
            passwordElement
                .addAttribute(QName("Type"), WSSE_PW_TYPE)

            val usernameTokenElement = soapFactory
                .createElement("UsernameToken", "wsse", WSSE_URI)

            usernameTokenElement.addChildElement(usernameElement)
            usernameTokenElement.addChildElement(passwordElement)

            val securityElement = soapFactory.createElement(
                "Security",
                "wsse",
                WSSE_URI
            )
            securityElement.addChildElement(usernameTokenElement)
            return securityElement
        } catch (e: javax.xml.soap.SOAPException) {
            log.error("Feil i createSecurityElement", e)
            throw RuntimeException("Feil i generering av usernametoken")
        }
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
