package no.nav.emottak.cpa.validation

import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.HttpClientUtil
import no.nav.emottak.util.cert.CRLChecker
import no.nav.emottak.util.cert.CRLRetriever
import no.nav.emottak.util.cert.CertificateValidationException
import no.nav.emottak.util.cert.SertifikatValidering
import java.security.cert.X509Certificate

val crlChecker = CRLChecker(
    runBlocking {
        CRLRetriever(HttpClientUtil.client).updateAllCRLs()
    }
)

val sertifikatValidering = SertifikatValidering(crlChecker)

@Throws(CertificateValidationException::class)
fun X509Certificate.validate() {
    sertifikatValidering.validateCertificate(this)
}
