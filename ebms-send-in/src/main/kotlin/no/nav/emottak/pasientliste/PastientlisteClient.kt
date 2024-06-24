package no.nav.emottak.pasientliste

import no.nav.emottak.cxf.ServiceBuilder
import no.nav.emottak.ebms.log
import no.nav.emottak.frikort.xmlMarshaller
import no.nav.emottak.util.getEnvVar
import no.nav.tjeneste.ekstern.frikort.v1.FrikortV1Port
import no.nav.tjeneste.ekstern.frikort.v1.types.FrikortsporringResponse
import no.nav.tjeneste.ekstern.frikort.v1.types.ObjectFactory
import no.trygdeetaten.xml.eiff._1.EIFellesformat
import javax.xml.namespace.QName

val pasientlisteClient = pasientlisteEndpoint()
private val pasientlisteObjectFactory = ObjectFactory()

fun pasientlisteEndpoint(): FrikortV1Port =
    ServiceBuilder(FrikortV1Port::class.java)
        .withAddress(getEnvVar("PASIENTLISTE_URL", "https://wasapp-local.adeo.no/nav-frikort/tjenestereksterne"))
        .withServiceName(QName("http://nav.no/tjeneste/ekstern/frikort/v1", "Frikort_v1Service"))
        .withEndpointName(QName("http://nav.no/tjeneste/ekstern/frikort/v1", "Frikort_v1Port"))
        .build()
        .withBasicSecurity()
        .get()

fun createRequest(fellesformat: EIFellesformat): FrikortsporringResponse = pasientlisteClient.frikortsporring(
    pasientlisteObjectFactory.createFrikortsporringRequest().also { it.eiFellesformat = fellesformat }
).also {
    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        log.info("Send in Frikort response " + xmlMarshaller.marshal(it))
    }
}
