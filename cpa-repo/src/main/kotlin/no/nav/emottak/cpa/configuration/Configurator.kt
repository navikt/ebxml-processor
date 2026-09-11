package no.nav.emottak.cpa.configuration

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import no.nav.emottak.utils.environment.getEnvVar

val config by lazy {
    config()
}

fun config() = ConfigLoader.builder()
    .addEnvironmentSource()
    .addResourceSource("/application-personal.conf", optional = true)
    .addResourceSource("/kafka_common.conf")
    .addResourceSource(caListResourceForCluster())
    .addResourceSource("/application.conf")
    .withExplicitSealedTypes()
    .build()
    .loadConfigOrThrow<Config>()

private fun caListResourceForCluster() = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> "/ca_list_prod.conf"
    "dev-fss" -> "/ca_list_dev.conf"
    else -> "/ca_list_local.conf"
}
