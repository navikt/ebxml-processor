package no.nav.emottak.frikort

import no.nav.emottak.cxf.ServiceBuilder
import no.nav.tjeneste.ekstern.frikort.v1.FrikortV1Port
import javax.xml.namespace.QName

val frikortClient = frikortEndpoint()

fun frikortEndpoint(): FrikortV1Port =
    ServiceBuilder(FrikortV1Port::class.java)
        .withAddress("https://wasapp-q1.adeo.no/nav-frikort/tjenestereksterne")
        .withWsdl("classpath:frikort_v1.wsdl")
        .withServiceName(QName("http://nav.no/tjeneste/ekstern/frikort/v1", "Frikort_v1Service"))
        .withEndpointName(QName("http://nav.no/tjeneste/ekstern/frikort/v1", "Frikort_v1Port"))
        .build()
        .withBasicSecurity()
        .get()
