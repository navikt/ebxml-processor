package no.nav.emottak.cpa.configuration

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource

@OptIn(ExperimentalHoplite::class)
fun config() = ConfigLoader.builder()
    .addEnvironmentSource()
    .addResourceSource("/application-personal.conf", optional = true)
    .addResourceSource("/kafka_common.conf")
    .addResourceSource("/application.conf")
    .withExplicitSealedTypes()
    .build()
    .loadConfigOrThrow<Config>()
