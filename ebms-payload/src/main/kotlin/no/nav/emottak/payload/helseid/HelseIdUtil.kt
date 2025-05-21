package no.nav.emottak.payload.helseid

import java.time.ZonedDateTime
import java.util.Date

fun toDate(zonedDateTime: ZonedDateTime?): Date =
    if (zonedDateTime == null) Date() else Date.from(zonedDateTime.toInstant())
