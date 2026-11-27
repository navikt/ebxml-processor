package no.nav.emottak.ebms.async.kafka

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import no.nav.emottak.ebms.async.kafka.consumer.recoverConsumerRebalances
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.common.errors.RebalanceInProgressException
import org.apache.kafka.common.errors.TopicAuthorizationException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConsumerRebalanceRecoveryTest {
    @Test
    fun `only group ownership failures reopen the upstream receiver`() = runTest {
        var subscriptions = 0
        val received = flow {
            when (++subscriptions) {
                1 -> throw RebalanceInProgressException()
                2 -> throw CommitFailedException()
                else -> emit("recovered")
            }
        }.recoverConsumerRebalances().toList()
        assertEquals(listOf("recovered"), received)
        assertEquals(3, subscriptions)

        assertFailsWith<TopicAuthorizationException> {
            flow<Unit> { throw TopicAuthorizationException("denied") }.recoverConsumerRebalances().collect()
        }
    }

    @Test
    fun `downstream business failures do not reopen the receiver`() = runTest {
        var subscriptions = 0
        assertFailsWith<CommitFailedException> {
            flow {
                subscriptions++
                emit("record")
            }.recoverConsumerRebalances().map { throw CommitFailedException() }.collect()
        }
        assertEquals(1, subscriptions)
    }
}
