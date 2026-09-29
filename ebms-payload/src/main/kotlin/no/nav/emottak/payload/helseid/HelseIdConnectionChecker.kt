package no.nav.emottak.payload.helseid

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.util.DefaultResourceRetriever
import no.nav.emottak.payload.helseid.util.OpenIdConfigProvider
import no.nav.emottak.payload.log

fun checkHelseIdJwksConnection() {
    val jwksUrl = OpenIdConfigProvider.jwksUrl
    log.info("Checking HelseID JWKS connection to {}", jwksUrl)
    val resource = DefaultResourceRetriever(5_000, 20_000).retrieveResource(jwksUrl)
    check(JWKSet.parse(resource.content).keys.isNotEmpty()) {
        "HelseID JWKS contained no keys"
    }
}
