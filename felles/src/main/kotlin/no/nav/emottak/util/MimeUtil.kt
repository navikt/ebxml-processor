package no.nav.emottak.util

import java.util.UUID

fun createUniqueMimeMessageId() = "${UUID.randomUUID()}@$hostName"

private val hostName = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> "ebms.nav.no"
    "dev-fss" -> "dev.ebms.nav.no"
    else -> "local"
}