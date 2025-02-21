package no.nav.emottak.cpa.cert

import no.nav.emottak.utils.getEnvVar

private val prodCRLLists = mapOf(
    "CN=Buypass Class 3 CA 3,O=Buypass AS-983163327,C=NO" to "http://crl.buypass.no/crl/BPClass3CA3.crl",
    "CN=Buypass Class 3 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.buypassca.com/BPCl3CaG2STBS.crl",
    "CN=Buypass Class 3 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.buypassca.com/BPCl3CaG2HTBS.crl",
    "C=NO, O=Commfides Norge AS - 988 312 495, OU=Commfides Trust Environment (c) 2011 Commfides Norge AS, CN=CPN Enterprise SHA256 CLASS 3" to "http://crl1.commfides.com/CommfidesEnterprise-SHA256.crl",
    "C=NO, O=Commfides Norge AS - 988 312 495, OU=Commfides Trust Environment (c) 2011 Commfides Norge AS, CN=CPN Person High SHA256 CLASS 3" to "http://crl1.commfides.com/CommfidesPerson-High-SHA256.crl",
    "CN=Commfides Legal Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO" to "https://crl.commfides.com/G3/CommfidesLegalPersonCA-G3.crl"
    // "CN=Buypass Class 3 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.buypassca.com/BPCl3CaG2HTPS.crl",
    // "CN=Commfides Natural Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO" to "https://crl.commfides.com/G3/CommfidesNaturalPersonCA-G3.crl",
)

private val testCRLLists = mapOf(
    "CN=Buypass Class 3 Test4 CA 3, O=Buypass AS-983163327, C=NO" to "http://crl.test4.buypass.no/crl/BPClass3T4CA3.crl",
    "CN=Buypass Class 3 Test4 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.test4.buypassca.com/BPCl3CaG2STBS.crl",
    "CN=Buypass Class 3 Test4 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.test4.buypassca.com/BPCl3CaG2HTBS.crl",
    "C=NO, O=Commfides Norge AS - 988 312 495, OU=CPN Enterprise-Norwegian SHA256 CA- TEST, OU=Commfides Trust Environment(C) 2014 Commfides Norge AS - TEST, CN=Commfides CPN Enterprise-Norwegian SHA256 CA - TEST" to "http://crl1.test.commfides.com/CommfidesEnterprise-SHA256.crl",
    "C=NO, O=Commfides Norge AS - 988 312 495, OU=CPN Person High SHA256 CA - TEST, OU=Commfides Trust Environment(C) 2014 Commfides Norge AS - TEST, CN=Commfides CPN Person-High SHA256 CA - TEST" to "http://crl1.test.commfides.com/CommfidesPerson-High-SHA256.crl",
    "CN=Commfides Legal Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO" to "https://crl.test.commfides.com/G3/CommfidesLegalPersonCA-G3-TEST.crl"
    // "CN=Buypass Class 3 Test4 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO" to "http://crl.test4.buypassca.com/BPCl3CaG2HTPS.crl",
    // "CN=Commfides Natural Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO" to "https://crl.test.commfides.com/G3/CommfidesNaturalPersonCA-G3-TEST.crl"
)

val defaultCRLLists: Map<String, String> = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "dev-fss" -> testCRLLists + prodCRLLists
    "prod-fss" -> prodCRLLists
    else -> testCRLLists
}
