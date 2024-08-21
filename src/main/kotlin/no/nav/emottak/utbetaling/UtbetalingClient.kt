package no.nav.emottak.utbetaling

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnBrukersUtbetalteYtelserRequest
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnBrukersUtbetalteYtelserResponse
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnUtbetalingListeRequest
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnUtbetalingListeResponse
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnBrukersUtbetalteYtelser
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListe
import no.nav.emottak.cxf.ServiceBuilder
import no.nav.emottak.util.getEnvVar
import org.slf4j.LoggerFactory
import java.io.FileInputStream
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

    fun behandleInntektsforesporsel(payloadBytes: ByteArray): ByteArray {
        val msgHead = xmlMarshaller.unmarshal(String(payloadBytes), MsgHead::class.java)
        val melding = msgHead.document.map { it.refDoc.content.any }.also { if (it.size > 1) log.warn("Inntektsforesporsel refdoc har size >1") }
            .first().also { if (it.size > 1) log.warn("Inntektsforesporsel content har size >1") }.first()
        val response: Any = when (melding) {
            is FinnUtbetalingListe -> inntektsforesporselSoapEndpoint.finnUtbetalingListe(melding.request)
            is FinnBrukersUtbetalteYtelser -> inntektsforesporselSoapEndpoint.finnBrukersUtbetalteYtelser(melding.request)
            else -> throw IllegalStateException("Ukjent meldingstype. Classname: " + melding.javaClass.name)
        }
        return marshal(response).toByteArray()
    }

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

    suspend fun finnUtbetalingListe(payloadBytes: ByteArray): FinnUtbetalingListeResponse {
        val unmarshal = xmlMarshaller.unmarshal(String(payloadBytes), MsgHead::class.java)
//        unmarshal.document.map { doc -> doc.refDoc.content.any.find {
//            rootElement -> rootElement is Element } as Element //&& rootElement.localName.equals("finnUtbetalingListe") }
//        }.first {
//            when((it).localName) {
//                "finnUtbetalingListe" ->
//                    unmarshal(it.toString(), )
//                else
//            }
//        }

        val response = withContext(Dispatchers.IO) {
            httpClient.post(UTBETAL_SOAP_ENDPOINT) {
                setBody(xmlMarshaller.marshal(payloadBytes))
            }.bodyAsText()
        }
        return xmlMarshaller.unmarshal(response, FinnUtbetalingListeResponse::class.java)
    }

    suspend fun finnBrukersUtbetalteYtelser(payloadBytes: ByteArray): FinnBrukersUtbetalteYtelserResponse {
        val response = withContext(Dispatchers.IO) {
            httpClient.post(UTBETAL_SOAP_ENDPOINT) {
                setBody(xmlMarshaller.marshal(payloadBytes))
            }.bodyAsText()
        }
        return xmlMarshaller.unmarshal(response, FinnBrukersUtbetalteYtelserResponse::class.java)
    }

    suspend fun finnUtbetalingListeMedHttpClient(p0: FinnUtbetalingListeRequest): FinnUtbetalingListeResponse {
        val response = withContext(Dispatchers.IO) {
            httpClient.post(UTBETAL_SOAP_ENDPOINT) {
                setBody(xmlMarshaller.marshal(p0))
            }.bodyAsText()
        }
        return xmlMarshaller.unmarshal(response, FinnUtbetalingListeResponse::class.java)
    }

    suspend fun finnBrukersUtbetalteYtelserMedHttpClient(p0: FinnBrukersUtbetalteYtelserRequest): FinnBrukersUtbetalteYtelserResponse {
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
        .withWsdl(
            "classpath:no.nav.ekstern.virkemiddelokonomi/tjenester/utbetaling/utbetaling.wsdl"
            // "classpath:no.nav.ekstern.virkemiddelokonomi/tjenester.utbetaling/utbetaling.wsdl"
        )
        .withServiceName(QName("http://nav.no/ekstern/virkemiddelokonomi/tjenester/utbetaling/v1", "Utbetaling"))
        .withEndpointName(QName("http://nav.no/ekstern/virkemiddelokonomi/tjenester/utbetaling/v1", "UtbetalingPort"))
        .build()
        .withUserNameToken(
            when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
                "local" -> "testUserName"
                else -> String(FileInputStream("/secret/serviceuser/username").readAllBytes())
            },
            when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
                "local" -> "testPassword"
                else -> String(FileInputStream("/secret/serviceuser/password").readAllBytes())
            }
        )
        .get()
