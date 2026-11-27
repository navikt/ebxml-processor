package no.nav.emottak.ebms.async.processing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.emottak.ebms.async.leadership.Leadership
import no.nav.emottak.ebms.async.log
import kotlin.time.Duration
import kotlin.time.TimeSource

fun CoroutineScope.launchLeaderSchedule(
    name: String,
    startupDelay: Duration,
    interval: Duration,
    leadership: Leadership,
    task: suspend () -> Unit
) = launch(Dispatchers.IO) {
    require(interval.isPositive()) { "Background-job interval must be positive" }
    delay(startupDelay)
    while (currentCoroutineContext().isActive) {
        val tick = TimeSource.Monotonic.markNow()
        if (leadership.isLeader) {
            try {
                task()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("{} failed", name, e)
            }
        }
        val elapsed = tick.elapsedNow()
        val skipped = elapsed / interval
        delay(interval * (skipped.toInt() + 1) - elapsed)
    }
}
