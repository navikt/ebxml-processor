package no.nav.emottak.util

import no.nav.emottak.utils.common.zoneOslo
import java.time.Instant
import java.time.LocalDateTime

fun Instant?.isMoreThanXMinutesAgo(x: Long) =
    if (null == this) {
        true
    } else {
        val today = LocalDateTime.ofInstant(Instant.now(), zoneOslo())
        val earlierToday = today.minusMinutes(x)
        val cpaLastUsed = LocalDateTime.ofInstant(this, zoneOslo())
        cpaLastUsed.isBefore(earlierToday)
    }
