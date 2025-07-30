package no.nav.emottak.message.util

import no.nav.emottak.utils.environment.getEnvVar
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun createUniqueMimeMessageId() = "${Uuid.random()}@$hostName"

private val hostName = when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
    "prod-fss" -> "ebms.nav.no"
    "dev-fss" -> "dev.ebms.nav.no"
    else -> "local"
}
