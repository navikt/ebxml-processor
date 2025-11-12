package no.nav.emottak.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun Instant?.isToday() =
    if (null == this) {
        false
    } else {
        val today = LocalDateTime.ofInstant(Instant.now(), OSLO_ZONE)
        val cpaLastUsed = LocalDateTime.ofInstant(this, OSLO_ZONE)
        today.dayOfYear == cpaLastUsed.dayOfYear
    }

val OSLO_ZONE: ZoneId = ZoneId.of("Europe/Oslo")
