package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.getPublicSigningCertificate
import no.nav.emottak.ebms.model.EbMSAttachment
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbMSMessage
import no.nav.emottak.ebms.validation.CID
import no.nav.emottak.ebms.validation.EbMSAttachmentResolver
import no.nav.emottak.util.createDocument
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
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import java.io.ByteArrayInputStream
import java.security.cert.X509Certificate

/**
 * https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/ValideringAvebXMLMeldinger/HITS%201172_2017%20Validering%20av%20ebXML-meldinger%20-oppdatert.pdf
 * 5.10.1 Validering av signatur
 *
  * Dekket: 45, 52, 363, 42 , 32, 39, 40, 34, 33, 37, 36, 35, 38, 41, 64, 85, 103, 65, 46, 84, 50
 * Mangler: 104 (sertifikatsjekk), 105 (sertifikatsjekk) //TODO
 *
 */
class SignatursjekkProcessor(val ebMSDocument: EbMSDocument, ebMSMessage: EbMSMessage): Processor(ebMSMessage) {

    override fun process() {
        validate(ebMSMessage.messageHeader, ebMSDocument.dokument, ebMSMessage.attachments)
    }

    init {
        org.apache.xml.security.Init.init()
    }

    @Throws(SignatureException::class)
    private fun validate(messageHeader: MessageHeader, document: ByteArray, attachments: List<EbMSAttachment>) {
        //TODO Sjekk isNonRepudiation?
        val xmlSignature = retrieveSignatureElement(createDocument(ByteArrayInputStream(document)))
        val certificateFraCPA = messageHeader.getPublicSigningCertificate()
        val certificateFraSignatur = xmlSignature.retrievePublicX509Certificate()
        if (certificateFraSignatur != certificateFraCPA) throw SignatureException("Signert med annet sertifikat enn definert i CPA")
        try {
            if (!verify(
                    certificateFraSignatur,
                    xmlSignature,
                    attachments
                )
            ) throw SignatureException("Signaturvalidering feilet")
        } catch (e: MissingResourceFailureException) {
            throw SignatureException("Signaturvalidering feilet", e)
        }
    }

    private fun verify(
        certificate: X509Certificate,
        signature: XMLSignature,
        attachments: List<EbMSAttachment>
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
    for (i in 0..< referenceLength) {
        val reference = this.item(i)
        val uri = reference.uri ?: throw SignatureException("URI mangler for reference")
        if(reference.digestValue == null || reference.digestValue.isEmpty()) throw SignatureException("Digest value mangler i reference")
        reference.messageDigestAlgorithm.isValidDigestMethodAlgorithm()
        if (uri == "") {
            foundRootReference = true
            if (reference.transforms.length != 3) throw SignatureException("Root reference skal ha 3 references, har ${reference.transforms.length}")
            if (reference.transforms.item(0).uri != Transforms.TRANSFORM_ENVELOPED_SIGNATURE) throw SignatureException("Transform 1 har feil uri! ${reference.transforms.item(0).uri}")
            if (reference.transforms.item(1).uri != Transforms.TRANSFORM_XPATH) throw SignatureException(("Transform 2 har feil uri! ${reference.transforms.item(1).uri}"))
            if (reference.transforms.item(2).uri != Transforms.TRANSFORM_C14N_OMIT_COMMENTS) throw SignatureException(("Transform 3 har feil uri! ${reference.transforms.item(2).uri}"))
        } else if (!uri.startsWith(CID)) throw SignatureException("Ugyldig URI $uri! Kun reference uri som starter med $CID er tillatt")
    }
    if(!foundRootReference) throw SignatureException("Root reference mangler!")
}


private fun SignatureAlgorithm.isValidSignatureMethodAlgorithm() {
    if (this.algorithmURI != XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256 && this.algorithmURI != XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1) {
        throw SignatureException("Ugyldig signaturalgoritme $algorithmURI")
    }
}
private fun MessageDigestAlgorithm.isValidDigestMethodAlgorithm() {
    if (this.algorithmURI != MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256 && this.algorithmURI != MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1) {
        throw SignatureException("Ugyldig digest method $algorithmURI")
    }
}



