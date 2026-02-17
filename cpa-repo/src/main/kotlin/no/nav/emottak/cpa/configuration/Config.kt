package no.nav.emottak.cpa.configuration

import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka
import java.net.URI

data class Config(
    val kafka: Kafka,
    val eventLogging: EventLogging,
    val nhnOAuth: NhnOAuth,
    val nhn: Nhn
)

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
    val baseUrl: URI,
    val keyPairPath: KeyPairPath
) {
    @JvmInline
    value class KeyPairPath(val value: String)
}
