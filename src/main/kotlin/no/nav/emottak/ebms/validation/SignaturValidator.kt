package no.nav.emottak.ebms.validation

import no.nav.emottak.ebms.model.EbmsAttachment
import no.nav.emottak.melding.model.SignatureDetails
import no.nav.emottak.util.retrievePublicX509Certificate
import no.nav.emottak.util.retrieveSignatureElement
import no.nav.emottak.util.signatur.SignatureException
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.algorithms.SignatureAlgorithm
import org.apache.xml.security.keys.KeyInfo
import org.apache.xml.security.signature.MissingResourceFailureException
import org.apache.xml.security.signature.SignedInfo
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.transforms.Transforms
import org.apache.xml.security.utils.Constants
import org.w3c.dom.Document
import java.security.cert.X509Certificate

/**
 * https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/ValideringAvebXMLMeldinger/HITS%201172_2017%20Validering%20av%20ebXML-meldinger%20-oppdatert.pdf
 * 5.10.1 Validering av signatur
 *
 * Dekket: 45, 52, 363, 42 , 32, 39, 40, 34, 33, 37, 36, 35, 38, 41, 64, 85, 103, 65, 46, 84, 50
 * Mangler: 104 (sertifikatsjekk), 105 (sertifikatsjekk) //TODO
 *
 */
class SignaturValidator() {

    init {
        org.apache.xml.security.Init.init()
    }

    @Throws(SignatureException::class)
    fun validate(signatureDetails: SignatureDetails, dokument: Document, attachments: List<EbmsAttachment>) {
        // TODO Sjekk isNonRepudiation?
        val xmlSignature = dokument.retrieveSignatureElement()
        val sertfikatFraCPA = signatureDetails.retrievePublicX509Certificate()
        val sertifikatFraSignatur = xmlSignature.retrievePublicX509Certificate()
        if (!sertifikatFraSignatur.equals(sertfikatFraCPA)) throw SignatureException("Signert med annet sertifikat enn definert i CPA")
        try {
            if (!verify(
                    sertifikatFraSignatur,
                    xmlSignature,
                    attachments
                )
            ) {
                throw SignatureException("Signaturvalidering feilet")
            }
        } catch (e: MissingResourceFailureException) {
            throw SignatureException("Signaturvalidering feilet", e)
        }
    }

    private fun verify(
        certificate: X509Certificate,
        signature: XMLSignature,
        attachments: List<EbmsAttachment>
    ): Boolean {
        signature.validate()
        val resolver = EbMSAttachmentResolver(attachments)
        signature.addResourceResolver(resolver)
        return signature.checkSignatureValue(certificate)
    }
}

private fun XMLSignature.validate() {
    val keyInfo = this.keyInfo ?: throw SignatureException("KeyInfo mangler fra signatur")
    keyInfo.validate()
    this.signedInfo.validate()
}

private fun KeyInfo.validate() {
    if (this.lengthX509Data() != 1) throw SignatureException("X509Data mangler fra signatur")
    if (this.itemX509Data(0).lengthCertificate() != 1 || this.x509Certificate == null) throw SignatureException("X509Certificate mangler fra X509Data")
}

private fun SignedInfo.validate() {
    this.signatureAlgorithm.isValidSignatureMethodAlgorithm()
    this.validateReferences()
}

private fun SignedInfo.validateReferences() {
    var foundRootReference = false
    val referenceLength = this.length(Constants.SignatureSpecNS, Constants._TAG_REFERENCE)
    if (referenceLength < 1) throw SignatureException("Mangler signature reference")
    for (i in 0 until referenceLength) {
        val reference = this.item(i)
        val uri = reference.uri ?: throw SignatureException("URI mangler for reference")
        if (reference.digestValue == null || reference.digestValue.isEmpty()) throw SignatureException("Digest value mangler i reference")
        reference.messageDigestAlgorithm.isValidDigestMethodAlgorithm()
        if (uri == "") {
            foundRootReference = true
            // if (reference.transforms.length != 3) throw SignatureException("Root reference skal ha 3 references, har ${reference.transforms.length}")
            var index = 0 // NB: for å være oasis compliant skal disse være i rekkefølge... men vi er pragmatisk
            if (reference.transforms.item(index).uri == Transforms.TRANSFORM_ENVELOPED_SIGNATURE) {
                index++
            } else {
                throw SignatureException("Transform: ${Transforms.TRANSFORM_ENVELOPED_SIGNATURE} har feil uri! ${reference.transforms.item(index).uri}")
            }
            if (reference.transforms.item(index).uri == Transforms.TRANSFORM_XPATH) {
                index++
            } else {
                log.warn("Mangler ${Transforms.TRANSFORM_XPATH}") // throw SignatureException(("Transform 2 har feil uri! ${reference.transforms.item(1).uri}"))
            }
            if (reference.transforms.item(index).uri != Transforms.TRANSFORM_C14N_OMIT_COMMENTS) throw SignatureException(("Transform: ${Transforms.TRANSFORM_C14N_OMIT_COMMENTS} har feil uri! ${reference.transforms.item(index).uri}"))
        } else if (!uri.startsWith(CID_PREFIX)) throw SignatureException("Ugyldig URI $uri! Kun reference uri som starter med $CID_PREFIX er tillatt")
    }
    if (!foundRootReference) throw SignatureException("Root reference mangler!")
}

private fun SignatureAlgorithm.isValidSignatureMethodAlgorithm() {
    if (!validSignatureAlgorithms.contains(this.algorithmURI)) {
        throw SignatureException("Ugyldig signaturalgoritme. ($algorithmURI) ikke en av $validSignatureAlgorithms")
    }
}
private fun MessageDigestAlgorithm.isValidDigestMethodAlgorithm() {
    if (!validMessageDigestAlgorithms.contains(this.algorithmURI)) {
        throw SignatureException("Ugyldig digest method algoritme. ($algorithmURI) ikke en av $validMessageDigestAlgorithms\")")
    }
}

private val validMessageDigestAlgorithms = listOf(
    MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256,
    MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1 // TODO Burde vi fjerne sha1?
)
private val validSignatureAlgorithms = listOf(
    XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
    XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1 // TODO Burde vi fjerne sha1?
)
