package no.nav.emottak.util.cert

import org.bouncycastle.asn1.x500.X500Name
import java.math.BigInteger
import java.security.cert.X509CRL
import java.security.cert.X509CRLEntry

val issuerList = mapOf(
//    Pair("CN=Buypass Class 3 CA 3,O=Buypass AS-983163327,C=NO","http://crl.buypass.no/crl/BPClass3CA3.crl"),
    Pair("CN=Buypass Class 3 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.buypassca.com/BPCl3CaG2STBS.crl"),
////    Pair("CN=Buypass Class 3 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.buypassca.com/BPCl3CaG2HTPS.crl"),
//    Pair("CN=Buypass Class 3 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.buypassca.com/BPCl3CaG2HTBS.crl"),
//    Pair("CN=Buypass Class 3 Test4 CA 3, O=Buypass AS-983163327, C=NO","http://crl.test4.buypass.no/crl/BPClass3T4CA3.crl"),
    Pair("CN=Buypass Class 3 Test4 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.test4.buypassca.com/BPCl3CaG2STBS.crl"),
////    Pair("CN=Buypass Class 3 Test4 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.test4.buypassca.com/BPCl3CaG2HTPS.crl"),
//    Pair("CN=Buypass Class 3 Test4 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.test4.buypassca.com/BPCl3CaG2HTBS.crl"),
//    Pair("CN=Commfides Legal Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.commfides.com/G3/CommfidesLegalPersonCA-G3.crl"),
////    Pair("CN=Commfides Natural Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.commfides.com/G3/CommfidesNaturalPersonCA-G3.crl"),
//    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=Commfides Trust Environment (c) 2011 Commfides Norge AS, CN=CPN Enterprise SHA256 CLASS 3","http://crl1.commfides.com/CommfidesEnterprise-SHA256.crl"),
////    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=Commfides Trust Environment (c) 2011 Commfides Norge AS, CN=CPN Person High SHA256 CLASS 3","http://crl1.commfides.com/CommfidesPerson-High-SHA256.crl"),
//    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=CPN Enterprise-Norwegian SHA256 CA- TEST, OU=Commfides Trust Environment(C) 2014 Commfides Norge AS - TEST, CN=Commfides CPN Enterprise-Norwegian SHA256 CA - TEST","http://crl1.test.commfides.com/CommfidesEnterprise-SHA256.crl"),
////    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=CPN Person High SHA256 CA - TEST, OU=Commfides Trust Environment(C) 2014 Commfides Norge AS - TEST, CN=Commfides CPN Person-High SHA256 CA - TEST","http://crl1.test.commfides.com/CommfidesPerson-High-SHA256.crl"),
//    Pair("CN=Commfides Legal Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.test.commfides.com/G3/CommfidesLegalPersonCA-G3-TEST.crl"),
////    Pair("CN=Commfides Natural Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.test.commfides.com/G3/CommfidesNaturalPersonCA-G3-TEST.crl"),
)

class CRLChecker(
    val crlFiles: Map<X500Name, X509CRL>
) {
    fun getCRLRevocationInfo(issuer: String, serialNumber: BigInteger) {
        getRevokedCertificate(issuer = X500Name(issuer), serialNumber = serialNumber)?.let {
            throw CertificateValidationException("Sertifikat revokert: serienummer <$serialNumber> revokert med reason <${it.revocationReason}> at <${it.revocationDate}>")
        }
    }

    private fun getRevokedCertificate(issuer: X500Name, serialNumber: BigInteger): X509CRLEntry? {
        return getCRLFile(issuer).getRevokedCertificate(serialNumber)
    }

    private fun getCRLFile(issuer: X500Name): X509CRL {
        val crlFile = crlFiles[issuer]
            ?: throw CertificateValidationException("Issuer $issuer ikke støttet. CRL liste må oppdateres med issuer om denne skal støttes")
        if (X500Name(crlFile.issuerX500Principal.name) != issuer)
            throw CertificateValidationException("Issuer $issuer har ikke utstedt denne CRL-filen, men ${crlFile.issuerX500Principal.name}! Dette skal ikke skje!")
        return crlFile
    }
}
