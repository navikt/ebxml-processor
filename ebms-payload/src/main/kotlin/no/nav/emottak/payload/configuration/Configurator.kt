package no.nav.emottak.payload.configuration

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import no.nav.emottak.utils.environment.getEnvVar

@OptIn(ExperimentalHoplite::class)
fun config() = ConfigLoader.builder()
    .addEnvironmentSource()
    .addResourceSource("/kafka_common.conf")
    .addResourceSource(configurationFileResolver())
    .withExplicitSealedTypes()
    .build()
    .loadConfigOrThrow<Config>()

private fun configurationFileResolver() = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> "/application_prod.conf"
    "dev-fss" -> "/application_dev.conf"
    else -> "/application_local.conf"
}
