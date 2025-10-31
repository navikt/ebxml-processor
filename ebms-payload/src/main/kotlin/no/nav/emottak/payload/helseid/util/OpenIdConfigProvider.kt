package no.nav.emottak.payload.helseid.util

import com.nimbusds.oauth2.sdk.id.Issuer
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import no.nav.emottak.payload.configuration.config
import no.nav.emottak.payload.log
import java.net.URI
import java.net.URL
import java.time.Instant

object OpenIdConfigProvider {
    private val nhnUrl: String = config().helseId.nhnUrl
    private val cacheTimeInSec = config().helseId.openIdConfigCacheTimeInSec
    private var cachedConfig: OIDCProviderMetadata? = null
    private var lastFetched: Instant? = null

    val issuer: String
        get() {
            try {
                return this.getConfig().issuer.value
            } catch (e: Exception) {
                val defaultIssuer = config().helseId.issuerDefaultValue
                log.warn("Failed to get OpenID issuer from $nhnUrl, use default value $defaultIssuer", e)
                return defaultIssuer
            }
        }

    val jwksUrl: URL
        get() {
            try {
                return this.getConfig().jwkSetURI.toURL()
            } catch (e: Exception) {
                val defaultJwksUrl = config().helseId.jwksUrlDefaultValue
                log.warn("Failed to get OpenID JWK set URL from $nhnUrl, use default value $defaultJwksUrl", e)
                return URI.create(defaultJwksUrl).toURL()
            }
        }

    private fun getConfig(): OIDCProviderMetadata {
        val now = Instant.now()
        if (cachedConfig != null &&
            lastFetched != null &&
            now.isBefore(lastFetched?.plusSeconds(cacheTimeInSec))
        ) {
            return cachedConfig!!
        }

        return loadOpenIdConfig()
    }

    private fun loadOpenIdConfig(): OIDCProviderMetadata {
        val issuer = Issuer(nhnUrl)
        val metadata = OIDCProviderMetadata.resolve(issuer)
        lastFetched = Instant.now()
        cachedConfig = metadata
        return metadata
    }
}
