package no.nav.emottak.payload.helseid

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import java.text.ParseException
import java.time.ZonedDateTime
import java.util.Base64
import java.util.Date
import no.nav.emottak.payload.helseid.util.util.namespaceContext
import no.nav.emottak.utils.environment.getEnvVar
import org.w3c.dom.Document

val ISSUER = getEnvVar("HELSE_ID_ISSUER", "https://helseid-sts.test.nhn.no")
val helseIdValidator = HelseIDValidator(ISSUER)

class HelseIDValidator(
    private val issuer: String = "", private val allowedClockSkewInMs: Long = 0
) {

    fun getValidatedNin(b64: String, timestamp: ZonedDateTime): String? {
        val jwt = parseSignedJWT(b64)
        validateHeader(jwt)
        validateClaims(jwt, Date.from(timestamp.toInstant()))
        return extractNin(jwt)
    }

    fun getNin(b64Token: String): String = extractNin(parseSignedJWT(b64Token))

    fun getNin(signedJWT: SignedJWT): String = extractNin(signedJWT)

    fun getHelseIDTokenNodesFromDocument(doc: Document): String? {
        val nodes = XPathEvaluator.nodesAt(
            doc,
            namespaceContext,
            "/mh:MsgHead/mh:Document/mh:RefDoc[mh:MsgType/@V='A'][mh:MimeType/text()='application/jwt']/mh:Content/bas:Base64Container|/mh:MsgHead/mh:Document/mh:RefDoc[mh:MsgType/@V='A'][mh:MimeType/text()='application/json']/mh:Content/bas:Base64Container"
        )
        return when (nodes.size) {
            0 -> null
            1 -> XPathEvaluator.stringAt(nodes[0], namespaceContext, "text()")
            else -> error("unable to determine which of the ${nodes.size} attachments that is HelseID-token")
        }
    }

    private fun parseSignedJWT(b64: String): SignedJWT {
        val token = if ('.' in b64) b64 else String(Base64.getDecoder().decode(b64))
        val jwt = try {
            JWTParser.parse(token)
        } catch (e: ParseException) {
            throw RuntimeException("Failed to parse token $token", e)
        }

        return when (jwt) {
            is SignedJWT -> {
                if (jwt.header.algorithm !in SUPPORTED_ALGOS) {
                    error("Token is not signed with an approved algorithm")
                }
                jwt
            }

            else -> error("Token $token is unsigned")
        }
    }

    private fun validateHeader(jwt: SignedJWT) {
        if (jwt.header.type !in SUPPORTED_JWT_TYPES) {
            error("Unsupported token type ${jwt.header.type}")
        }
    }

    private fun validateClaims(jwt: SignedJWT, timestamp: Date) {
        val claims = try {
            jwt.jwtClaimsSet
        } catch (e: ParseException) {
            throw RuntimeException("Failed to parse claims", e)
        }

        validateTimestamps(claims, timestamp)
        validateIssuer(claims)
        validateEssentialClaims(claims)
    }

    private fun validateTimestamps(claims: JWTClaimsSet, ts: Date) {
        issuedTime(claims)?.let {
            if (ts.time < it.time - allowedClockSkewInMs) error("$TIMESTAMP$ts) is before issued time ($it)")
        }
        claims.expirationTime?.let {
            if (ts.time > it.time + allowedClockSkewInMs) error("$TIMESTAMP$ts) is after expiry time ($it)")
        }
        claims.notBeforeTime?.let {
            if (ts.time < it.time - allowedClockSkewInMs) error("$TIMESTAMP$ts) is before not-before time ($it)")
        }
        authTime(claims)?.let {
            if (ts.time < it.time - allowedClockSkewInMs) error("$TIMESTAMP$ts) is before auth-time ($it)")
        }
    }

    private fun issuedTime(claims: JWTClaimsSet): Date? =
        (claims.getClaim(JWTClaimNames.ISSUED_AT) as? Number)?.let { Date(it.toLong() * 1000) }

    private fun validateIssuer(claims: JWTClaimsSet) {
        if (claims.issuer != issuer) error("Invalid issuer ${claims.issuer}")
    }

    private fun validateEssentialClaims(claims: JWTClaimsSet) {
        if (claims.getClaim("helseid://claims/identity/security_level") != SECURITY_LEVEL) {
            error("Token has invalid security-level")
        }

        if (claims.audience.none { it in SUPPORTED_AUDIENCE }) {
            error("Token does not contain required audience")
        }

        val scopes = getStringArrayClaim(claims, "scope")
        if (scopes.none { it in SUPPORTED_SCOPES }) {
            error("Token does not contain required scope")
        }
    }

    private fun extractNin(jwt: SignedJWT): String {
        val claims = try {
            jwt.jwtClaimsSet
        } catch (e: ParseException) {
            throw RuntimeException("Failed to get claimset", e)
        }
        return getStringClaim(claims, "helseid://claims/identity/pid")
    }

    private fun getStringClaim(claims: JWTClaimsSet, name: String): String =
        try {
            claims.getStringClaim(name)
        } catch (e: ParseException) {
            throw RuntimeException("Failed to get '$name' from claims", e)
        }

    private fun getStringArrayClaim(claims: JWTClaimsSet, name: String): Array<String> =
        try {
            claims.getStringArrayClaim(name)
        } catch (e: ParseException) {
            throw RuntimeException("Failed to get '$name' from claims", e)
        }

    private fun authTime(claims: JWTClaimsSet): Date? =
        (claims.getClaim("auth_time") as? Number)?.let { Date(it.toLong()) }

    companion object {
        private val SUPPORTED_ALGOS = listOf(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512
        )

        internal val SUPPORTED_AUDIENCE = listOf("e-helse:reseptformidleren", "hdir:rf-rekvirent")
        internal val SUPPORTED_SCOPES = listOf("e-helse:reseptformidleren/rekvirent", "hdir:rf-rekvirent/rekvirering")
        private val SUPPORTED_JWT_TYPES = listOf(
            JOSEObjectType.JWT, JOSEObjectType("at+jwt"), JOSEObjectType("application/at+jwt")
        )

        private const val SECURITY_LEVEL = "4"
        private const val TIMESTAMP = "Timestamp ("
    }
}
