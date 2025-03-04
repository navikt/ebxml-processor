package no.nav.emottak.payload

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import no.nav.emottak.payload.configuration.Config
import no.nav.emottak.utils.getEnvVar

@OptIn(ExperimentalHoplite::class)
fun config() = ConfigLoader.builder()
    .addEnvironmentSource()
    .addResourceSource(certificateAuthorityResourceResolver())
    .withExplicitSealedTypes()
    .build()
    .loadConfigOrThrow<Config>()

private fun certificateAuthorityResourceResolver() = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> "/ca_prod.conf"
    else -> "/ca_dev.conf"
}
