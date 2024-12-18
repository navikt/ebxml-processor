package no.nav.emottak.ebms.consumer

import io.github.nomisRev.kafka.receiver.KafkaReceiver
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import no.nav.emottak.ebms.log

class SignalReceiver(
    private val kafkaReceiver: KafkaReceiver<String, ByteArray>,
    private val kafkaSignalTopic: String
) {

    suspend fun processMessages() {
        log.debug("Receiving signal messages from $kafkaSignalTopic")
        runCatching {
            kafkaReceiver
                .receive(kafkaSignalTopic).also {
                    log.info("Received signal messages (${it.count()})")
                }
                .take(10)
                .map { it.key() to it.value() }
                .collect(this::processSignal)
        }.onFailure {
            log.error("Error receiving signal messages", it)
        }
    }

    private fun processSignal(signal: Pair<String, ByteArray>) {
        log.info("Got signal with reference <${signal.first}> and content: ${String(signal.second)}")
    }
}
