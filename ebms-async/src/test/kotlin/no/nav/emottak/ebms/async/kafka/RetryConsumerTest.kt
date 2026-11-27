package no.nav.emottak.ebms.async.kafka

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.RetryConsumer
import no.nav.emottak.ebms.async.kafka.consumer.startEbmsOutPayloadReceiver
import no.nav.emottak.ebms.async.kafka.consumer.startPayloadReceiver
import no.nav.emottak.ebms.async.kafka.consumer.startSignalReceiver
import no.nav.emottak.ebms.async.leadership.BackgroundJobUnavailable
import no.nav.emottak.ebms.async.processing.MessageFilterService
import no.nav.emottak.ebms.async.processing.PayloadMessageService
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import no.nav.emottak.utils.common.model.SendInResponse
import no.nav.emottak.utils.serialization.LENIENT_JSON_PARSER
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.RebalanceInProgressException
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetryConsumerTest {
    @BeforeAll
    fun start() = KafkaTestContainer.start()

    @AfterAll
    fun stop() = KafkaTestContainer.stop()

    @Test
    fun `truncated multi-partition polls do not lose excluded records`() = runBlocking {
        val topic = topic()
        produce(topic, 6)
        withOwner(topic) { owner, _ ->
            val processed = mutableListOf<String>()
            owner.consume(2) { processed.add(it.key()) }
            assertEquals(2, processed.size)
            owner.consume(20) { processed.add(it.key()) }
            assertEquals((0..5).map { "key-$it" }.toSet(), processed.toSet())
            assertEquals(6, processed.size)
        }
    }

    @Test
    fun `requeued records stay outside the frozen batch and idle consumer keeps polling`() = runBlocking {
        val topic = topic()
        produce(topic, 2)
        withOwner(topic) { owner, observer ->
            val processed = mutableListOf<String>()
            owner.consume(20) { record ->
                processed.add(record.key())
                producer().use { it.send(ProducerRecord(topic, record.partition(), record.key(), record.value())).get() }
            }
            assertEquals(2, processed.size)
            val polls = observer.polls.get()
            delay(350)
            assertTrue(observer.polls.get() > polls)
            assertEquals(2, processed.size)
            owner.consume(20) { processed.add(it.key()) }
            assertEquals(4, processed.size)
        }
    }

    @Test
    fun `slow processing does not block polling and commits only after completion`() = runBlocking {
        val topic = topic()
        produce(topic, 1)
        withOwner(topic) { owner, observer ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val batch = async {
                owner.consume(1) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            val polls = observer.polls.get()
            delay(350)
            assertTrue(observer.polls.get() > polls)
            assertEquals(0, observer.commits.get())
            release.complete(Unit)
            batch.await()
            assertEquals(1, observer.commits.get())
        }
    }

    @Test
    fun `rebalance-in-progress commit failure polls before retrying confirmed work`() = runBlocking {
        val topic = topic()
        produce(topic, 2)
        withOwner(topic, commitFailure = RebalanceInProgressException()) { owner, observer ->
            val processed = mutableListOf<String>()
            owner.consume(20) { processed.add(it.key()) }
            assertEquals(2, processed.size)
            assertEquals(2, observer.commits.get())
            assertTrue(observer.pollsAtRetry.get() > observer.pollsAtFailure.get())
        }
    }

    @Test
    fun `lost commit authority aborts batch and replays without skipping fetched records`() = runBlocking {
        val topic = topic()
        produce(topic, 4)
        withOwner(topic, commitFailure = CommitFailedException()) { owner, _ ->
            val processed = mutableListOf<String>()
            assertFailsWith<BackgroundJobUnavailable> {
                owner.consume(20) { processed.add(it.key()) }
            }
            assertEquals(1, processed.size)
            owner.consume(20) { processed.add(it.key()) }
            assertEquals((0..3).map { "key-$it" }.toSet(), processed.toSet())
            assertEquals(5, processed.size)
        }
    }

    @Test
    fun `competing consumer revokes in-flight work without committing its late completion`() = runBlocking {
        val topic = topic()
        produce(topic, 4)
        val group = UUID.randomUUID().toString()
        withOwner(topic, group) { owner, observer ->
            val entered = CompletableDeferred<Unit>()
            val first = async {
                runCatching {
                    owner.consume(20) {
                        entered.complete(Unit)
                        CompletableDeferred<Unit>().await()
                    }
                }
            }
            entered.await()
            val competing = launch(Dispatchers.IO) {
                consumer(group).use {
                    it.subscribe(listOf(topic))
                    while (kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
                        it.poll(Duration.ofMillis(100))
                    }
                }
            }
            try {
                assertTrue(first.await().exceptionOrNull() is BackgroundJobUnavailable)
                assertEquals(0, observer.commits.get())
            } finally {
                competing.cancelAndJoin()
            }
            withTimeout(30_000) {
                while (observer.assignments.get() != 2) delay(50)
            }
            val processed = ConcurrentLinkedQueue<String>()
            owner.consume(20) { processed.add(it.key()) }
            assertEquals((0..3).map { "key-$it" }.toSet(), processed.toSet())
        }
    }

    @Test
    fun `successor starts from committed offsets after owner shutdown`() = runBlocking {
        val topic = topic()
        produce(topic, 4)
        val group = UUID.randomUUID().toString()
        val processed = mutableListOf<String>()
        withOwner(topic, group) { owner, _ -> owner.consume(1) { processed.add(it.key()) } }
        withOwner(topic, group) { owner, _ -> owner.consume(20) { processed.add(it.key()) } }
        assertEquals(4, processed.size)
        assertEquals(4, processed.toSet().size)
    }

    @Test
    fun `latest starting boundary survives failover before any record is processed`() = runBlocking {
        val topic = topic()
        val group = UUID.randomUUID().toString()
        produce(topic, 2)
        withOwner(topic, group, reset = "latest") { owner, _ ->
            owner.consume(20) { error("Existing records must remain outside the latest starting boundary") }
            produce(topic, 2)
        }
        val processed = mutableListOf<String>()
        withOwner(topic, group, reset = "latest") { owner, _ ->
            owner.consume(20) { processed.add(it.key()) }
        }
        assertEquals(2, processed.size)
        assertEquals(setOf("key-0", "key-1"), processed.toSet())
    }

    @Test
    fun `all main receivers continue processing across scale up and down with pending work`() = runBlocking {
        for (kind in 0..2) {
            withTimeout(120_000) {
                val topic = "main-${UUID.randomUUID()}"
                KafkaTestContainer.createTopic(topic, partitions = 3)
                val kafka = config().kafka.copy(bootstrapServers = KafkaTestContainer.bootstrapServers, groupId = UUID.randomUUID().toString())
                val group = kafka.groupId + if (kind == 2) "-ebms-out-payload" else ""
                val seen = ConcurrentHashMap.newKeySet<String>()
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                suspend fun handle(record: ReceiverRecord<String, ByteArray>) {
                    if (record.key() == "blocked") {
                        entered.complete(Unit)
                        release.await()
                    }
                    seen.add(record.key())
                }
                fun receiver() = launch(Dispatchers.IO) {
                    if (kind == 2) {
                        val service = mockk<PayloadMessageService>()
                        coEvery { service.processOutboundResponse(any(), any()) } coAnswers { handle(firstArg()) }
                        startEbmsOutPayloadReceiver(topic, kafka, service)
                    } else {
                        val service = mockk<MessageFilterService>()
                        coEvery { service.filterMessage(any()) } coAnswers { handle(firstArg()) }
                        if (kind == 0) startPayloadReceiver(topic, kafka, service) else startSignalReceiver(topic, kafka, service)
                    }
                }
                val party = Party(listOf(PartyId("HER", "123")), "test")
                val value = LENIENT_JSON_PARSER.encodeToString(
                    SendInResponse(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "test",
                        Addressing(party, party, "service", "action"),
                        byteArrayOf(1),
                        UUID.randomUUID().toString()
                    )
                ).toByteArray()
                val jobs = mutableListOf(receiver())
                try {
                    producer().use { producer ->
                        while (!(0..2).all { "warm-$it" in seen }) {
                            repeat(3) { producer.send(ProducerRecord(topic, it, "warm-$it", value)).get() }
                            delay(200)
                        }
                        AdminClient.create(mapOf("bootstrap.servers" to KafkaTestContainer.bootstrapServers)).use { admin ->
                            while (admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS).size < 3) {
                                delay(100)
                            }
                        }
                        producer.send(ProducerRecord(topic, 0, "blocked", value)).get()
                        entered.await()
                        jobs.add(receiver())
                        delay(500)
                        release.complete(Unit)
                        repeat(12) { producer.send(ProducerRecord(topic, it % 3, "before-$it", value)).get() }
                        while (!(0..11).all { "before-$it" in seen }) delay(100)
                        jobs.removeLast().cancelAndJoin()
                        jobs.add(receiver())
                        jobs.add(receiver())
                        repeat(12) { producer.send(ProducerRecord(topic, it % 3, "after-$it", value)).get() }
                        while (!(0..11).all { "after-$it" in seen }) delay(100)
                    }
                    assertTrue("blocked" in seen)
                    assertTrue(jobs.all { it.isActive })
                } finally {
                    release.complete(Unit)
                    jobs.forEach { it.cancelAndJoin() }
                }
            }
        }
    }

    private fun topic(): String = "retry-${UUID.randomUUID()}".also { KafkaTestContainer.createTopic(it, partitions = 2) }

    private fun producer() = KafkaProducer(
        Properties().apply { setProperty("bootstrap.servers", KafkaTestContainer.bootstrapServers) },
        StringSerializer(),
        ByteArraySerializer()
    )

    private fun produce(topic: String, count: Int) = producer().use { producer ->
        repeat(count) { producer.send(ProducerRecord(topic, it % 2, "key-$it", byteArrayOf(1))).get() }
    }

    private fun consumer(group: String, reset: String = "earliest") = KafkaConsumer(
        Properties().apply {
            setProperty("bootstrap.servers", KafkaTestContainer.bootstrapServers)
            setProperty("group.id", group)
            setProperty("auto.offset.reset", reset)
            setProperty("enable.auto.commit", "false")
        },
        StringDeserializer(),
        ByteArrayDeserializer()
    )

    private suspend fun CoroutineScope.withOwner(
        topic: String,
        group: String = UUID.randomUUID().toString(),
        commitFailure: RuntimeException? = null,
        reset: String = "earliest",
        block: suspend (RetryConsumer, ObservingConsumer) -> Unit
    ) {
        val observer = ObservingConsumer(consumer(group, reset), commitFailure)
        val owner = RetryConsumer(topic, { observer }, { true })
        val job = launch(Dispatchers.IO) { owner.run() }
        try {
            withTimeout(60_000) { block(owner, observer) }
        } finally {
            job.cancelAndJoin()
        }
    }

    private class ObservingConsumer(
        private val delegate: Consumer<String, ByteArray>,
        private var failure: RuntimeException?
    ) : Consumer<String, ByteArray> by delegate {
        val polls = AtomicInteger()
        val commits = AtomicInteger()
        val assignments = AtomicInteger()
        val pollsAtFailure = AtomicInteger()
        val pollsAtRetry = AtomicInteger()

        override fun poll(timeout: Duration) = delegate.poll(timeout).also {
            polls.incrementAndGet()
            assignments.set(delegate.assignment().size)
        }

        override fun commitSync(offsets: Map<TopicPartition, OffsetAndMetadata>, timeout: Duration) {
            if (offsets.values.all { it.offset() == 0L }) {
                delegate.commitSync(offsets, timeout)
                return
            }
            failure?.let {
                failure = null
                pollsAtFailure.set(polls.get())
                throw it
            }
            if (pollsAtFailure.get() > 0) pollsAtRetry.compareAndSet(0, polls.get())
            delegate.commitSync(offsets, timeout)
            commits.incrementAndGet()
        }
    }
}
