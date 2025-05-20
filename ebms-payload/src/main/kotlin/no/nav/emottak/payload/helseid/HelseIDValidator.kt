package no.nav.emottak.payload.helseid

import com.nimbusds.jose.HeaderParameterNames
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import no.nav.emottak.payload.helseid.util.lang.DateUtil
import no.nav.emottak.payload.helseid.util.security.X509Utils
import no.nav.emottak.payload.helseid.util.util.XPathUtil
import no.nav.emottak.payload.helseid.util.util.xades.XAdESVerifier
import no.nav.emottak.utils.environment.getEnvVar
import org.w3c.dom.Document
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.text.ParseException
import java.time.ZonedDateTime
import java.util.Base64
import java.util.Date
import javax.xml.namespace.NamespaceContext

interface NinTokenValidator {

    /**
     * Gets the helse-id token from a xml-document which is inside an attachment with MimeType application/jwt
     * or application/json
     * @param doc the M1 xml document
     * @return helse-id token or null
     */
    fun getHelseIDTokenNodesFromDocument(
        doc: Document,
        namespaceContext: NamespaceContext = XAdESVerifier.Companion.namespaceContext
    ): String?

    /**
     * Validates a helse related token
     * @param b64 the base 64 encoded token or the jwt token
     * @param timeStamp when the token must be valid
     * @param certificates a collection of certificates where we will find the one that signed the token
     * @return the signer certificate
     */
    fun validate(
        b64: String,
        timeStamp: ZonedDateTime?,
        certificates: Collection<X509Certificate>
    ): X509Certificate

    fun getValidatedNin(b64: String, timeStamp: ZonedDateTime, certificates: Collection<X509Certificate>): String?

    /**
     * Extracts national identity number from a string
     * @param b64 the token
     * @return the national identity number
     */
    fun getNin(b64: String): String

    /**
     * Extracts national identity number from a signed json web token
     * @param signedJWT the
     * @return the national identity number
     */
    fun getNin(signedJWT: SignedJWT): String
}

val ISSUER = getEnvVar("HELSE_ID_ISSUER", "https://helseid-sts.test.nhn.no")
val ORGNR = "994598759"
val helseIdValidator = HelseIDValidator(ISSUER, ORGNR)

@Suppress("TooManyFunctions")
class HelseIDValidator(
    private val issuer: String = "",
    private val issuerOrganizationNumber: String,
    private val allowedClockSkewInMs: Long = 0
) : NinTokenValidator {

    override fun validate(
        b64: String,
        timeStamp: ZonedDateTime?,
        certificates: Collection<X509Certificate>
    ): X509Certificate {
        val signedJWT = getSignedJWT(b64)
        validateHeader(signedJWT)
        validateClaims(signedJWT, DateUtil.toDate(timeStamp), issuer)
        val certificate = getSignerCertificate(signedJWT, certificates)
        verifySignature(signedJWT, certificate)
        verifySigner(certificate)
        return certificate
    }

    override fun getValidatedNin(
        b64: String,
        timeStamp: ZonedDateTime,
        certificates: Collection<X509Certificate>
    ): String? {
        val signedJWT = getSignedJWT(b64)
        validateHeader(signedJWT)
        validateClaims(signedJWT, DateUtil.toDate(timeStamp), issuer)
        return getNin(signedJWT)
    }

    override fun getNin(b64: String): String {
        val signedJWT = getSignedJWT(b64)
        return getStringClaim(getClaims(signedJWT), "helseid://claims/identity/pid")
    }

    override fun getNin(signedJWT: SignedJWT): String =
        getStringClaim(getClaims(signedJWT), "helseid://claims/identity/pid")

    /**
     * Gets the helse-id token from a xml-document which is inside an attachment with MimeType application/jwt
     * or application/json
     * @param doc the M1 xml document
     * @return helse-id token or null
     */
    @Suppress("MaxLineLength")
    override fun getHelseIDTokenNodesFromDocument(doc: Document, namespaceContext: NamespaceContext): String? {
        val nodes = XPathUtil.getNodesAtPath(
            doc,
            namespaceContext,
            "/mh:MsgHead/mh:Document/mh:RefDoc[mh:MsgType/@V='A'][mh:MimeType/text()='application/jwt']/mh:Content/bas:Base64Container|/mh:MsgHead/mh:Document/mh:RefDoc[mh:MsgType/@V='A'][mh:MimeType/text()='application/json']/mh:Content/bas:Base64Container"
        )

        if (nodes.isEmpty()) {
            return null
        } else if (nodes.size == 1) {
            return XPathUtil.getNormalizedValueAtPathOrNull(nodes[0], namespaceContext, "text()")
        }
        throw RuntimeException("unable to determine which of the ${nodes.size} attachments that is HelseID-token")
    }

    private fun getSignerCertificate(signedJWT: SignedJWT, certificates: Collection<X509Certificate>): X509Certificate {
        val hdr = signedJWT.header
        return when {
            hdr.includedParams.any { it == HeaderParameterNames.X_509_CERT_SHA_256_THUMBPRINT } ->
                getCertificateByThumbprint(hdr.x509CertSHA256Thumbprint.toString(), "SHA-256", certificates)

            hdr.includedParams.any { it == HeaderParameterNames.KEY_ID } ->
                getCertificateByThumbprint(hdr.keyID.lowercase(), "SHA-1", certificates, base64url = false)

            else -> throw NoSuchElementException(NO_CERTIFICATE_TO_VERIFY_TOKEN)
        }
    }

    private fun validateHeader(signedJWT: SignedJWT) {
        val hdr = signedJWT.header
        if (SUPPORTED_JWT_TYPES.none { it == hdr.type }) {
            throw RuntimeException("Unsupported token type ${hdr.type}")
        }
    }

    private fun validateClaims(signedJWT: SignedJWT, timeStamp: Date, issuer: String) {
        try {
            val claimsSet = signedJWT.jwtClaimsSet
            validateTimestamp(claimsSet, timeStamp)
            validateIssuer(claimsSet, issuer)
            validateClaims(claimsSet)
        } catch (e: ParseException) {
            throw RuntimeException("Failed to parse", e)
        }
    }

    @Suppress("ThrowsCount")
    private fun validateTimestamp(claimsSet: JWTClaimsSet, timeStamp: Date) {
        val ts = timeStamp.time
        val issued = issuedTime(claimsSet)
        if (issued != null && ts < (issued.time - allowedClockSkewInMs)) {
            throw RuntimeException("$TIMESTAMP$timeStamp) is xx token issued time ($issued)")
        }
        val expiry = claimsSet.expirationTime
        if (ts > (expiry.time + allowedClockSkewInMs)) {
            throw RuntimeException("$TIMESTAMP$timeStamp) is after token expiry time ($expiry)")
        }
        val notBefore = claimsSet.notBeforeTime
        if (ts < (notBefore.time - allowedClockSkewInMs)) {
            throw RuntimeException("$TIMESTAMP$timeStamp) is before token not-before time ($notBefore)")
        }
        val authTime = authTime(claimsSet)
        if (authTime != null && ts < (authTime.time - allowedClockSkewInMs)) {
            throw RuntimeException("$TIMESTAMP$timeStamp) is before token auth-time time ($authTime)")
        }
    }

    private fun validateIssuer(claimsSet: JWTClaimsSet, issuer: String) {
        if (claimsSet.issuer != issuer) {
            throw RuntimeException("invalid issuer ${claimsSet.issuer}")
        }
    }

    @Suppress("ThrowsCount")
    private fun validateClaims(claimsSet: JWTClaimsSet) {
        if (claimsSet.getClaim("helseid://claims/identity/security_level") != SECURITY_LEVEL) {
            throw RuntimeException("Token has invalid security-level")
        }
        if (claimsSet.audience.intersect(SUPPORTED_AUDIENCE).isEmpty()) {
            throw RuntimeException("Token does not contain required audience")
        }
        if (getStringArrayClaim(claimsSet, "scope").intersect(SUPPORTED_SCOPES).isEmpty()) {
            throw RuntimeException("Token does not contain required scope")
        }
    }

    @Suppress("MaxLineLength")
    fun verifySigner(certificate: X509Certificate) {
        val orgNo = X509Utils.getOrganizationNumber(certificate)
        if (orgNo != issuerOrganizationNumber) {
            throw RuntimeException("Signer certificate organization number $orgNo is not the approved organization number")
        }
    }

    private fun getStringClaim(claimsSet: JWTClaimsSet, claim: String): String =
        try {
            claimsSet.getStringClaim(claim)
        } catch (e: ParseException) {
            throw RuntimeException("failed to get '$claim' from claims", e)
        }

    private fun getStringArrayClaim(claimsSet: JWTClaimsSet, claim: String): Array<String> =
        try {
            claimsSet.getStringArrayClaim(claim)
        } catch (e: ParseException) {
            throw RuntimeException("failed to get '$claim' from claims", e)
        }

    private fun getClaims(signedJWT: SignedJWT): JWTClaimsSet =
        try {
            signedJWT.jwtClaimsSet
        } catch (e: ParseException) {
            throw RuntimeException("failed to get claimset", e)
        }

    private fun authTime(claimsSet: JWTClaimsSet): Date? {
        return when (val authTimeClaim = claimsSet.getClaim("auth_time")) {
            is Number -> Date(authTimeClaim.toLong())
            else -> null
        }
    }

    private fun issuedTime(claimsSet: JWTClaimsSet): Date? {
        return when (val authTimeClaim = claimsSet.getClaim(JWTClaimNames.ISSUED_AT)) {
            is Number -> Date(authTimeClaim.toLong())
            else -> null
        }
    }

    @Suppress("ThrowsCount")
    private fun getSignedJWT(b64: String): SignedJWT {
        val token = if (b64.indexOf('.') >= 0) b64 else String(Base64.getDecoder().decode(b64))
        return try {
            val jwt = JWTParser.parse(token)
            if (jwt is SignedJWT) {
                if (!SUPPORTED_ALGOS.contains(jwt.header.algorithm)) {
                    throw RuntimeException("Token is not signed with an approved algorithm")
                }
                jwt
            } else {
                throw RuntimeException("Token $token is unsigned")
            }
        } catch (e: ParseException) {
            throw RuntimeException("Failed to parse token $token", e)
        }
    }

    private fun verifySignature(jwsObject: JWSObject, certificate: X509Certificate?) {
        requireNotNull(certificate) { NO_CERTIFICATE_TO_VERIFY_TOKEN }
        verifySignature(jwsObject, RSASSAVerifier(certificate.publicKey as RSAPublicKey))
    }

    private fun verifySignature(jwsObject: JWSObject, verifier: JWSVerifier) {
        try {
            if (!jwsObject.verify(verifier)) {
                throw RuntimeException("signature does not verify")
            }
        } catch (e: JOSEException) {
            throw RuntimeException("failed to verify token", e)
        }
    }

    private fun getCertificateByThumbprint(
        thumbprint: String,
        algorithm: String,
        certificates: Collection<X509Certificate>,
        base64url: Boolean = true
    ): X509Certificate =
        certificates.firstOrNull {
            thumbprint == if (base64url) {
                X509Utils.thumbprintBase64Url(it, algorithm)
            } else {
                X509Utils.thumbprintHex(
                    it,
                    algorithm
                )
            }
        } ?: throw NoSuchElementException(NO_CERTIFICATE_TO_VERIFY_TOKEN)

    companion object {
        private val SUPPORTED_ALGOS = listOf(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512
        )
        internal val SUPPORTED_AUDIENCE = listOf("e-helse:reseptformidleren", "hdir:rf-rekvirent")
        internal val SUPPORTED_SCOPES = listOf("e-helse:reseptformidleren/rekvirent", "hdir:rf-rekvirent/rekvirering")
        private val SUPPORTED_JWT_TYPES = listOf(
            JOSEObjectType.JWT,
            JOSEObjectType("at+jwt"),
            JOSEObjectType("application/at+jwt")
        )
        private const val SECURITY_LEVEL = "4"
        private const val TIMESTAMP = "Timestamp ("
        private const val NO_CERTIFICATE_TO_VERIFY_TOKEN = "no certificate to verify token with given"
    }
}
