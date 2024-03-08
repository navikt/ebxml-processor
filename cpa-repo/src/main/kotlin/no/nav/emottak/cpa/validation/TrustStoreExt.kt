package no.nav.emottak.cpa.validation

import no.nav.emottak.crypto.KeyStore
import no.nav.emottak.util.isSelfSigned
import java.security.cert.X509Certificate

fun KeyStore.getTrustedRootCerts(): Set<X509Certificate> {
    return this.getPublicCertificates().values.filter { isSelfSigned(it) }.toSet()
}

internal fun KeyStore.getIntermediateCerts(): Set<X509Certificate> {
    return this.getPublicCertificates().values.filter { !isSelfSigned(it) }.toSet()
}
