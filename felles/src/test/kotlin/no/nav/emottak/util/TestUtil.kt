package no.nav.emottak.util

class TestUtil {
    companion object {
        val validCertificate = createX509Certificate(this::class.java.classLoader.getResource("certificates/valid.qcevident.ca23.ssl.buypass.no.cer").readBytes())
        val expiredCertificate = createX509Certificate(this::class.java.classLoader.getResource("certificates/expired.qcevident.ca23.ssl.buypass.no.cer").readBytes())
        val revokedCertificate = createX509Certificate(this::class.java.classLoader.getResource("certificates/revoked.qcevident.ca23.ssl.buypass.no.cer").readBytes())
        val selfSignedCertificate = createX509Certificate(this::class.java.classLoader.getResource("certificates/cert_selfsigned.pem").readBytes())

        val crlFile = createCRLFile(this::class.java.classLoader.getResource("crl/BPClass3CA2.crl").readBytes())
    }
}
