package no.nav.emottak.utbetaling

import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnBrukersUtbetalteYtelser
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListe
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListeBaksystemIkkeTilgjengelig
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListeBrukerIkkeFunnet
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListeFeil
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListeIngenTilgangTilEnEllerFlereYtelser
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListeUgyldigDato
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListeUgyldigKombinasjonBrukerIdOgBrukertype
import no.nav.emottak.cxf.ServiceBuilder
import no.nav.emottak.util.getEnvVar
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import javax.xml.namespace.QName

class InntektsForesporselClient {

    val log = LoggerFactory.getLogger(InntektsForesporselClient::class.java)

    val utbetalingObjectFactory: no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.ObjectFactory =
        no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.ObjectFactory()

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

    fun behandleInntektsforesporsel(payloadBytes: ByteArray): Any {
        val msgHead = utbetalingXmlMarshaller.unmarshal(String(payloadBytes), MsgHead::class.java)
        val melding = msgHead.document.map { it.refDoc.content.any }
            .also { if (it.size > 1) log.warn("Inntektsforesporsel refdoc har size >1") }
            .first().also { if (it.size > 1) log.warn("Inntektsforesporsel content har size >1") }.first()
        try {
            val response: Any = when (melding) {
                is FinnUtbetalingListe -> inntektsforesporselSoapEndpoint.finnUtbetalingListe(melding.request)
                is FinnBrukersUtbetalteYtelser -> inntektsforesporselSoapEndpoint.finnBrukersUtbetalteYtelser(melding.request)
                else -> throw IllegalStateException("Ukjent meldingstype. Classname: " + melding.javaClass.name)
            }
            return marshal(response).toByteArray()
        } catch (utbetalError: Throwable) {
            log.info("Handling inntektsforesporsel error: " + utbetalError.message)
            val feil = FinnUtbetalingListeFeil()
            return when (utbetalError) {
                is FinnUtbetalingListeBrukerIkkeFunnet
                -> feil.finnUtbetalingListebrukerIkkeFunnet = utbetalError.faultInfo
                is FinnUtbetalingListeBaksystemIkkeTilgjengelig
                -> feil.finnUtbetalingListebaksystemIkkeTilgjengelig = utbetalError.faultInfo
                is FinnUtbetalingListeIngenTilgangTilEnEllerFlereYtelser
                -> feil.finnUtbetalingListeingenTilgangTilEnEllerFlereYtelser = utbetalError.faultInfo
                is FinnUtbetalingListeUgyldigDato
                -> feil.finnUtbetalingListeugyldigDato = utbetalError.faultInfo
                is FinnUtbetalingListeUgyldigKombinasjonBrukerIdOgBrukertype
                -> feil.finnUtbetalingListeugyldigKombinasjonBrukerIdOgBrukertype = utbetalError.faultInfo
                else ->
                    throw utbetalError.also { log.error("Ukjent feiltype: " + it.message, it) }
            }
        }
    }
}

val inntektsforesporselSoapEndpoint: no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.Utbetaling =
    ServiceBuilder(
        no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.Utbetaling::class.java
    )
        .withAddress(getEnvVar("UTBETALING_URL", "https://ytelser-rest-proxy.dev.intern.nav.no/Utbetaling"))
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
