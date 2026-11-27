package no.nav.emottak.ebms.async.kafka.consumer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import no.nav.emottak.ebms.async.leadership.BackgroundJobUnavailable
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.RebalanceInProgressException
import org.apache.kafka.common.errors.RetriableException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.time.TimeSource

class RetryConsumer(
    private val topic: String,
    private val consumerFactory: () -> Consumer<String, ByteArray>,
    private val isLeader: () -> Boolean,
    private val progress: () -> Unit = {}
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val requests = Channel<Batch>(Channel.RENDEZVOUS)
    private var epoch = 0L
    private val assignmentStarts = mutableMapOf<TopicPartition, Long>()
    private var rejoining = false
    private var initializing = true

    suspend fun consume(limit: Int, processor: suspend (ConsumerRecord<String, ByteArray>) -> Unit) {
        require(limit > 0) { "Retry limit must be positive" }
        try {
            withTimeout(10 * 60_000L) {
                val result = CompletableDeferred<Unit>(currentCoroutineContext()[kotlinx.coroutines.Job])
                requests.send(Batch(limit, processor, result))
                result.await()
            }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw BackgroundJobUnavailable("Retry batch timed out; inspect progress before repeating")
        }
    }

    suspend fun run() {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "retry-consumer-$topic").apply { isDaemon = true }
        }.asCoroutineDispatcher().use { dispatcher ->
            withContext(dispatcher) {
                val consumer = try {
                    consumerFactory()
                } catch (e: Exception) {
                    requests.close(e)
                    throw e
                }
                try {
                    consumer.subscribe(
                        listOf(topic),
                        object : ConsumerRebalanceListener {
                            override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
                                epoch++
                                partitions.forEach(assignmentStarts::remove)
                                log.info("Retry partitions revoked: {}", partitions)
                            }

                            override fun onPartitionsLost(partitions: Collection<TopicPartition>) {
                                onPartitionsRevoked(partitions)
                            }

                            override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
                                epoch++
                                rejoining = false
                                initializing = true
                                consumer.pause(partitions)
                                partitions.forEach {
                                    assignmentStarts.putIfAbsent(it, consumer.position(it, API_TIMEOUT))
                                }
                                log.info("Retry partitions assigned and paused: {}", partitions)
                            }
                        }
                    )
                    while (currentCoroutineContext().isActive && isLeader()) {
                        val request = requests.tryReceive().getOrNull()
                        if (request != null) {
                            execute(consumer, request)
                        } else {
                            consumer.pause(consumer.assignment())
                            consumer.poll(POLL_TIMEOUT)
                            initializePositions(consumer)
                        }
                    }
                } finally {
                    requests.close(BackgroundJobUnavailable("Retry consumer stopped for $topic"))
                    consumer.close(API_TIMEOUT)
                }
            }
        }
    }

    private suspend fun execute(consumer: Consumer<String, ByteArray>, batch: Batch) {
        var batchEpoch = epoch
        val resumePositions = mutableMapOf<TopicPartition, Long>()
        try {
            if (!batch.result.isActive) return
            requireLeadership()
            val assignmentDeadline = TimeSource.Monotonic.markNow()
            while (consumer.assignment().isEmpty() || rejoining || initializing) {
                checkBatch(batch)
                if (assignmentDeadline.elapsedNow().inWholeSeconds >= 30) {
                    throw BackgroundJobUnavailable("Timed out assigning retry partitions for $topic")
                }
                consumer.poll(POLL_TIMEOUT)
                initializePositions(consumer)
            }
            batchEpoch = epoch
            val partitions = consumer.assignment()
            partitions.forEach { resumePositions[it] = consumer.position(it, API_TIMEOUT) }
            val endOffsets = consumer.endOffsets(partitions, API_TIMEOUT)
            val records = mutableListOf<ConsumerRecord<String, ByteArray>>()
            val collectionDeadline = TimeSource.Monotonic.markNow()
            consumer.resume(partitions)
            while (records.size < batch.limit && partitions.any { consumer.position(it, API_TIMEOUT) < endOffsets.getValue(it) }) {
                checkBatch(batch, batchEpoch)
                if (collectionDeadline.elapsedNow().inWholeSeconds >= 5) break
                val polled = consumer.poll(POLL_TIMEOUT)
                checkBatch(batch, batchEpoch)
                val excluded = mutableMapOf<TopicPartition, Long>()
                polled.forEach { record ->
                    val tp = TopicPartition(record.topic(), record.partition())
                    if (records.size < batch.limit && record.offset() < endOffsets.getValue(tp)) {
                        records.add(record)
                    } else {
                        excluded.putIfAbsent(tp, record.offset())
                    }
                }
                excluded.forEach { (tp, offset) -> consumer.seek(tp, offset) }
                if (excluded.isNotEmpty()) break
            }
            consumer.pause(consumer.assignment())
            val fetchedPositions = partitions.associateWith { consumer.position(it, API_TIMEOUT) }
            for (record in records) {
                checkBatch(batch, batchEpoch)
                processWhilePolling(consumer, batch, batchEpoch, record)
                checkBatch(batch, batchEpoch)
                val tp = TopicPartition(record.topic(), record.partition())
                commit(consumer, batch, batchEpoch, tp, record.offset() + 1)
                resumePositions[tp] = record.offset() + 1
                progress()
            }
            resumePositions.putAll(fetchedPositions)
        } catch (e: CancellationException) {
            batch.result.cancel(e)
            throw e
        } catch (e: KafkaException) {
            batch.result.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            log.error("Retry batch interrupted for {}; uncommitted records remain eligible", topic, e)
            batch.result.completeExceptionally(e)
        } finally {
            try {
                consumer.pause(consumer.assignment())
                if (batchEpoch == epoch && !rejoining) {
                    resumePositions.filterKeys { it in consumer.assignment() }.forEach { (tp, offset) ->
                        consumer.seek(tp, offset)
                    }
                } else {
                    val assigned = consumer.assignment()
                    val committed = consumer.committed(assigned, API_TIMEOUT)
                    assigned.forEach { tp ->
                        val offset = committed[tp]?.offset() ?: assignmentStarts[tp]
                        if (offset != null) consumer.seek(tp, offset)
                    }
                }
            } catch (e: Exception) {
                batch.result.completeExceptionally(e)
                throw e
            }
        }
        batch.result.complete(Unit)
    }

    private suspend fun processWhilePolling(
        consumer: Consumer<String, ByteArray>,
        batch: Batch,
        batchEpoch: Long,
        record: ConsumerRecord<String, ByteArray>
    ) = supervisorScope {
        val work = async(Dispatchers.IO) { batch.processor(record) }
        try {
            while (!work.isCompleted) {
                checkBatch(batch, batchEpoch)
                consumer.poll(POLL_TIMEOUT)
            }
            work.await()
        } finally {
            work.cancel()
        }
    }

    private fun initializePositions(consumer: Consumer<String, ByteArray>) {
        if (!initializing || rejoining || consumer.assignment().isEmpty()) return
        val assigned = consumer.assignment()
        try {
            val committed = consumer.committed(assigned, API_TIMEOUT)
            val missing = assigned.filter { committed[it] == null }.associateWith {
                OffsetAndMetadata(assignmentStarts.getValue(it))
            }
            // Persist the configured reset boundary before fetching, also for a new group using latest.
            if (missing.isNotEmpty()) consumer.commitSync(missing, API_TIMEOUT)
            initializing = false
        } catch (e: CommitFailedException) {
            rejoining = true
            consumer.enforceRebalance()
            log.info("Rejoining retry group before establishing starting offsets for {}", topic)
        } catch (e: RebalanceInProgressException) {
            log.info("Waiting for retry rebalance before establishing starting offsets for {}", topic)
        } catch (e: RetriableException) {
            log.warn("Retry starting offsets temporarily unavailable for {}", topic, e)
        }
    }

    private suspend fun commit(
        consumer: Consumer<String, ByteArray>,
        batch: Batch,
        batchEpoch: Long,
        partition: TopicPartition,
        offset: Long
    ) {
        val deadline = TimeSource.Monotonic.markNow()
        while (true) {
            checkBatch(batch, batchEpoch)
            try {
                consumer.commitSync(mapOf(partition to OffsetAndMetadata(offset)), API_TIMEOUT)
                return
            } catch (e: CommitFailedException) {
                rejoining = true
                consumer.enforceRebalance()
                throw BackgroundJobUnavailable("Retry commit authority lost for $partition; batch will replay")
            } catch (e: RebalanceInProgressException) {
                log.info("Completing retry consumer rebalance before reconsidering commit for {}", partition)
            } catch (e: RetriableException) {
                log.warn("Retry offset commit temporarily unavailable for {}", partition, e)
            }
            if (deadline.elapsedNow().inWholeSeconds >= 5) {
                throw BackgroundJobUnavailable("Retry offset commit timed out for $partition; batch will replay")
            }
            consumer.poll(POLL_TIMEOUT)
        }
    }

    private suspend fun checkBatch(batch: Batch, batchEpoch: Long = epoch) {
        currentCoroutineContext().ensureActive()
        requireLeadership()
        if (!batch.result.isActive) throw BackgroundJobUnavailable("Retry batch request was cancelled")
        if (epoch != batchEpoch) throw BackgroundJobUnavailable("Retry assignment changed; batch will replay")
    }

    private fun requireLeadership() {
        if (!isLeader()) throw BackgroundJobUnavailable("This pod no longer holds background-job leadership")
    }

    private data class Batch(
        val limit: Int,
        val processor: suspend (ConsumerRecord<String, ByteArray>) -> Unit,
        val result: CompletableDeferred<Unit>
    )

    private companion object {
        val POLL_TIMEOUT: Duration = Duration.ofMillis(100)
        val API_TIMEOUT: Duration = Duration.ofSeconds(2)
    }
}
