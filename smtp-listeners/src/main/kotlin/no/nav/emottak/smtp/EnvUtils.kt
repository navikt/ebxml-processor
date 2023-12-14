package no.nav.emottak.smtp

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: System.getProperty(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")

fun String.fromEnv(): String =
    getEnvVar(this)
