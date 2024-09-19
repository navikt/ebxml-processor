package no.nav.emottak.payload.fnrsjekk

import no.nav.emottak.payload.log

import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.cert.ocsp.SingleResp
import org.bouncycastle.cert.ocsp.UnknownStatus
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
internal fun createSertifikatInfoFromOCSPResponse(
    certificate: X509Certificate,
    singleResponse: SingleResp,
    ssn: String
): SertifikatInfo {
    val certStatus: CertificateStatus? = singleResponse.certStatus
    var status = SertifikatStatus.UKJENT
    val beskrivelse: String
    when (certStatus) {
        null -> {
            status = SertifikatStatus.OK
            beskrivelse = "Sertifikat ikke revokert"
        }
        is RevokedStatus -> {
            status = SertifikatStatus.REVOKERT
            beskrivelse = if (certStatus.hasRevocationReason()) {
                RevocationReason.getRevocationReason(certStatus.revocationReason)
            } else {
                "Revokasjonstatus mangler, kan være revokert"
            }
            log.warn("Sertifikat revokert: $beskrivelse")
        }
        is UnknownStatus -> {
            log.warn("Revokasjonstatus ukjent, kan være revokert")
            beskrivelse = "Revokasjonstatus ukjent, kan være revokert"
        }
        else -> {
            log.warn("Kan ikke fastslå revokasjonsstatus, kan være revokert")
            beskrivelse = "Kan ikke fastslå revokasjonsstatus, kan være revokert"
        }
    }
    return SertifikatInfo(
        serienummer = certificate.serialNumber.toString(),
        status = status,
        type = getSertifikatType(certificate),
        seid = certificate.getSEIDVersion(),
        gyldigFra = formatDate(certificate.notBefore),
        gyldigTil = formatDate(certificate.notAfter),
        utsteder = certificate.issuerX500Principal.name,
        orgnummer = certificate.getOrganizationNumber(),
        fnr = ssn,
        beskrivelse = beskrivelse,
        feilmelding = null
    )
}

internal fun formatDate(date: Date): String {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale("nb")).format(date)
}

private fun getSertifikatType(certificate: X509Certificate): SertifikatType {
    return if (certificate.isVirksomhetssertifikat())
        SertifikatType.VIRKSOMHET
    else
        SertifikatType.PERSONLIG
}