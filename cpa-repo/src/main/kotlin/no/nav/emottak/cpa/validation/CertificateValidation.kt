package no.nav.emottak.cpa.validation

import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.HttpClientUtil
import no.nav.emottak.util.cert.CRLChecker
import no.nav.emottak.util.cert.CRLHandler
import no.nav.emottak.util.cert.CertificateValidationException
import no.nav.emottak.util.cert.SertifikatValidering
import java.security.cert.X509Certificate


val crlChecker = CRLChecker(
    runBlocking {
        CRLHandler(HttpClientUtil.client).updateCRLs()
    }
)

val sertifikatValidering = SertifikatValidering(crlChecker)

@Throws(CertificateValidationException::class)
fun X509Certificate.validate() {
    sertifikatValidering.validateCertificate(this)
}