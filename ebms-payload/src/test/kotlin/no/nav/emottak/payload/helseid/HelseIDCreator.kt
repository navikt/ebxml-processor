package no.nav.emottak.payload.helseid

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
import com.nimbusds.oauth2.sdk.AuthorizationGrant
import com.nimbusds.oauth2.sdk.ClientCredentialsGrant
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.TokenResponse
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.id.ClientID
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64
import java.util.Date
import java.util.UUID
import no.nav.emottak.payload.helseid.util.security.SecurityUtils
import no.nav.emottak.payload.helseid.util.security.X509Utils
import no.nav.emottak.utils.serialization.getErrorMessage
import org.slf4j.LoggerFactory

class HelseIDCreator(pathToKeystore: String, keystoreType: String = "jks", private val password: CharArray) {

    private val keyStore: KeyStore = SecurityUtils.createKeyStore(pathToKeystore, keystoreType, password)

    @Suppress("LongParameterList")
    fun getToken(
        alias: String? = null, pid: String,
        audience: String = HelseIDValidator.SUPPORTED_AUDIENCE.last(),
        scope: String = HelseIDValidator.SUPPORTED_SCOPES.last(),
        algo: JWSAlgorithm = JWSAlgorithm.RS256,
        type: JOSEObjectType = JOSEObjectType.JWT,
        jwk: JWK? = null
    ): String {
        return getSignedJWT(alias, pid, audience, scope, algo, type, jwk).serialize()
    }

    @Suppress("LongParameterList")
    private fun getSignedJWT(
        alias: String? = null, pid: String,
        audience: String,
        scope: String, algo: JWSAlgorithm,
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
            .claim("auth_time", now.time)
            .claim("iat", now.time)
            .claim("idp", "testidp-oidc")
            .claim("helseid://claims/identity/security_level", "4")
            .claim("helseid://claims/hpr/hpr_number", listOf("431001110"))
            .claim("helseid://claims/identity/pid", pid)
            .claim("amr", listOf("pwd"))
            .claim(
                "scope", listOf(
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
        // Create RSA-signer with the private key
        val signer: JWSSigner = signerWithCertificate(alias)
        try {
            jwsObject!!.sign(signer)
        } catch (e: JOSEException) {
            throw RuntimeException("failed to sign", e)
        }
        return jwsObject
    }

    private fun <T : JWSObject?> doSign(jwsObject: T, jwk: JWK): T {
        // Create RSA-signer with the private key
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

    companion object {
        private val LOG = LoggerFactory.getLogger("no.nav.emottak.payload.helseid.HelseIDCreator")
        private const val MS_IN_A_DAY = 24 * 60 * 60 * 1000
        private const val EXPIRATION_DAYS = 100L

        @JvmStatic
        fun main(a: Array<String>) {
            try {
                val clientGrant: AuthorizationGrant = ClientCredentialsGrant()

                // The credentials to authenticate the client at the token endpoint
                val clientID = ClientID("123")
                val clientSecret = Secret("secret")
                val clientAuth: ClientAuthentication = ClientSecretBasic(clientID, clientSecret)

                // The request scope for the token (may be optional)
                val scope = Scope("read", "write")

                // The token endpoint
                val tokenEndpoint = URI("https://c2id.com/token")

                // Make the token request
                val request = TokenRequest(tokenEndpoint, clientAuth, clientGrant, scope)
                val response = TokenResponse.parse(request.toHTTPRequest().send())
                println(response.toString())
            } catch (e: Exception) {
                LOG.error(e.getErrorMessage(), e)
            }
        }
    }


}
