package no.nav.emottak.validering.sertifikat

import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509CRL
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

class CRLStore(initialCrls: List<CRL> = emptyList()) {
    constructor(issuerList: Map<String, String>) : this(
        issuerList.map { (issuer, url) ->
            CRL(X500Name(issuer), url, null)
        }
    )

    private val crls = AtomicReference(initialCrls.associateBy(CRL::x500Name))

    fun get(issuer: X500Name): CRL? = crls.get()[issuer]

    fun update(updatedCrls: List<CRL>) {
        crls.updateAndGet { current ->
            current.toMutableMap().apply {
                updatedCrls.forEach { updated ->
                    if (updated.file != null || updated.x500Name !in current) {
                        put(updated.x500Name, updated)
                    }
                }
            }.toMap()
        }
    }
}

data class CRL(
    val x500Name: X500Name,
    val url: String,
    val file: X509CRL?,
    val updated: Instant = Instant.now()
) {
    fun validate() {
        when {
            file == null ->
                throw CRLException("Issuer $x500Name støttet, men henting av CRL har feilet")
            x500Name != X500Name(file.issuerX500Principal.name) ->
                throw CRLException("CRL-fil utstedt av ${file.issuerX500Principal.name}, men forventet $x500Name! Dette skal ikke skje!")
            file.nextUpdate?.before(Date.from(Instant.now())) == true ->
                throw CRLException("CRL for Issuer $x500Name er utløpt ${file.nextUpdate}")
        }
    }
}
