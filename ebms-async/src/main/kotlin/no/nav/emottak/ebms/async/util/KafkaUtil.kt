package no.nav.emottak.ebms.async.util

import no.nav.emottak.message.model.EmailAddress
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader

private const val EMAIL_ADDRESSES = "emailAddresses"

fun List<EmailAddress>.toHeaders(): List<Header> = listOf(
    RecordHeader(
        EMAIL_ADDRESSES,
        joinToString(",") { it.emailAddress }.toByteArray()
    )
)
