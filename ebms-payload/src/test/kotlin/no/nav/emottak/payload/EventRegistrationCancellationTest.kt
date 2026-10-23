package no.nav.emottak.payload

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.payload.util.EventRegistrationServiceImpl
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EventRegistrationCancellationTest {
    private val eventLoggingService = mockk<EventLoggingService>()
    private val service = EventRegistrationServiceImpl(eventLoggingService)
    private val request = mockk<PayloadRequest>(relaxed = true) {
        every { requestId } returns "00000000-0000-0000-0000-000000000001"
    }

    @Test
    fun `registerEvent propagates cancellation`() {
        val cancellation = CancellationException("SuspendApp shutting down")
        coEvery { eventLoggingService.logEvent(any()) } throws cancellation

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                service.registerEvent(EventType.UNKNOWN_ERROR_OCCURRED, request)
            }
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun `registerEvent contains ordinary publishing failures`() = runBlocking {
        coEvery { eventLoggingService.logEvent(any()) } throws IllegalStateException("Publishing failed")

        service.registerEvent(EventType.UNKNOWN_ERROR_OCCURRED, request)

        coVerify(exactly = 1) { eventLoggingService.logEvent(any()) }
    }
}
