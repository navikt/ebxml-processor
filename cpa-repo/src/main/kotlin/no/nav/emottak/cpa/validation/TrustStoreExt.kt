package no.nav.emottak.cpa.validation

import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.util.isSelfSigned
import java.security.cert.X509Certificate

fun KeyStoreManager.getTrustedRootCerts(): Set<X509Certificate> {
    return this.getPublicCertificates().values.filter { isSelfSigned(it) }.toSet().onEach {
        log.info("Loaded root certificate: <${it.serialNumber.toString(16)}> <${it.subjectX500Principal.name}> <${it.issuerX500Principal}>")
    }
}

internal fun KeyStoreManager.getIntermediateCerts(): Set<X509Certificate> {
    return this.getPublicCertificates().values.filter { !isSelfSigned(it) }.toSet().onEach {
        log.info("Loaded intermediate certificate: <${it.serialNumber.toString(16)}> <${it.subjectX500Principal.name}> <${it.issuerX500Principal}>")
    }
}
