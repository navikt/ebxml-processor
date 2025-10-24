package no.nav.emottak.payload.helseid.testutils

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import no.nav.emottak.payload.helseid.HelseIdTokenValidator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.math.floor

class HelseIDCreator(pathToKeystore: String, keystoreType: String = "jks", private val password: CharArray) {

    private val MS_IN_A_DAY = 24 * 60 * 60 * 1000
    private val EXPIRATION_DAYS = 100L

    private val keyStore: KeyStore = SecurityUtils.createKeyStore(pathToKeystore, keystoreType, password)

    @Suppress("LongParameterList")
    fun getToken(
        alias: String? = null,
        pid: String,
        audience: String = HelseIdTokenValidator.Companion.SUPPORTED_AUDIENCE.last(),
        scope: String = HelseIdTokenValidator.Companion.SUPPORTED_SCOPES.last(),
        algo: JWSAlgorithm = JWSAlgorithm.RS256,
        type: JOSEObjectType = JOSEObjectType.JWT,
        jwk: JWK? = null
    ): String {
        return getSignedJWT(alias, pid, audience, scope, algo, type, jwk).serialize()
    }

    @Suppress("LongParameterList")
    private fun getSignedJWT(
        alias: String? = null,
        pid: String,
        audience: String,
        scope: String,
        algo: JWSAlgorithm,
        type: JOSEObjectType,
        jwk: JWK?
    ): SignedJWT {
        val now = Date()
        val expirationTime = Date(now.time + MS_IN_A_DAY * EXPIRATION_DAYS)
        val clientId = UUID.randomUUID().toString()
        val claimsSet = JWTClaimsSet.Builder()
            .subject(hash(clientId + pid))
            .issuer("https://helseid-sts.test.nhn.no")
            .expirationTime(expirationTime)
            .audience(
                listOf(
                    "https://helseid-sts.test.nhn.no/resources",
                    "kjernejournal.api",
                    audience
                )
            )
            .issueTime(now)
            .notBeforeTime(now)
            .claim("client_id", clientId)
            .claim("auth_time", floor(now.time / 1000.0))
            .claim("iat", floor(now.time / 1000.0))
            .claim("idp", "testidp-oidc")
            .claim("helseid://claims/identity/security_level", "4")
            .claim("helseid://claims/hpr/hpr_number", listOf("431001110"))
            .claim("helseid://claims/identity/pid", pid)
            .claim("amr", listOf("pwd"))
            .claim(
                "scope",
                listOf(
                    "openid",
                    "profile",
                    "helseid://scopes/identity/pid",
                    "helseid://scopes/identity/security_level",
                    "https://ehelse.no/kjernejournal/kj_api",
                    scope
                )
            )
            .build()
        return when {
            alias != null -> {
                val certificate = SecurityUtils.getCertificate(keyStore, alias)
                val signedJWT = SignedJWT(
                    JWSHeader.Builder(algo)
                        .type(type)
                        .keyID(X509Utils.thumbprintHex(certificate, "SHA-1"))
                        .x509CertSHA256Thumbprint(Base64URL.encode(X509Utils.thumbprint(certificate)))
                        .build(),
                    claimsSet
                )
                doSign(signedJWT, alias)
            }

            jwk != null -> {
                val signedJWT = SignedJWT(
                    JWSHeader.Builder(algo)
                        .type(type)
                        .keyID(jwk.keyID)
                        .x509CertSHA256Thumbprint(jwk.computeThumbprint())
                        .build(),
                    claimsSet
                )
                doSign(signedJWT, jwk)
            }

            else -> throw IllegalArgumentException("need alias or jwk to sign")
        }
    }

    private fun <T : JWSObject?> doSign(jwsObject: T, alias: String): T {
        val signer: JWSSigner = signerWithCertificate(alias)
        try {
            jwsObject!!.sign(signer)
        } catch (e: JOSEException) {
            throw RuntimeException("failed to sign", e)
        }
        return jwsObject
    }

    private fun <T : JWSObject?> doSign(jwsObject: T, jwk: JWK): T {
        val signer: JWSSigner = signer(jwk)
        try {
            jwsObject!!.sign(signer)
        } catch (e: JOSEException) {
            throw RuntimeException("failed to sign", e)
        }
        return jwsObject
    }

    private fun signerWithCertificate(alias: String): JWSSigner =
        RSASSASigner(SecurityUtils.getPrivateKey(keyStore, password, alias))

    private fun signer(jwk: JWK): JWSSigner =
        when (jwk.algorithm) {
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512 -> RSASSASigner(jwk.toRSAKey().toRSAPrivateKey())

            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512 -> ECDSASigner(jwk.toECKey().toECPrivateKey())
            JWSAlgorithm.HS256, JWSAlgorithm.HS384, JWSAlgorithm.HS512 -> MACSigner(jwk as OctetSequenceKey)
            else -> throw RuntimeException("unsupported algorithm")
        }

    private fun hash(text: String): String {
        val salt = "MySuperSecretSalt"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            Base64.getEncoder().encodeToString(md.digest((text + salt).toByteArray()))
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("failed to hash string", e)
        }
    }
}
