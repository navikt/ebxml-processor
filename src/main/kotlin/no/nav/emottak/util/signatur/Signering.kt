package no.nav.emottak.util.signatur

import no.nav.emottak.util.crypto.getSignerCertificate
import no.nav.emottak.util.crypto.getSignerKey
import org.w3c.dom.Document
import java.security.Key
import java.security.cert.X509Certificate
import javax.xml.crypto.dsig.Reference
import javax.xml.crypto.dsig.SignedInfo
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec


class Signering {

    private val defaultAlias = "test2023"
    private val digestAlgorithm: String = "http://www.w3.org/2001/04/xmlenc#sha256"
    private val canonicalizationMethod: String = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
    private val signatureAlgorithm: String = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"

    private val factory = XMLSignatureFactory.getInstance("DOM")

    fun signerXML(document: Document, alias: String = defaultAlias): Document {
        val signerCertificate: X509Certificate = getSignerCertificate(alias)
        val signerKey: Key = getSignerKey(alias)
        val keyInfoFactory = factory.keyInfoFactory
        val x509Content: MutableList<Any?> = ArrayList()
        x509Content.add(signerCertificate)
        val x509data = keyInfoFactory.newX509Data(x509Content)
        val keyInfo = keyInfoFactory.newKeyInfo(listOf(x509data))

        val dsc = DOMSignContext(signerKey, document.documentElement)

        val signature = factory.newXMLSignature(createSignedInfo(), keyInfo)
        signature.sign(dsc)
        return document
    }

    private fun createSignedInfo(): SignedInfo {
        return factory.newSignedInfo(
            factory.newCanonicalizationMethod(
                canonicalizationMethod,
                null as C14NMethodParameterSpec?
            ),
            factory.newSignatureMethod(signatureAlgorithm, null), listOf(createReference())
        )
    }

    private fun createReference(): Reference {
        return factory.newReference(
            "",
            factory.newDigestMethod(digestAlgorithm, null),
            listOf(factory.newTransform(Transform.ENVELOPED, null as TransformParameterSpec?)),
            null,
            null
        )
    }
}

