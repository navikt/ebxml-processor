package no.nav.emottak.ebms.async

import io.micrometer.core.instrument.MeterRegistry

const val MESSAGES_QUEUED_FOR_RETRY_COUNTER = "messages_queued_for_retry_total"
const val TAG_RETRY_TOPIC = "retry_topic"

const val MESSAGES_FIRST_FAILURE_COUNTER = "messages_first_failure_total"
const val TAG_DIRECTION = "direction"
const val TAG_SERVICE = "service"
const val TAG_ACTION = "action"

const val MESSAGES_RESENT_COUNTER = "messages_resent_total"
const val TAG_RESENT_COUNT = "resent_count"

fun MeterRegistry.incrementMessagesQueuedForRetry(topic: String) =
    counter(
        MESSAGES_QUEUED_FOR_RETRY_COUNTER,
        TAG_RETRY_TOPIC,
        topic
    ).increment()

fun MeterRegistry.incrementFirstFailure(direction: String, service: String, action: String) =
    counter(
        MESSAGES_FIRST_FAILURE_COUNTER,
        TAG_DIRECTION,
        direction,
        TAG_SERVICE,
        service,
        TAG_ACTION,
        action
    ).increment()

fun MeterRegistry.incrementMessagesResent(resentCount: Int, service: String, action: String) =
    counter(
        MESSAGES_RESENT_COUNTER,
        TAG_RESENT_COUNT,
        resentCount.toString(),
        TAG_SERVICE,
        service,
        TAG_ACTION,
        action
    ).increment()
