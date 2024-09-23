package no.nav.emottak.util

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: System.getProperty(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")

fun String.fromEnv(): String = getEnvVar(this)

fun isProdEnv(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "local") == "prod-fss"
