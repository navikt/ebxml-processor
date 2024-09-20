package no.nav.emottak.payload.ocspstatus

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.emottak.crypto.FileKeyStoreConfig
import no.nav.emottak.util.getEnvVar
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509CRL
import java.time.LocalDateTime



var certificateAuthorities: CertificateAuthorities = run {
    val mapper = ObjectMapper(YAMLFactory())
    mapper.registerModules(KotlinModule.Builder().build())
    val input = ClassLoader.getSystemResourceAsStream("caList-dev.yaml")
    mapper.readValue(input,CertificateAuthorities::class.java)
}

internal fun trustStoreConfig() = FileKeyStoreConfig(
    keyStoreFilePath = getEnvVar("TRUSTSTORE_PATH", resolveDefaultTruststorePath()),
    keyStorePass = getEnvVar("TRUSTSTORE_PWD", "123456789").toCharArray(),
    keyStoreType = "PKCS12"
)
data class CAHolder(
    val name: String,
    val dn: String,
    val crlUrl: String,
    val ocspUrl: String,
    val ocspSignerAlias: String,
    val x500Name: X500Name = X500Name(dn),
    var crl: X509CRL?,
    var cachedDate: LocalDateTime = LocalDateTime.now()
)
data class CertificateAuthorities(
    val caList: List<CAHolder>
)