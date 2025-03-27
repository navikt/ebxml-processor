package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.Flow
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer

class EmottakKafkaReceiver<K, V>(private val settings: ReceiverSettings<K, V>) : KafkaReceiver<K, V> {
    override fun receive(topicNames: Collection<String>): Flow<ReceiverRecord<K, V>> {
        TODO("Not yet implemented")
    }

    override fun receiveAutoAck(topicNames: Collection<String>): Flow<Flow<ConsumerRecord<K, V>>> {
        TODO("Not yet implemented")
    }

    override suspend fun <A> withConsumer(action: suspend KafkaConsumer<K, V>.(KafkaConsumer<K, V>) -> A): A {
        TODO("Not yet implemented")
    }
}
