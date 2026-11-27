package no.nav.emottak.ebms.async.kafka.consumer

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import no.nav.emottak.ebms.async.log
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.common.errors.RebalanceInProgressException

// kotlin-kafka 0.4.1 terminates receive() on these commit failures.
internal fun <T> Flow<T>.recoverConsumerRebalances(): Flow<T> = retryWhen { cause, _ ->
    when (cause) {
        is RebalanceInProgressException, is CommitFailedException -> {
            log.warn("Reopening Kafka receiver after consumer-group change; uncommitted records may replay", cause)
            delay(1_000)
            true
        }
        else -> false
    }
}
