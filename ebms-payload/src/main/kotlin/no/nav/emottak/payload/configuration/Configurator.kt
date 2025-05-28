package no.nav.emottak.payload.configuration

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import no.nav.emottak.utils.environment.getEnvVar

@OptIn(ExperimentalHoplite::class)
fun config() = ConfigLoader.builder()
    .addEnvironmentSource()
    .addResourceSource(certificateAuthorityResourceResolver())
    .addResourceSource("/kafka_common.conf")
    .withExplicitSealedTypes()
    .build()
    .loadConfigOrThrow<Config>()

private fun certificateAuthorityResourceResolver() = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> "/ca_prod.conf"
    else -> "/ca_dev.conf"
}
