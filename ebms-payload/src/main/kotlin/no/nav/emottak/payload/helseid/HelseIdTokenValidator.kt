package no.nav.emottak.payload.helseid

import com.nimbusds.jose.HeaderParameterNames
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
import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import jakarta.xml.bind.DatatypeConverter
import no.nav.emottak.payload.helseid.util.XPathEvaluator
import no.nav.emottak.payload.helseid.util.msgHeadNamespaceContext
import no.nav.emottak.utils.environment.getEnvVar
import org.w3c.dom.Document
import java.net.URI
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.text.ParseException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date
import java.util.Locale

const val HELSE_ID_ISSUER_URL_PROD = "https://helseid-sts.nhn.no"
const val HELSE_ID_ISSUER_URL_TEST = "https://helseid-sts.test.nhn.no"

val HELSE_ID_ISSUER = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> HELSE_ID_ISSUER_URL_PROD
    else -> HELSE_ID_ISSUER_URL_TEST
}
const val HELSE_ID_JWKS_URL_TEST = "https://helseid-sts.test.nhn.no/.well-known/openid-configuration/jwks"
const val HELSE_ID_JWKS_URL_PROD = "https://helseid-sts.nhn.no/.well-known/openid-configuration/jwks"

val HELSE_ID_JWKS_URL = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> HELSE_ID_JWKS_URL_PROD
    else -> HELSE_ID_JWKS_URL_TEST
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

class HelseIdTokenValidator(
    private val issuer: String = HELSE_ID_ISSUER,
    private val allowedClockSkewInMs: Long = 0,
    private val helseIdJwkSource: JWKSource<SecurityContext> = JWKSourceBuilder<SecurityContext>.create<SecurityContext>(
        URI.create(HELSE_ID_JWKS_URL).toURL()
    ).build()
) {

    fun getValidatedNin(base64Token: String, timestamp: Instant): String? = parseSignedJwt(base64Token).also {
        validateHeader(it)
        validateClaims(it, Date.from(timestamp))
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

    private val NO_CERTIFICATE_TO_VERIFY_TOKEN = "no certificate to verify token with given"
    private fun getCertificateByThumbprint(
        thumbprint: String,
        algorithm: String,
        certificates: Collection<X509Certificate>,
        base64url: Boolean = true
    ): X509Certificate =
        certificates.firstOrNull {
            thumbprint == if (base64url) thumbprintBase64Url(it, algorithm) else thumbprintHex(it, algorithm)
        } ?: throw NoSuchElementException(NO_CERTIFICATE_TO_VERIFY_TOKEN)

    private fun validateClaims(jwt: SignedJWT, timestamp: Date) = try {
        jwt.jwtClaimsSet
    } catch (ex: ParseException) {
        throw RuntimeException("Failed to parse claims", ex)
    }.also { claims ->
        validateTimestamps(claims, timestamp)
        if (claims.issuer != issuer) error("Invalid issuer ${claims.issuer}")
        validateEssentialClaims(claims)
    }

    private val SHA256 = "SHA-256"

    fun thumbprintBase64Url(certificate: X509Certificate, algorithm: String = SHA256): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint(certificate, algorithm))

    fun thumbprintHex(certificate: X509Certificate, algorithm: String = SHA256): String =
        DatatypeConverter.printHexBinary(thumbprint(certificate, algorithm)).lowercase()

    fun thumbprint(certificate: X509Certificate, algorithm: String = SHA256): ByteArray =
        try {
            val md = MessageDigest.getInstance(algorithm)
            md.digest(certificate.encoded)
        } catch (e: NoSuchAlgorithmException) {
            throw SecurityException("failed to get certificate's thumbprint", e)
        } catch (e: CertificateEncodingException) {
            throw SecurityException("failed to get certificate's thumbprint", e)
        }

    private fun validateTimestamps(claims: JWTClaimsSet, now: Date) {
        issuedAt(claims)?.let { iat ->
            if (now.time < iat.time - allowedClockSkewInMs) {
                error("${timePrefix(now)} is before issued time ${timePrefix(iat)}")
            }
        }
        claims.expirationTime?.let { exp ->
            if (now.time > exp.time + allowedClockSkewInMs) {
                error("${timePrefix(now)} is after expiry time ${timePrefix(exp)}")
            }
        }
        claims.notBeforeTime?.let { nbf ->
            if (now.time < nbf.time - allowedClockSkewInMs) {
                error("${timePrefix(now)} is before not-before time ${timePrefix(nbf)}")
            }
        }
        authTime(claims)?.let { at ->
            if (now.time < at.time - allowedClockSkewInMs) {
                error("${timePrefix(now)} is before auth-time ${timePrefix(at)}")
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
        if (getStringArray(claims, "scope").none { it in SUPPORTED_SCOPES }) {
            error("Token does not contain required scope")
        }
    }

    private fun extractNin(jwt: SignedJWT): String = getString(jwt.jwtClaimsSet, PID_CLAIM)

    private fun issuedAt(claims: JWTClaimsSet): Date? =
        (claims.getClaim(JWTClaimNames.ISSUED_AT) as? Number)?.let { Date(it.toLong() * 1000) }

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
