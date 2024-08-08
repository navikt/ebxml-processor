package no.nav.emottak.pasientliste

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.emottak.ebms.log
import no.nav.emottak.frikort.xmlMarshaller
import no.nav.emottak.util.getEnvVar
import no.trygdeetaten.xml.eiff._1.EIFellesformat

// TODO: Delete if it works

// val pasientlisteClient = pasientlisteEndpoint()
// private val pasientlisteObjectFactory = ObjectFactory()
// private val log = LoggerFactory.getLogger("no.nav.emottak.pasientliste.PasientlisteClient")
//
// fun pasientlisteEndpoint(): FrikortV1Port =
//    ServiceBuilder(FrikortV1Port::class.java)
//        .withAddress(getEnvVar("PASIENTLISTE_URL", "https://wasapp-local.adeo.no/nav-frikort/tjenestereksterne"))
//        .withServiceName(QName("http://nav.no/tjeneste/ekstern/frikort/v1", "Frikort_v1Service"))
//        .withEndpointName(QName("http://nav.no/tjeneste/ekstern/frikort/v1", "Frikort_v1Port"))
//        .build()
//        .withBasicSecurity()
//        .get()

suspend fun pasientlisteRequest(fellesformat: EIFellesformat): EIFellesformat {
    val httpClient = HttpClient()

    val responseText =
        httpClient.post("https://wasapp-q1.adeo.no/nav-emottak-practitioner-web/remoting/httpreqhandler-practitioner") {
            setBody(fellesformat)
            contentType(ContentType.Application.Xml)
        }.bodyAsText()

    log.info("pasientlisteRequest response: $responseText")

    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        log.info("Send in pasientlisteRequest response " + xmlMarshaller.marshal(responseText))
    }

    return xmlMarshaller.unmarshal(responseText, EIFellesformat::class.java)
}
