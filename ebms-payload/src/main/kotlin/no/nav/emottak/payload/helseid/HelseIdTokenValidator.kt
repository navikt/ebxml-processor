package no.nav.emottak.payload.helseid

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import no.nav.emottak.payload.helseid.util.XPathEvaluator
import no.nav.emottak.payload.helseid.util.msgHeadNamespaceContext
import no.nav.emottak.utils.environment.getEnvVar
import org.w3c.dom.Document
import java.text.ParseException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date
import java.util.Locale

val HELSE_ID_ISSUER = getEnvVar("HELSE_ID_ISSUER", "https://helseid-sts.test.nhn.no")

class HelseIdTokenValidator(
    private val issuer: String = HELSE_ID_ISSUER,
    private val allowedClockSkewInMs: Long = 0
) {
    fun getValidatedNin(base64Token: String, timestamp: ZonedDateTime): String? =
        parseSignedJwt(base64Token).also {
            validateHeader(it)
            validateClaims(it, Date.from(timestamp.toInstant()))
        }.let(::extractNin)

    fun getNin(base64Token: String): String = extractNin(parseSignedJwt(base64Token))

    fun getNin(signedJwt: SignedJWT): String = extractNin(signedJwt)

    fun getHelseIdTokenFromDocument(document: Document): String? {
        val nodes = XPathEvaluator.nodesAt(
            document,
            msgHeadNamespaceContext,
            "/mh:MsgHead/mh:Document/mh:RefDoc[mh:MsgType/@V='A']" +
                "[mh:MimeType/text()='application/jwt' or mh:MimeType/text()='application/json']" +
                "/mh:Content/bas:Base64Container"
        )
        return when (nodes.size) {
            0 -> null
            1 -> XPathEvaluator.stringAt(nodes[0], msgHeadNamespaceContext, "text()")
            else -> error("unable to determine which of the ${nodes.size} attachments that is HelseID-token")
        }
    }

    private fun parseSignedJwt(base64Token: String): SignedJWT {
        val compactToken =
            if ('.' in base64Token) base64Token else String(Base64.getDecoder().decode(base64Token))

        val jwt = try {
            JWTParser.parse(compactToken)
        } catch (ex: ParseException) {
            throw RuntimeException("Failed to parse token", ex)
        }

        if (jwt !is SignedJWT) error("token is unsigned")
        if (jwt.header.algorithm !in SUPPORTED_ALGORITHMS) error("Token is not signed with an approved algorithm")
        return jwt
    }

    private fun validateHeader(jwt: SignedJWT) {
        if (jwt.header.type !in SUPPORTED_JWT_TYPES) error("Unsupported token type ${jwt.header.type}")
    }

    private fun validateClaims(jwt: SignedJWT, timestamp: Date) =
        try {
            jwt.jwtClaimsSet
        } catch (ex: ParseException) {
            throw RuntimeException("Failed to parse claims", ex)
        }.also {
            validateTimestamps(it, timestamp)
            if (it.issuer != issuer) error("Invalid issuer ${it.issuer}")
            validateEssentialClaims(it)
        }

    private fun validateTimestamps(claims: JWTClaimsSet, now: Date) {
        issuedAt(claims)?.let {
            if (now.time < it.time - allowedClockSkewInMs) {
                error(
                    "${timePrefix(now)} is before issued time ${
                    timePrefix(
                        it
                    )
                    }"
                )
            }
        }
        claims.expirationTime?.let {
            if (now.time > it.time + allowedClockSkewInMs) {
                error(
                    "${timePrefix(now)} is after expiry time ${
                    timePrefix(
                        it
                    )
                    }"
                )
            }
        }
        claims.notBeforeTime?.let {
            if (now.time < it.time - allowedClockSkewInMs) {
                error(
                    "${timePrefix(now)} is before not-before time ${
                    timePrefix(
                        it
                    )
                    }"
                )
            }
        }
        authTime(claims)?.let {
            if (now.time < it.time - allowedClockSkewInMs) error("${timePrefix(now)} is before auth-time ${timePrefix(it)}")
        }
    }

    private fun validateEssentialClaims(claims: JWTClaimsSet) {
        if (claims.getClaim(SECURITY_LEVEL_CLAIM) != SECURITY_LEVEL) error("Invalid security-level")
        if (claims.audience.none { it in SUPPORTED_AUDIENCE }) error("Token does not contain required audience")
        if (getStringArray(
                claims,
                "scope"
            ).none { it in SUPPORTED_SCOPES }
        ) {
            error("Token does not contain required scope")
        }
    }

    private fun extractNin(jwt: SignedJWT): String =
        getString(jwt.jwtClaimsSet, PID_CLAIM)

    private fun issuedAt(claims: JWTClaimsSet): Date? =
        (claims.getClaim(JWTClaimNames.ISSUED_AT) as? Number)
            ?.let { Date(it.toLong() * 1000) }

    private fun authTime(claims: JWTClaimsSet): Date? =
        (claims.getClaim("auth_time") as? Number)
            ?.let { Date(it.toLong()) }

    private fun getString(claims: JWTClaimsSet, name: String): String =
        try {
            claims.getStringClaim(name)
        } catch (ex: ParseException) {
            throw RuntimeException("failed to read claim '$name'", ex)
        }

    private fun getStringArray(claims: JWTClaimsSet, name: String): Array<String> =
        try {
            claims.getStringArrayClaim(name)
        } catch (ex: ParseException) {
            throw RuntimeException("failed to read claim '$name'", ex)
        }

    private fun timePrefix(date: Date): String = DATE_FMT.format(date.toInstant())

    companion object {
        private val OSLO_ZONE: ZoneId = ZoneId.of("Europe/Oslo")
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
            .withZone(OSLO_ZONE)
        private val SUPPORTED_ALGORITHMS = listOf(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512
        )
        internal val SUPPORTED_AUDIENCE = listOf(
            "e-helse:reseptformidleren",
            "hdir:rf-rekvirent"
        )
        internal val SUPPORTED_SCOPES = listOf(
            "e-helse:reseptformidleren/rekvirent",
            "hdir:rf-rekvirent/rekvirering"
        )
        private val SUPPORTED_JWT_TYPES = listOf(
            JOSEObjectType.JWT,
            JOSEObjectType("at+jwt"),
            JOSEObjectType("application/at+jwt")
        )

        private const val SECURITY_LEVEL = "4"
        private const val SECURITY_LEVEL_CLAIM = "helseid://claims/identity/security_level"
        private const val PID_CLAIM = "helseid://claims/identity/pid"
    }
}
