package no.nav.emottak.util


internal fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?:
    System.getProperty(varName) ?:
    defaultValue ?: throw RuntimeException("Missing required variable $varName")






