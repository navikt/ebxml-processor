package no.nav.emottak.ebms.async.util

import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

/** Returnerer Duration (gjenstående tid) til neste gang klokka er det samme som LocalTime-objektet. */
internal fun LocalTime.durationUntil(now: LocalDateTime = LocalDateTime.now()): Duration {
    val nextRun = now.toLocalDate().atTime(this).let { todayRun ->
        if (now.isBefore(todayRun)) todayRun else todayRun.plusDays(1)
    }
    return java.time.Duration.between(now, nextRun).toKotlinDuration()
}

/** Lesbar presentasjon av en Duration, slik som "1 day, 3 hours, 30 minutes". */
internal fun Duration.readableInterval(): String {
    this.toComponents { days, hours, minutes, seconds, nanoseconds ->
        var readable = ""
        if (days > 0) readable = "$days days"
        if (hours > 0) readable = if (readable != "") "$readable, $hours hours" else "$hours hours"
        if (minutes > 0) readable = if (readable != "") "$readable, $minutes minutes" else "$minutes minutes"
        if (readable == "") readable = "$seconds seconds"
        return readable
    }
}
