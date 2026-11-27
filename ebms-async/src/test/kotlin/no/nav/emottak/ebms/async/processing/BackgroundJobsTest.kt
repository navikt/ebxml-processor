package no.nav.emottak.ebms.async.processing

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import no.nav.emottak.ebms.async.configuration.Config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.launchRetryConsumers
import no.nav.emottak.ebms.async.leadership.TestLeadership
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BackgroundJobsTest {
    @Test
    fun `only elected pod runs retry owners and authority loss cancels them`() = runTest {
        val config = mockk<Config>()
        every { config.kafkaErrorQueue.active } returns true
        every { config.kafkaErrorQueueOut.active } returns true
        val first = TestLeadership("pod-1", "pod-1")
        val second = TestLeadership("pod-2", "pod-1")
        val active = AtomicInteger()
        fun handler() = mockk<FailedMessageKafkaHandler>().also { handler ->
            coEvery { handler.runRetryConsumer(any(), any()) } coAnswers {
                active.incrementAndGet()
                try {
                    awaitCancellation()
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        val firstJob = launchRetryConsumers(config, handler(), first)
        val secondJob = launchRetryConsumers(config, handler(), second)
        try {
            runCurrent()
            assertEquals(2, active.get())
            first.leader.value = null
            runCurrent()
            assertEquals(0, active.get())
            second.leader.value = "pod-2"
            runCurrent()
            assertEquals(2, active.get())
        } finally {
            firstJob.cancelAndJoin()
            secondJob.cancelAndJoin()
        }
        assertEquals(0, active.get())
    }

    @Test
    fun `follower ticks are skipped and slow scheduled work cannot overlap`() = runBlocking {
        val leadership = TestLeadership("pod-1", "pod-2")
        val calls = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launchLeaderSchedule("test", 0.milliseconds, 100.milliseconds, leadership) {
            calls.incrementAndGet()
            entered.complete(Unit)
            release.await()
        }
        try {
            delay(250)
            assertEquals(0, calls.get())
            leadership.leader.value = "pod-1"
            withTimeout(5_000) { entered.await() }
            delay(250)
            assertEquals(1, calls.get())
            leadership.leader.value = "pod-2"
            release.complete(Unit)
            delay(250)
            assertEquals(1, calls.get())
        } finally {
            job.cancelAndJoin()
        }
    }
}
