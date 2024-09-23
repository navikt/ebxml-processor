package no.nav.emottak.frikort

import no.nav.emottak.cxf.ServiceBuilder
import no.nav.emottak.ebms.log
import no.nav.emottak.fellesformat.FellesFormatXmlMarshaller
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.isProdEnv
import no.nav.tjeneste.ekstern.frikort.v1.FrikortV1Port
import no.nav.tjeneste.ekstern.frikort.v1.types.FrikortsporringResponse
import no.nav.tjeneste.ekstern.frikort.v1.types.ObjectFactory
import no.trygdeetaten.xml.eiff._1.EIFellesformat
import javax.xml.namespace.QName

val frikortClient = frikortEndpoint()
private val frikortObjectFactory = ObjectFactory()

fun frikortEndpoint(): FrikortV1Port =
    ServiceBuilder(FrikortV1Port::class.java)
        .withAddress(getEnvVar("FRIKORT_URL", "https://wasapp-local.adeo.no/nav-frikort/tjenestereksterne"))
        .withWsdl("classpath:frikort_v1.wsdl")
        .withServiceName(QName("http://nav.no/tjeneste/ekstern/frikort/v1", "Frikort_v1Service"))
        .withEndpointName(QName("http://nav.no/tjeneste/ekstern/frikort/v1", "Frikort_v1Port"))
        .build()
        .withBasicSecurity()
        .get()

fun frikortsporring(fellesformat: EIFellesformat): FrikortsporringResponse {
    if (!isProdEnv()) {
        log.info("Sending in frikortsporring request with body: " + FellesFormatXmlMarshaller.marshal(fellesformat))
    }

    return frikortClient.frikortsporring(
        frikortObjectFactory.createFrikortsporringRequest().also { it.eiFellesformat = fellesformat }
    ).also {
        if (!isProdEnv()) {
            log.info("Send in Frikort response " + FellesFormatXmlMarshaller.marshal(it))
        }
    }
}
