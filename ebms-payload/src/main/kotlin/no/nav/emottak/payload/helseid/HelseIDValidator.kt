package no.nav.emottak.payload.helseid

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import java.security.cert.X509Certificate
import java.text.ParseException
import java.time.ZonedDateTime
import java.util.Base64
import java.util.Date
import no.nav.emottak.payload.helseid.util.util.XPathUtil
import no.nav.emottak.utils.environment.getEnvVar
import org.w3c.dom.Document

interface NinTokenValidator {

    /**
     * Gets the helse-id token from a xml-document which is inside an attachment with MimeType application/jwt
     * or application/json
     * @param doc the M1 xml document
     * @return helse-id token or null
     */
    fun getHelseIDTokenNodesFromDocument(doc: Document): String?

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

    override fun getValidatedNin(
        b64: String,
        timeStamp: ZonedDateTime,
        certificates: Collection<X509Certificate>
    ): String? {
        val signedJWT = getSignedJWT(b64)
        validateHeader(signedJWT)
        validateClaims(signedJWT, toDate(timeStamp), issuer)
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
    override fun getHelseIDTokenNodesFromDocument(doc: Document): String? {
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
    }
}
