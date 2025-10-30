package no.nav.emottak.payload.helseid.util

import com.nimbusds.oauth2.sdk.id.Issuer
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import no.nav.emottak.payload.configuration.config
import java.net.URL
import java.time.Instant

object OpenIdConfigProvider {
    private val nhnUrl: String = config().helseId.nhnUrl
    private val cacheTimeInSec = config().helseId.openIdConfigCacheTimeInSec
    private var cachedConfig: OIDCProviderMetadata? = null
    private var lastFetched: Instant? = null

    val issuer: String
        get() = this.getConfig().issuer.value

    val jwksUrl: URL
        get() = this.getConfig().jwkSetURI.toURL()

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
        return metadata
    }
}
