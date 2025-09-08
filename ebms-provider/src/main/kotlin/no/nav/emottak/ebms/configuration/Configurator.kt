package no.nav.emottak.ebms.configuration

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import no.nav.emottak.utils.environment.getEnvVar

@OptIn(ExperimentalHoplite::class)
fun config() = ConfigLoader.builder()
    .addEnvironmentSource()
    .addResourceSource("/application-personal.conf", optional = true)
    .addResourceSource("/kafka_common.conf")
    .addResourceSource("/ebms_kafka_queues.conf")
    .addResourceSource("/application.conf")
    .addResourceSource(configurationFileResolver())
    .withExplicitSealedTypes()
    .build()
    .loadConfigOrThrow<Config>()

private fun configurationFileResolver() = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> "/application_prod.conf"
    "dev-fss" -> "/application_dev.conf"
    else -> "/application_local.conf"
}
