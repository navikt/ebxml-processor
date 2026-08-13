package no.nav.emottak.cpa.configuration

import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka
import java.net.URI
import java.time.Duration

data class Config(
    val kafka: Kafka,
    val eventLogging: EventLogging,
    val nhnOAuth: NhnOAuthConfig,
    val nhn: Nhn
)

/**
 * OAuth settings as loaded from configuration. [audience] and [tokenEndpoint] are deliberately not
 * part of this class - they are derived at runtime from the OpenID discovery document found at
 * [wellKnownUrl], see [NhnOAuthConfig.resolve].
 */
data class NhnOAuthConfig(
    val keyId: NhnOAuth.KeyId,
    val clientId: NhnOAuth.ClientId,
    val wellKnownUrl: URI,
    val scope: NhnOAuth.Scope,
    val grantType: NhnOAuth.GrantType,
    val clientAssertionType: NhnOAuth.ClientAssertionType
)

/**
 * Fully resolved OAuth settings, ready to be used to authenticate against NHN. [audience] and
 * [tokenEndpoint] are resolved from the OpenID discovery document, see [NhnOAuthConfig.resolve].
 */
data class NhnOAuth(
    val keyId: KeyId,
    val clientId: ClientId,
    val audience: Audience,
    val tokenEndpoint: URI,
    val scope: Scope,
    val grantType: GrantType,
    val clientAssertionType: ClientAssertionType
) {
    @JvmInline
    value class KeyId(val value: String)

    @JvmInline
    value class ClientId(val value: String)

    @JvmInline
    value class Audience(val value: String)

    @JvmInline
    value class Scope(val value: String)

    @JvmInline
    value class GrantType(val value: String)

    @JvmInline
    value class ClientAssertionType(val value: String)
}

data class Nhn(
    val cpApiBaseUrl: URI,
    val cpApiCommunicationPartyUrl: URI,
    val cpApiCertificateUrl: URI,
    val cpApiActive: Boolean,
    val keyPairPath: KeyPairPath,
    val cpApiCacheTtl: Duration = Duration.ofDays(1)
) {
    @JvmInline
    value class KeyPairPath(val value: String)
}
