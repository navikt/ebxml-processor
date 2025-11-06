package no.nav.emottak.payload.helseid

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKMatcher
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.SimpleSecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import no.nav.emottak.payload.configuration.config
import no.nav.emottak.payload.helseid.util.OpenIdConfigProvider
import no.nav.emottak.payload.helseid.util.XPathEvaluator
import no.nav.emottak.payload.helseid.util.msgHeadNamespaceContext
import org.w3c.dom.Document
import java.text.ParseException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date
import java.util.Locale

class HelseIdTokenValidator(
    private val issuer: String = OpenIdConfigProvider.issuer,
    private val allowedClockSkewInMs: Long = config().helseId.allowedClockSkewInMs,
    private val allowedMessageGenerationGapInMs: Long = config().helseId.allowedMessageGenerationGapInMs,
    private val helseIdJwkSource: JWKSource<SecurityContext> = JWKSourceBuilder<SecurityContext>
        .create<SecurityContext>(OpenIdConfigProvider.jwksUrl).build()
) {
    fun getValidatedNin(base64Token: String, messageGenerationDate: Instant): String? = parseSignedJwt(base64Token)
        .also {
            validateHeader(it)
            validateClaims(it, Date.from(messageGenerationDate))
            it.verify(helseIdJwkSource)
        }.let(::extractNin)

    fun getNin(base64Token: String): String = extractNin(parseSignedJwt(base64Token))

    fun getNin(signedJwt: SignedJWT): String = extractNin(signedJwt)

    fun getHelseIdTokenFromDocument(document: Document): String? {
        val nodes = XPathEvaluator.nodesAt(
            document,
            msgHeadNamespaceContext,
            "/mh:MsgHead/mh:Document/mh:RefDoc[mh:MsgType/@V='A']" + "[mh:MimeType/text()='application/jwt' or mh:MimeType/text()='application/json']" + "/mh:Content/bas:Base64Container"
        )
        return when (nodes.size) {
            0 -> null
            1 -> XPathEvaluator.stringAt(nodes[0], msgHeadNamespaceContext, "text()")
            else -> error("unable to determine which of the ${nodes.size} attachments that is HelseID-token")
        }
    }

    private fun parseSignedJwt(base64Token: String): SignedJWT {
        val compactToken = if ('.' in base64Token) base64Token else String(Base64.getDecoder().decode(base64Token))

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

    private fun validateClaims(jwt: SignedJWT, messageGenerationDate: Date) = try {
        jwt.jwtClaimsSet
    } catch (ex: ParseException) {
        throw RuntimeException("Failed to parse claims", ex)
    }.also { claims ->
        validateTimestamps(claims, messageGenerationDate)
        if (claims.issuer != issuer) error("Invalid issuer ${claims.issuer}")
        validateEssentialClaims(claims)
    }

    private fun validateTimestamps(claims: JWTClaimsSet, messageGenerationDate: Date) {
        claims.issueTime?.let { iat ->
            if (messageGenerationDate.time < iat.time - allowedClockSkewInMs) {
                error("${timePrefix(messageGenerationDate)} is before issued time ${timePrefix(iat)}")
            }
        }
        claims.issueTime?.let { iat ->
            if (messageGenerationDate.time > iat.time - allowedClockSkewInMs + allowedMessageGenerationGapInMs) {
                error("Message generation time should be within ${allowedMessageGenerationGapInMs / 1000} seconds after token issued time")
            }
        }
        claims.expirationTime?.let { exp ->
            if (messageGenerationDate.time > exp.time + allowedClockSkewInMs) {
                error("${timePrefix(messageGenerationDate)} is after expiry time ${timePrefix(exp)}")
            }
        }
        claims.notBeforeTime?.let { nbf ->
            if (messageGenerationDate.time < nbf.time - allowedClockSkewInMs) {
                error("${timePrefix(messageGenerationDate)} is before not-before time ${timePrefix(nbf)}")
            }
        }
        authTime(claims)?.let { at ->
            if (messageGenerationDate.time < at.time - allowedClockSkewInMs) {
                error("${timePrefix(messageGenerationDate)} is before auth-time ${timePrefix(at)}")
            }
        }
    }

    private fun validateEssentialClaims(claims: JWTClaimsSet) {
        if (claims.getClaim(SECURITY_LEVEL_CLAIM) != SECURITY_LEVEL) {
            error("Invalid security-level")
        }
        if (claims.audience.none { it in SUPPORTED_AUDIENCE }) {
            error("Token does not contain required audience")
        }
        if (claims.audience.size > 1) {
            error("Token contains multiple audiences")
        }

        val scopes = getStringArray(claims, "scope")
        if (scopes.none { it in SUPPORTED_SCOPES }) {
            error("Token does not contain required scope")
        }
        if (scopes.size > 1) {
            error("Token contains multiple scopes")
        }
    }

    private fun extractNin(jwt: SignedJWT): String = getString(jwt.jwtClaimsSet, PID_CLAIM)

    private fun authTime(claims: JWTClaimsSet): Date? =
        (claims.getClaim("auth_time") as? Number)?.let { Date(it.toLong()) }

    private fun getString(claims: JWTClaimsSet, name: String): String = try {
        claims.getStringClaim(name)
    } catch (ex: ParseException) {
        throw RuntimeException("failed to read claim '$name'", ex)
    }

    private fun getStringArray(claims: JWTClaimsSet, name: String): Array<String> = try {
        claims.getStringArrayClaim(name)
    } catch (ex: ParseException) {
        throw RuntimeException("failed to read claim '$name'", ex)
    }

    private fun timePrefix(date: Date): String = DATE_FMT.format(date.toInstant())

    companion object {
        private val OSLO_ZONE: ZoneId = ZoneId.of("Europe/Oslo")
        private val DATE_FMT: DateTimeFormatter =
            DateTimeFormatter
                .ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
                .withZone(OSLO_ZONE)
        private val SUPPORTED_ALGORITHMS = listOf(
            JWSAlgorithm.RS256,
            JWSAlgorithm.RS384,
            JWSAlgorithm.RS512,
            JWSAlgorithm.PS256,
            JWSAlgorithm.PS384,
            JWSAlgorithm.PS512,
            JWSAlgorithm.ES256,
            JWSAlgorithm.ES384,
            JWSAlgorithm.ES512
        )
        internal val SUPPORTED_AUDIENCE = listOf(
            "nav:sign-message"
        )
        internal val SUPPORTED_SCOPES = listOf(
            "nav:sign-message/msghead"
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

fun SignedJWT.verify(jwkSource: JWKSource<SecurityContext>): Boolean {
    return jwkSource.get(
        JWKSelector(
            JWKMatcher.Builder()
                .x509CertSHA256Thumbprint(this.header.x509CertSHA256Thumbprint)
                .keyID(this.header.keyID)
                .keyUse(KeyUse.SIGNATURE)
                .build()
        ),
        SimpleSecurityContext()
    ).any { jwk ->
        return this.verify(
            when (jwk) {
                is RSAKey -> RSASSAVerifier(jwk.toRSAPublicKey())
                is ECKey -> ECDSAVerifier(jwk.toECPublicKey())
                is OctetSequenceKey -> com.nimbusds.jose.crypto.MACVerifier(jwk.toByteArray())
                else -> return false
            }
        )
    }
}
