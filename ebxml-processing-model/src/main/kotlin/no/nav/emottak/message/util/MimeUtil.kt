package no.nav.emottak.message.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun createUniqueMimeMessageId() = "${Uuid.random()}@$hostName"

private val hostName = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> "ebms.nav.no"
    "dev-fss" -> "dev.ebms.nav.no"
    else -> "local"
}

// For å slippe avhengighet til emottak-utils (kopiert fra EnvUtils.kt):
private fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getProperty(varName) ?: System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
