package no.nav.emottak.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

inline fun <T> measureTime(block: () -> T): Pair<T, Duration> {
    val startTime = Instant.now()
    val result = block()
    val duration = Duration.between(startTime, Instant.now())
    return result to duration
}

suspend inline fun <T> measureTimeSuspended(crossinline block: suspend () -> T): Pair<T, Duration> {
    val startTime = Instant.now()
    val result = withContext(Dispatchers.Default) { block() }
    val duration = Duration.between(startTime, Instant.now())
    return result to duration
}
