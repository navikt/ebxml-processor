package no.nav.emottak.utils

import java.io.FileInputStream

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: System.getProperty(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")

fun String.fromEnv(): String = getEnvVar(this)

fun isProdEnv(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "local") == "prod-fss"

fun getSecret(secretPath: String, localValue: String) = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "local" -> localValue
    else -> String(FileInputStream(secretPath).readAllBytes())
}
