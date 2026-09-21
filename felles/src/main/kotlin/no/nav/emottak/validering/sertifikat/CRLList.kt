package no.nav.emottak.validering.sertifikat

import no.nav.emottak.utils.environment.getEnvVar

private val prodCRLLists = mapOf(
    "CN=Buypass Class 3 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.buypassca.com/BPCl3CaG2STBS.crl",
    "CN=Buypass Class 3 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.buypassca.com/BPCl3CaG2HTBS.crl",
    "CN=Buypass Class 3 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.buypassca.com/BPCl3CaG2HTPS.crl",
    "CN=Commfides Legal Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO" to "https://crl.commfides.com/G3/CommfidesLegalPersonCA-G3.crl",
    "CN=Commfides Natural Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO" to "https://crl.commfides.com/G3/CommfidesNaturalPersonCA-G3.crl"
)

private val testCRLLists = mapOf(
    "CN=Buypass Class 3 Test4 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.test4.buypassca.com/BPCl3CaG2STBS.crl",
    "CN=Buypass Class 3 Test4 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.test4.buypassca.com/BPCl3CaG2HTBS.crl",
    "CN=Buypass Class 3 Test4 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.test4.buypassca.com/BPCl3CaG2HTPS.crl",
    "CN=Commfides Legal Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO" to "https://crl.test.commfides.com/G3/CommfidesLegalPersonCA-G3-TEST.crl",
    "CN=Commfides Natural Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO" to "https://crl.test.commfides.com/G3/CommfidesNaturalPersonCA-G3-TEST.crl"
)

val defaultCRLLists: Map<String, String> = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "dev-fss" -> testCRLLists + prodCRLLists
    "prod-fss" -> prodCRLLists
    else -> testCRLLists
}
